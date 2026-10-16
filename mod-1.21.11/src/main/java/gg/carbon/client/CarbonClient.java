package gg.carbon.client;

import gg.carbon.client.config.ConfigManager;
import gg.carbon.client.module.ModuleManager;
import gg.carbon.client.module.impl.hud.PotionEffects;
import gg.carbon.client.mixin.ContainerScreenInvoker;
import gg.carbon.client.module.impl.pvp.ToggleSprint;
import gg.carbon.client.module.impl.utility.UtilityMods;
import gg.carbon.client.module.impl.visual.Crosshair;
import gg.carbon.client.module.impl.visual.VisualMods;
import gg.carbon.client.screen.ClickGui;
import gg.carbon.client.screen.HudEditor;
import gg.carbon.client.ui.HudRenderer;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.util.Stats;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CarbonClient implements ClientModInitializer {
	public static final String NAME = "Carbon";
	public static final String VERSION = "1.0.0";
	public static final Logger LOG = LoggerFactory.getLogger(NAME);

	private static ModuleManager modules;
	private static ConfigManager config;
	private static ServerData lastServer;
	private static long disconnectedAt;

	public static ModuleManager modules() {
		return modules;
	}

	public static ConfigManager config() {
		return config;
	}

	@Override
	public void onInitializeClient() {
		modules = new ModuleManager();
		config = new ConfigManager(modules);
		config.load();

		registerHud();

		ClientTickEvents.START_CLIENT_TICK.register(client -> {
			ToggleSprint ts = modules.get(ToggleSprint.class);
			if (ts.enabled()) ts.applyKeys();
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Stats.tick();
			modules.tick();
			config.tick();
			if (client.getCurrentServer() != null) lastServer = client.getCurrentServer();
			gg.carbon.client.util.DevTest.tick(client);
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> Stats.resetWorld());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> config.save());
		ScreenEvents.AFTER_INIT.register(CarbonClient::afterScreenInit);

		LOG.info("Carbon Client {} initialised with {} modules", VERSION, modules.all().size());
	}

	private static void registerHud() {
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("carbon", "hud"), (g, delta) -> HudRenderer.render(g));

		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (g, delta) -> {
			Crosshair c = modules.get(Crosshair.class);
			if (c.enabled()) c.render(g);
			else vanilla.render(g, delta);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.STATUS_EFFECTS, vanilla -> (g, delta) -> {
			PotionEffects p = modules.get(PotionEffects.class);
			if (!(p.enabled() && p.hideVanilla.on())) vanilla.render(g, delta);
		});
		HudElementRegistry.replaceElement(VanillaHudElements.BOSS_BAR, vanilla -> (g, delta) -> {
			VisualMods.BossBar b = modules.get(VisualMods.BossBar.class);
			if (!b.enabled()) vanilla.render(g, delta);
			else if (!b.hide.on()) scaled(g, vanilla, delta, g.guiWidth() / 2f, 0, b.scale.f());
		});
		HudElementRegistry.replaceElement(VanillaHudElements.SCOREBOARD, vanilla -> (g, delta) -> {
			VisualMods.Scoreboard s = modules.get(VisualMods.Scoreboard.class);
			if (!s.enabled()) vanilla.render(g, delta);
			else if (!s.hide.on()) scaled(g, vanilla, delta, g.guiWidth(), g.guiHeight() / 2f, s.scale.f());
		});
		HudElementRegistry.replaceElement(VanillaHudElements.TITLE_AND_SUBTITLE, vanilla -> (g, delta) -> {
			VisualMods.TitleTweaker t = modules.get(VisualMods.TitleTweaker.class);
			if (!t.enabled()) vanilla.render(g, delta);
			else if (!t.hide.on()) scaled(g, vanilla, delta, g.guiWidth() / 2f, g.guiHeight() / 2f, t.scale.f());
		});
	}

	private static void scaled(GuiGraphics g, HudElement element, net.minecraft.client.DeltaTracker delta, float px, float py, float scale) {
		R2D.push(g);
		R2D.scaleAt(g, px, py, scale);
		element.render(g, delta);
		R2D.pop(g);
	}

	/** Vanilla buttons get Carbon's look, except on the untouched vanilla title screen. */
	public static boolean restyleButtons() {
		if (modules == null || !modules.client.buttons.on()) return false;
		return !(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.TitleScreen);
	}

	private static void afterScreenInit(Minecraft mc, Screen screen, int width, int height) {
		if (screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
			net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
				if (event.key() == modules.client.menuKey.key()) mc.setScreen(new ClickGui(s));
			});
		}
		if (screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container) {
			// Module toggle keys also work while an inventory is open.
			net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> modules.toggleByKey(event.key()));
		}
		if (screen instanceof DisconnectedScreen) {
			UtilityMods.Reconnect reconnect = modules.get(UtilityMods.Reconnect.class);
			if (!reconnect.enabled() || lastServer == null) return;
			ServerData target = lastServer;
			disconnectedAt = System.currentTimeMillis();
			Button button = Button.builder(Component.literal("Reconnect"), b -> connect(mc, screen, target))
				.bounds(width / 2 - 100, height - 32, 200, 20)
				.build();
			Screens.getButtons(screen).add(button);
			if (reconnect.auto.on()) {
				ScreenEvents.afterTick(screen).register(s -> {
					long left = reconnect.delay.i() * 1000L - (System.currentTimeMillis() - disconnectedAt);
					if (left <= 0) connect(mc, screen, target);
					else button.setMessage(Component.literal("Reconnect (" + (left / 1000 + 1) + ")"));
				});
			}
		}
	}

	private static void connect(Minecraft mc, Screen parent, ServerData data) {
		if (mc.screen instanceof ConnectScreen) return;
		ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(data.ip), data, false, null);
	}

	public static void openMenu() {
		Minecraft.getInstance().setScreen(new ClickGui(null));
	}

	public static void openHudEditor() {
		if (Minecraft.getInstance().level == null) return;
		Minecraft.getInstance().setScreen(new HudEditor(null));
	}
}
