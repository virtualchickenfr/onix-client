package gg.carbon.client.module.impl.visual;

import com.mojang.blaze3d.platform.InputConstants;
import gg.carbon.client.module.Category;
import gg.carbon.client.module.Module;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.ColorSetting;
import gg.carbon.client.setting.KeySetting;
import gg.carbon.client.setting.ModeSetting;
import gg.carbon.client.setting.NumberSetting;
import net.minecraft.client.CameraType;

public final class VisualMods {
	private VisualMods() {}

	public static class SnapLook extends Module {
		private final KeySetting key = add(new KeySetting("Key", "Hold to look behind you", InputConstants.KEY_V));
		private CameraType previous;

		public SnapLook() {
			super("snaplook", "Snaplook", "Hold a key to see yourself from the front", Category.VISUAL);
		}

		@Override
		public void onTick() {
			boolean down = key.isDown() && mc.screen == null;
			if (down && previous == null) {
				previous = mc.options.getCameraType();
				mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			} else if (!down && previous != null) {
				mc.options.setCameraType(previous);
				previous = null;
			}
		}

		@Override
		protected void onDisable() {
			if (previous != null) mc.options.setCameraType(previous);
			previous = null;
		}
	}

	public static class AutoPerspective extends Module {
		private final BoolSetting elytra = add(new BoolSetting("Elytra", "Third person while gliding", true));
		private final BoolSetting riding = add(new BoolSetting("Riding", "Third person while riding", false));
		private CameraType previous;

		public AutoPerspective() {
			super("autoperspective", "Auto Perspective", "Switches to third person while flying or riding", Category.VISUAL);
		}

		@Override
		public void onTick() {
			if (mc.player == null) return;
			boolean want = (elytra.on() && mc.player.isFallFlying()) || (riding.on() && mc.player.isPassenger());
			if (want && previous == null) {
				previous = mc.options.getCameraType();
				mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			} else if (!want && previous != null) {
				mc.options.setCameraType(previous);
				previous = null;
			}
		}
	}

	public static class Fullbright extends Module {
		public final ModeSetting mode = add(new ModeSetting("Mode", "How the world is brightened", "Night Vision", "Night Vision", "Gamma"));
		public final NumberSetting strength = add(new NumberSetting("Strength", "Brightness boost", 1.0, 0.1, 1.0, 0.05));

		public Fullbright() {
			super("brightness", "Fullbright", "Light up dark areas", Category.VISUAL);
		}
	}

	public static class FovChanger extends Module {
		public final BoolSetting staticFov = add(new BoolSetting("Static FOV", "Ignore speed, sprint and bow FOV changes", true));
		public final BoolSetting custom = add(new BoolSetting("Custom FOV", "Override your field of view", false));
		public final NumberSetting fov = add(new NumberSetting("FOV", "Field of view", 90, 30, 130, 1).suffix("°")).visibleIf(custom::on);

		public FovChanger() {
			super("fovChanger", "FOV Changer", "Control your field of view and FOV effects", Category.VISUAL);
		}
	}

	public static class WeatherChanger extends Module {
		public WeatherChanger() {
			super("weatherchanger", "Clear Weather", "Hides rain and thunder client-side", Category.VISUAL);
		}
	}

	public static class BlockOverlay extends Module {
		public final ColorSetting color = add(new ColorSetting("Outline Color", "Colour of the block outline", 0xCCFFFFFF));
		public final NumberSetting width = add(new NumberSetting("Outline Width", "Line thickness", 2.5, 1, 8, 0.5));

		public BlockOverlay() {
			super("blockOverlay", "Block Overlay", "Customise the block selection outline", Category.VISUAL);
		}
	}

	public static class CameraTweaks extends Module {
		public final BoolSetting noHurtCam = add(new BoolSetting("No Hurt Shake", "Disable the camera shake when damaged", true));

		public CameraTweaks() {
			super("camera", "Camera", "Camera tweaks such as removing hurt shake", Category.VISUAL);
		}
	}

	public static class OwnNametag extends Module {
		public OwnNametag() {
			super("nametags", "Own Nametag", "Show your own nametag in third person", Category.VISUAL);
		}
	}

	public static class BossBar extends Module {
		public final BoolSetting hide = add(new BoolSetting("Hide", "Hide boss bars completely", false));
		public final NumberSetting scale = add(new NumberSetting("Scale", "Size of the boss bars", 1, 0.4, 1.5, 0.05)).visibleIf(() -> !hide.on());

		public BossBar() {
			super("bossBar", "Boss Bar", "Scale or hide boss bars", Category.VISUAL);
		}
	}

	public static class Scoreboard extends Module {
		public final BoolSetting hide = add(new BoolSetting("Hide", "Hide the sidebar scoreboard", false));
		public final NumberSetting scale = add(new NumberSetting("Scale", "Size of the scoreboard", 1, 0.4, 1.5, 0.05)).visibleIf(() -> !hide.on());

		public Scoreboard() {
			super("scoreboard", "Scoreboard", "Scale or hide the sidebar scoreboard", Category.VISUAL);
		}
	}

	public static class TitleTweaker extends Module {
		public final BoolSetting hide = add(new BoolSetting("Hide", "Hide titles completely", false));
		public final NumberSetting scale = add(new NumberSetting("Scale", "Size of titles", 0.7, 0.3, 1.5, 0.05)).visibleIf(() -> !hide.on());

		public TitleTweaker() {
			super("titletweaker", "Title Tweaker", "Scale or hide on-screen titles", Category.VISUAL);
		}
	}
}
