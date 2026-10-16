package gg.carbon.client.module;

import gg.carbon.client.module.impl.hud.ArmorStatus;
import gg.carbon.client.module.impl.hud.Compass;
import gg.carbon.client.module.impl.hud.Coordinates;
import gg.carbon.client.module.impl.hud.Keystrokes;
import gg.carbon.client.module.impl.hud.Mousestrokes;
import gg.carbon.client.module.impl.hud.PotionEffects;
import gg.carbon.client.module.impl.hud.SimpleHuds;
import gg.carbon.client.module.impl.performance.PerformanceMods;
import gg.carbon.client.module.impl.pvp.AimAssist;
import gg.carbon.client.module.impl.pvp.ItemCounter;
import gg.carbon.client.module.impl.pvp.PvpMods;
import gg.carbon.client.module.impl.pvp.ToggleSprint;
import gg.carbon.client.module.impl.utility.UtilityMods;
import gg.carbon.client.module.impl.utility.Waypoints;
import gg.carbon.client.module.impl.visual.Crosshair;
import gg.carbon.client.module.impl.visual.Freelook;
import gg.carbon.client.module.impl.visual.VisualMods;
import gg.carbon.client.module.impl.visual.Zoom;
import gg.carbon.client.CarbonClient;
import gg.carbon.client.setting.KeySetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class ModuleManager {
	private final List<Module> modules = new ArrayList<>();
	private final List<HudModule> hud = new ArrayList<>();
	private final Map<Class<?>, Module> byClass = new HashMap<>();
	public final ClientSettings client = new ClientSettings();

	public ModuleManager() {
		// HUD
		register(new SimpleHuds.Fps());
		register(new SimpleHuds.Cps());
		register(new SimpleHuds.Ping());
		register(new Coordinates());
		register(new Compass());
		register(new ArmorStatus());
		register(new PotionEffects());
		register(new SimpleHuds.Clock());
		register(new SimpleHuds.Speed());
		register(new SimpleHuds.Playtime());
		register(new SimpleHuds.ServerAddress());
		register(new SimpleHuds.Memory());
		register(new SimpleHuds.Tps());
		register(new SimpleHuds.Saturation());
		register(new SimpleHuds.PackDisplay());
		// PvP
		register(new Keystrokes());
		register(new Mousestrokes());
		register(new ToggleSprint());
		register(new SimpleHuds.Combo());
		register(new SimpleHuds.Reach());
		register(new SimpleHuds.JumpReset());
		register(new ItemCounter());
		register(new PvpMods.Hitbox());
		register(new PvpMods.DropPrevention());
		register(new AimAssist());
		// Visual
		register(new Zoom());
		register(new Freelook());
		register(new VisualMods.SnapLook());
		register(new VisualMods.AutoPerspective());
		register(new Crosshair());
		register(new VisualMods.Fullbright());
		register(new VisualMods.FovChanger());
		register(new VisualMods.BlockOverlay());
		register(new VisualMods.CameraTweaks());
		register(new VisualMods.OwnNametag());
		register(new VisualMods.WeatherChanger());
		register(new VisualMods.BossBar());
		register(new VisualMods.Scoreboard());
		register(new VisualMods.TitleTweaker());
		register(new gg.carbon.client.module.impl.visual.DarkMode());
		// Utility
		register(new Waypoints());
		register(new gg.carbon.client.module.impl.utility.ShulkerPreview());
		register(new UtilityMods.AutoText());
		register(new UtilityMods.Reconnect());
		register(new UtilityMods.ToastControl());
		register(new UtilityMods.Stopwatch());
		register(new SimpleHuds.DeathInfo());
		// Performance
		register(new PerformanceMods.UnfocusedFps());
		register(new PerformanceMods.ParticleLimiter());
		register(new PerformanceMods.EntityCulling());

		byClass.put(ClientSettings.class, client);
		defaults();
	}

	/** Modules that are on for a fresh install. */
	private void defaults() {
		for (Class<?> c : List.of(SimpleHuds.Fps.class, SimpleHuds.Cps.class, Keystrokes.class, ArmorStatus.class, PotionEffects.class,
			ToggleSprint.class, Zoom.class, Freelook.class, Waypoints.class, PerformanceMods.UnfocusedFps.class, UtilityMods.Reconnect.class)) {
			byClass.get(c).setEnabled(true);
		}
	}

	private void register(Module module) {
		modules.add(module);
		byClass.put(module.getClass(), module);
		if (module instanceof HudModule h) hud.add(h);
	}

	public List<Module> all() {
		return Collections.unmodifiableList(modules);
	}

	public List<HudModule> hud() {
		return Collections.unmodifiableList(hud);
	}

	public List<Module> byCategory(Category category) {
		return modules.stream().filter(m -> m.category() == category).toList();
	}

	@SuppressWarnings("unchecked")
	public <T extends Module> T get(Class<T> type) {
		return (T) byClass.get(type);
	}

	/** Toggles every module whose toggle key is {@code key}; shows the new state in the action bar. */
	public boolean toggleByKey(int key) {
		boolean any = false;
		for (Module m : modules) {
			KeySetting k = m.toggleKey();
			if (k == null || !k.bound() || k.key() != key) continue;
			m.toggle();
			any = true;
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) mc.player.displayClientMessage(Component.literal("§f" + m.name() + ": " + (m.enabled() ? "§aON" : "§cOFF")), true);
		}
		if (any && CarbonClient.config() != null) CarbonClient.config().saveSoon();
		return any;
	}

	public Module byId(String id) {
		if (client.id().equals(id)) return client;
		for (Module m : modules) if (m.id().equals(id)) return m;
		return null;
	}

	public void tick() {
		client.onTick();
		for (Module m : modules) {
			if (m.enabled()) m.onTick();
		}
	}

	public boolean key(int key, int action) {
		boolean consumed = action == 1 && toggleByKey(key);
		for (Module m : modules) {
			if (m.enabled() && m.onKey(key, action)) consumed = true;
		}
		return consumed;
	}
}
