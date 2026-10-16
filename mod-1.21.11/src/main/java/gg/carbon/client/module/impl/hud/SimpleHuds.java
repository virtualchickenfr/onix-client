package gg.carbon.client.module.impl.hud;

import gg.carbon.client.module.Category;
import gg.carbon.client.module.TextHudModule;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.ModeSetting;
import gg.carbon.client.util.Stats;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.world.phys.Vec3;

/** The compact text based HUD modules. */
public final class SimpleHuds {
	private SimpleHuds() {}

	public static class Fps extends TextHudModule {
		public Fps() {
			super("fps", "FPS", "Shows your frames per second", 0.005f, 0.02f);
		}

		protected String label() { return "FPS"; }

		protected String value() { return String.valueOf(mc.getFps()); }
	}

	public static class Cps extends TextHudModule {
		private final BoolSetting right = add(new BoolSetting("Right Click", "Also show right click CPS", true));

		public Cps() {
			super("cps", "CPS", "Shows your clicks per second", 0.005f, 0.08f);
		}

		protected String label() { return "CPS"; }

		protected String value() {
			return right.on() ? Stats.leftCps() + " | " + Stats.rightCps() : String.valueOf(Stats.leftCps());
		}
	}

	public static class Ping extends TextHudModule {
		public Ping() {
			super("ping", "Ping", "Shows your latency to the server", 0.005f, 0.14f);
		}

		protected String label() { return "Ping"; }

		protected String value() {
			if (mc.player == null || mc.getConnection() == null) return "0 ms";
			PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			return (info == null ? 0 : info.getLatency()) + " ms";
		}
	}

	public static class Clock extends TextHudModule {
		private final ModeSetting format = add(new ModeSetting("Format", "Clock format", "24h", "24h", "12h"));
		private final BoolSetting seconds = add(new BoolSetting("Seconds", "Show seconds", false));

		public Clock() {
			super("time", "Clock", "Shows the real-world time", 0.995f, 0.02f);
		}

		protected String label() { return "Time"; }

		protected String value() {
			String pattern = format.is("24h") ? (seconds.on() ? "HH:mm:ss" : "HH:mm") : (seconds.on() ? "h:mm:ss a" : "h:mm a");
			return LocalTime.now().format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
		}
	}

	public static class Speed extends TextHudModule {
		private final BoolSetting vertical = add(new BoolSetting("Include Vertical", "Measure 3D speed instead of horizontal", false));

		public Speed() {
			super("speedMeter", "Speed Meter", "Shows your movement speed in blocks/s", 0.005f, 0.26f);
		}

		protected String label() { return "Speed"; }

		protected String value() {
			if (mc.player == null) return "0.00 b/s";
			Vec3 d = mc.player.position().subtract(mc.player.xo, mc.player.yo, mc.player.zo);
			double v = vertical.on() ? d.length() : Math.sqrt(d.x * d.x + d.z * d.z);
			return String.format(Locale.ROOT, "%.2f b/s", v * 20);
		}
	}

	public static class Playtime extends TextHudModule {
		public Playtime() {
			super("playtime", "Playtime", "Shows how long this session has been running", 0.995f, 0.08f);
		}

		protected String label() { return "Session"; }

		protected String value() {
			long s = (System.currentTimeMillis() - Stats.SESSION_START) / 1000;
			return s >= 3600 ? String.format("%dh %02dm", s / 3600, (s % 3600) / 60) : String.format("%dm %02ds", s / 60, s % 60);
		}
	}

	public static class ServerAddress extends TextHudModule {
		public ServerAddress() {
			super("serverAddress", "Server Address", "Shows the address of the server you are on", 0.995f, 0.14f);
		}

		protected String label() { return "IP"; }

		protected String value() {
			ServerData data = mc.getCurrentServer();
			if (data != null) return data.ip;
			return mc.hasSingleplayerServer() ? "Singleplayer" : "—";
		}
	}

	public static class Memory extends TextHudModule {
		private final ModeSetting mode = add(new ModeSetting("Mode", "What to display", "Percent", "Percent", "Used / Max"));

		public Memory() {
			super("systemresources", "System Resources", "Shows Java memory usage", 0.995f, 0.20f);
		}

		protected String label() { return "Mem"; }

		protected String value() {
			Runtime rt = Runtime.getRuntime();
			long used = rt.totalMemory() - rt.freeMemory();
			long max = rt.maxMemory();
			if (mode.is("Percent")) return (used * 100 / max) + "%";
			return (used >> 20) + " / " + (max >> 20) + " MB";
		}
	}

	public static class Tps extends TextHudModule {
		public Tps() {
			super("tps", "TPS", "Estimates the server's ticks per second", 0.995f, 0.26f);
		}

		protected String label() { return "TPS"; }

		protected String value() {
			return String.format(Locale.ROOT, "%.1f", Stats.tps());
		}
	}

	public static class Combo extends TextHudModule {
		public Combo() {
			super("comboDisplay", "Combo Display", "Counts your consecutive hits", Category.PVP, 0.5f, 0.62f);
		}

		protected String label() { return "Combo"; }

		protected String value() {
			int c = Stats.combo();
			return c == 0 ? "No combo" : c + (c == 1 ? " hit" : " hits");
		}
	}

	public static class Reach extends TextHudModule {
		public Reach() {
			super("reachDisplay", "Reach Display", "Shows the distance of your last hit", Category.PVP, 0.5f, 0.68f);
		}

		protected String label() { return "Reach"; }

		protected String value() {
			double r = Stats.lastReach();
			return r < 0 ? "—" : String.format(Locale.ROOT, "%.2f blocks", r);
		}
	}

	public static class JumpReset extends TextHudModule {
		public JumpReset() {
			super("jumpreset", "Jump Reset", "Shows how many ticks after a hit you jumped", Category.PVP, 0.5f, 0.74f);
		}

		protected String label() { return "Jump Reset"; }

		protected String value() {
			int d = Stats.lastJumpDelay();
			if (d < 0) return "—";
			return d + (d == 1 ? " tick" : " ticks") + (d <= 1 ? "  perfect" : "");
		}
	}

	public static class Saturation extends TextHudModule {
		public Saturation() {
			super("saturation", "Saturation", "Shows your hidden food saturation", 0.6f, 0.92f);
		}

		protected String label() { return "Saturation"; }

		protected String value() {
			if (mc.player == null) return "0.0";
			return String.format(Locale.ROOT, "%.1f", mc.player.getFoodData().getSaturationLevel());
		}
	}

	public static class PackDisplay extends TextHudModule {
		public PackDisplay() {
			super("packdisplay", "Pack Display", "Shows your active resource pack", 0.995f, 0.32f);
		}

		protected String label() { return "Pack"; }

		protected String value() {
			String name = "Default";
			for (Pack pack : mc.getResourcePackRepository().getSelectedPacks()) {
				if (pack.isRequired()) continue;
				String id = pack.getId();
				if (id.equals("vanilla") || id.startsWith("fabric") || id.equals("carbon")) continue;
				name = pack.getTitle().getString();
			}
			return name;
		}
	}

	public static class DeathInfo extends TextHudModule {
		private final BoolSetting chat = add(new BoolSetting("Chat Message", "Post your death position in chat", true));
		private String last = "—";
		private boolean wasDead;

		public DeathInfo() {
			super("deathInfo", "Death Info", "Remembers where you died last", Category.UTILITY, 0.995f, 0.38f);
		}

		protected String label() { return "Death"; }

		protected String value() { return last; }

		@Override
		public void onTick() {
			if (mc.player == null) return;
			boolean dead = mc.player.isDeadOrDying();
			if (dead && !wasDead) {
				last = String.format(Locale.ROOT, "%d, %d, %d", mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
				if (chat.on()) {
					mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§7[§fCarbon§7] §fYou died at §7" + last
						+ " §8(" + mc.player.level().dimension().identifier().getPath() + ")"), false);
				}
			}
			wasDead = dead;
		}
	}
}
