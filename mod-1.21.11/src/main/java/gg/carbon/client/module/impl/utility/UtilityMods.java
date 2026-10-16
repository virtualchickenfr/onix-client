package gg.carbon.client.module.impl.utility;

import gg.carbon.client.module.Category;
import gg.carbon.client.module.Module;
import gg.carbon.client.module.TextHudModule;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.KeySetting;
import gg.carbon.client.setting.ModeSetting;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.setting.TextSetting;
import net.minecraft.client.gui.components.toasts.Toast;

public final class UtilityMods {
	private UtilityMods() {}

	public static class AutoText extends Module {
		private static final int SLOTS = 6;
		private final TextSetting[] messages = new TextSetting[SLOTS];
		private final KeySetting[] keys = new KeySetting[SLOTS];
		private long lastSend;

		public AutoText() {
			super("autoText", "Auto Text", "Send chat messages or commands with a hotkey", Category.UTILITY);
			String[] defaults = {"gg", "/spawn", "", "", "", ""};
			for (int i = 0; i < SLOTS; i++) {
				messages[i] = add(new TextSetting("Message " + (i + 1), "Text or /command to send", defaults[i], 256));
				keys[i] = add(new KeySetting("Key " + (i + 1), "Hotkey for message " + (i + 1), 0));
			}
		}

		@Override
		public boolean onKey(int key, int action) {
			if (action != 1 || mc.getConnection() == null) return false;
			for (int i = 0; i < SLOTS; i++) {
				if (keys[i].bound() && keys[i].key() == key && !messages[i].get().isBlank()) {
					if (System.currentTimeMillis() - lastSend < 400) return true;
					lastSend = System.currentTimeMillis();
					String msg = messages[i].get().trim();
					if (msg.startsWith("/")) mc.getConnection().sendCommand(msg.substring(1));
					else mc.getConnection().sendChat(msg);
					return true;
				}
			}
			return false;
		}
	}

	public static class Reconnect extends Module {
		public final BoolSetting auto = add(new BoolSetting("Auto Reconnect", "Reconnect automatically after a countdown", false));
		public final NumberSetting delay = add(new NumberSetting("Delay", "Seconds before auto reconnecting", 5, 1, 30, 1).suffix("s")).visibleIf(auto::on);

		public Reconnect() {
			super("reconnect", "Reconnect", "Adds a reconnect button to the disconnect screen", Category.UTILITY);
		}
	}

	public static class ToastControl extends Module {
		private final ModeSetting mode = add(new ModeSetting("Hide", "Which notifications to hide", "All", "All", "Recipes & Tutorials", "Advancements"));

		public ToastControl() {
			super("toastcontrol", "Toast Control", "Hide notification toasts", Category.UTILITY);
		}

		public boolean block(Toast toast) {
			String cls = toast.getClass().getSimpleName();
			return switch (mode.get()) {
				case "All" -> true;
				case "Recipes & Tutorials" -> cls.contains("Recipe") || cls.contains("Tutorial");
				case "Advancements" -> cls.contains("Advancement");
				default -> false;
			};
		}
	}

	public static class Stopwatch extends TextHudModule {
		private final KeySetting key = add(new KeySetting("Start / Stop Key", "Toggles the stopwatch", 0));
		private final KeySetting resetKey = add(new KeySetting("Reset Key", "Resets the stopwatch", 0));
		private long start = -1;
		private long elapsed;

		public Stopwatch() {
			super("stopwatch1", "Stopwatch", "A simple stopwatch on your HUD", Category.UTILITY, 0.995f, 0.44f);
		}

		@Override
		public boolean onKey(int k, int action) {
			if (action != 1) return false;
			if (key.bound() && k == key.key()) {
				if (start >= 0) {
					elapsed += System.currentTimeMillis() - start;
					start = -1;
				} else start = System.currentTimeMillis();
				return true;
			}
			if (resetKey.bound() && k == resetKey.key()) {
				start = -1;
				elapsed = 0;
				return true;
			}
			return false;
		}

		protected String label() { return "Timer"; }

		protected String value() {
			long t = elapsed + (start >= 0 ? System.currentTimeMillis() - start : 0);
			return String.format("%02d:%02d.%02d", t / 60000, (t / 1000) % 60, (t / 10) % 100);
		}
	}
}
