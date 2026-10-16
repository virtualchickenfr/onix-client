package gg.carbon.client.util;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.HudModule;
import gg.carbon.client.module.impl.hud.Keystrokes;
import gg.carbon.client.screen.CarbonMainMenu;
import gg.carbon.client.screen.ClickGui;
import gg.carbon.client.screen.HudEditor;
import gg.carbon.client.screen.ThemePicker;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;

/**
 * Automated visual smoke test, only active with -Dcarbon.devtest=true (gradle runDevtest).
 * Walks through Carbon's screens and saves screenshots to run/screenshots.
 */
public final class DevTest {
	private DevTest() {}

	public static final boolean ENABLED = Boolean.getBoolean("carbon.devtest");

	private record Step(int delay, BooleanSupplier action) {}

	private static final List<Step> STEPS = new ArrayList<>();
	private static int index;
	private static int wait;
	private static int waited;
	private static ClickGui gui;
	private static ThemePicker picker;
	private static CarbonMainMenu menu;
	private static Object lastAccepted;

	/** Adds a step that runs once after {@code delay} ticks. */
	private static void then(int delay, Runnable r) {
		STEPS.add(new Step(delay, () -> {
			r.run();
			return true;
		}));
	}

	/** Adds a step that is retried every tick until it returns true. */
	private static void until(BooleanSupplier condition) {
		STEPS.add(new Step(0, condition));
	}

	private static void build(Minecraft mc) {
		until(() -> mc.screen instanceof CarbonMainMenu);
		then(10, () -> {
			mc.options.pauseOnLostFocus = false;
			menu = (CarbonMainMenu) mc.screen;
		});
		var client = CarbonClient.modules().client;
		int right = com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT;
		for (String id : List.of("Carbon", "Voxel", "Portal", "Classic")) {
			then(5, () -> client.menuDesign.set(id));
			then(80, () -> snap(mc, "01_menu_" + id.toLowerCase()));
		}
		then(5, () -> client.menuDesign.set("Portal"));
		then(10, () -> menu.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_4, 0, 0)));
		then(25, () -> snap(mc, "01_menu_portal_slot4"));
		then(5, () -> client.menuLight.set(true));
		for (String id : List.of("Carbon", "Voxel", "Portal")) {
			then(5, () -> client.menuDesign.set(id));
			then(60, () -> snap(mc, "01_menu_" + id.toLowerCase() + "_light"));
		}
		then(5, () -> client.menuDesign.set("Carbon"));
		then(10, () -> {
			picker = new ThemePicker(menu);
			mc.setScreen(picker);
		});
		then(40, () -> snap(mc, "02_picker_light"));
		then(2, () -> picker.keyPressed(new KeyEvent(right, 0, 0)));
		then(8, () -> snap(mc, "02_picker_swiping"));
		then(30, () -> snap(mc, "02_picker_carbon_client_light"));
		then(2, () -> picker.keyPressed(new KeyEvent(right, 0, 0)));
		then(30, () -> snap(mc, "02_picker_voxel_light"));
		then(2, () -> picker.keyPressed(new KeyEvent(right, 0, 0)));
		then(30, () -> snap(mc, "02_picker_portal_light"));
		then(2, () -> client.menuLight.set(false));
		then(30, () -> snap(mc, "02_picker_portal"));
		then(2, () -> picker.keyPressed(new KeyEvent(right, 0, 0)));
		then(2, () -> picker.keyPressed(new KeyEvent(right, 0, 0)));
		then(40, () -> snap(mc, "02_picker_vanilla"));
		then(2, () -> mc.setScreen(menu));
		then(20, () -> {
			gui = new ClickGui(mc.screen);
			mc.setScreen(gui);
		});
		then(40, () -> snap(mc, "03_mod_menu"));
		then(2, () -> gui.showModule(CarbonClient.modules().get(Keystrokes.class)));
		then(30, () -> snap(mc, "04_module_settings"));
		then(2, () -> {
			gui = new ClickGui(menu).design();
			mc.setScreen(gui);
		});
		then(40, () -> snap(mc, "03_mod_menu_design"));
		then(2, () -> client.menuLight.set(true));
		then(20, () -> snap(mc, "03_mod_menu_design_light"));
		then(2, () -> {
			gui = new ClickGui(menu);
			mc.setScreen(gui);
		});
		then(40, () -> snap(mc, "03_mod_menu_light"));
		then(2, () -> gui.showModule(CarbonClient.modules().get(Keystrokes.class)));
		then(30, () -> snap(mc, "04_module_settings_light"));
		// vanilla menu screens with Carbon's backdrop, light then dark
		then(2, () -> mc.options.skipMultiplayerWarning = true);
		for (boolean light : new boolean[] {true, false}) {
			String suffix = light ? "_light" : "";
			then(2, () -> client.menuLight.set(light));
			then(2, () -> mc.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(menu)));
			then(40, () -> snap(mc, "10_worlds" + suffix));
			then(2, () -> mc.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(menu)));
			then(40, () -> snap(mc, "11_servers" + suffix));
			then(2, () -> mc.setScreen(new OptionsScreen(menu, mc.options)));
			then(30, () -> snap(mc, "12_options" + suffix));
			then(2, () -> mc.setScreen(new net.minecraft.client.gui.screens.options.LanguageSelectScreen(menu, mc.options, mc.getLanguageManager())));
			then(30, () -> snap(mc, "13_language" + suffix));
		}
		then(2, () -> {
			gui = new ClickGui(menu);
			mc.setScreen(gui);
			gui.showModule(client);
		});
		then(30, () -> snap(mc, "14_settings"));
		then(2, () -> client.menuLight.set(false));
		then(2, () -> mc.setScreen(menu));
		then(20, () -> {});		then(2, () -> {
			if (java.nio.file.Files.isDirectory(mc.gameDirectory.toPath().resolve("saves").resolve("New World"))) {
				mc.createWorldOpenFlows().openWorld("New World", () -> {});
			} else {
				CreateWorldScreen.testWorld(mc, () -> {});
			}
		});
		until(() -> {
			if (mc.player != null && mc.level != null) return true;
			acceptDialogs(mc);
			if (++waited % 100 == 0) {
				CarbonClient.LOG.info("[devtest] waiting for world, screen = {}",
					mc.screen == null ? "none" : mc.screen.getClass().getName());
			}
			return false;
		});
		then(0, () -> {
			for (HudModule m : CarbonClient.modules().hud()) {
				if (m.id().equals("speedMeter") || m.id().equals("direction") || m.id().equals("coordinates")
					|| m.id().equals("time") || m.id().equals("ping") || m.id().equals("mousestrokes")) m.setEnabled(true);
			}
			gg.carbon.client.ui.HudLayout.pending = true;
		});
		then(160, () -> snap(mc, "05_hud"));
		then(2, () -> mc.setScreen(new HudEditor(null)));
		then(40, () -> snap(mc, "06_hud_editor"));
		then(2, () -> {
			gui = new ClickGui(null);
			mc.setScreen(gui);
		});
		then(40, () -> snap(mc, "07_mod_menu_ingame"));
		then(2, () -> mc.setScreen(new OptionsScreen(null, mc.options)));
		then(30, () -> snap(mc, "08_vanilla_restyled"));
		then(2, () -> {
			CarbonClient.modules().get(gg.carbon.client.module.impl.visual.DarkMode.class).setEnabled(true);
			CarbonClient.modules().get(gg.carbon.client.module.impl.utility.ShulkerPreview.class).setEnabled(true);
			var server = mc.getSingleplayerServer();
			if (server != null) server.execute(() -> {
				var sp = server.getPlayerList().getPlayers().get(0);
				sp.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				java.util.List<net.minecraft.world.item.ItemStack> stuff = new java.util.ArrayList<>();
				stuff.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 64));
				stuff.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 12));
				stuff.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 16));
				stuff.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 64));
				stuff.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING, 1));
				stuff.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.NETHERITE_SWORD, 1));
				stuff.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OBSIDIAN, 48));
				net.minecraft.world.item.ItemStack box = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHULKER_BOX);
				box.set(net.minecraft.core.component.DataComponents.CONTAINER, net.minecraft.world.item.component.ItemContainerContents.fromItems(stuff));
				sp.getInventory().setItem(9, box);
				sp.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
			});
		});
		then(20, () -> {
			var screen = new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player);
			mc.setScreen(screen);
			net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s, g, mx, my, d) -> {
				var box = mc.player.getInventory().getItem(9);
				if (!box.isEmpty()) CarbonClient.modules().get(gg.carbon.client.module.impl.utility.ShulkerPreview.class)
					.render(g, box, s.width / 2 + 60, s.height / 2 - 70, s.width, s.height);
			});
		});
		then(30, () -> snap(mc, "09_inventory_dark_shulker"));
		then(20, () -> {			CarbonClient.LOG.info("[devtest] finished");
			mc.stop();
		});
	}

	public static void tick(Minecraft mc) {
		if (!ENABLED) return;
		if (STEPS.isEmpty()) build(mc);
		if (index >= STEPS.size()) return;
		if (wait > 0) {
			wait--;
			return;
		}
		Step step = STEPS.get(index);
		if (step.action().getAsBoolean()) {
			index++;
			if (index < STEPS.size()) wait = STEPS.get(index).delay();
		}
	}

	/** Clicks through "experimental settings" / backup confirmations for the throwaway test world. */
	private static void acceptDialogs(Minecraft mc) {
		var screen = mc.screen;
		if (screen == lastAccepted) return;
		if (!(screen instanceof net.minecraft.client.gui.screens.ConfirmScreen)
			&& !(screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen)
			&& !(screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen)) return;
		for (var w : net.fabricmc.fabric.api.client.screen.v1.Screens.getButtons(screen)) {
			if (!(w instanceof net.minecraft.client.gui.components.AbstractButton b)) continue;
			String label = b.getMessage().getString().toLowerCase(java.util.Locale.ROOT);
			boolean accept = label.equals("create new world") || label.equals("load") || label.contains("proceed") || label.equals("yes") || label.contains("know what")
				|| (label.contains("load") && !label.contains("back"));
			if (accept) {
				CarbonClient.LOG.info("[devtest] accepting dialog button '{}'", label);
				lastAccepted = screen;
				b.onPress(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 28, 0));
				return;
			}
		}
	}

	private static void snap(Minecraft mc, String name) {
		Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1,
			msg -> CarbonClient.LOG.info("[devtest] {}", msg.getString()));
	}
}
