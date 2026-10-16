package gg.carbon.client.module.impl.visual;

import com.mojang.blaze3d.platform.InputConstants;
import gg.carbon.client.module.Category;
import gg.carbon.client.module.Module;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.KeySetting;
import gg.carbon.client.setting.ModeSetting;
import net.minecraft.client.CameraType;

/** Dawn "perspective": look around freely without turning your player. */
public class Freelook extends Module {
	private final KeySetting key = add(new KeySetting("Key", "Key to activate freelook", InputConstants.KEY_LALT));
	private final ModeSetting mode = add(new ModeSetting("Mode", "Hold or toggle", "Hold", "Hold", "Toggle"));
	private final BoolSetting thirdPerson = add(new BoolSetting("Third Person", "Switch to third person while active", true));
	private final BoolSetting invert = add(new BoolSetting("Invert Pitch", "Invert vertical look", false));

	private boolean active;
	private float yaw, pitch;
	private CameraType previous;
	private boolean wasDown;

	public Freelook() {
		super("perspective", "Freelook", "Look around without turning your player", Category.VISUAL);
	}

	@Override
	public void onTick() {
		boolean down = key.isDown() && mc.screen == null;
		if (mode.is("Hold")) {
			if (down && !active) start();
			else if (!down && active) stop();
		} else if (down && !wasDown) {
			if (active) stop();
			else start();
		}
		wasDown = down;
	}

	private void start() {
		if (mc.player == null) return;
		active = true;
		yaw = mc.player.getYRot();
		pitch = mc.player.getXRot();
		previous = mc.options.getCameraType();
		if (thirdPerson.on() && previous.isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
	}

	private void stop() {
		active = false;
		if (previous != null) mc.options.setCameraType(previous);
		previous = null;
	}

	@Override
	protected void onDisable() {
		if (active) stop();
	}

	public boolean active() {
		return enabled() && active;
	}

	public void turn(double dx, double dy) {
		yaw += (float) dx * 0.15f;
		pitch += (float) (invert.on() ? -dy : dy) * 0.15f;
		pitch = Math.max(-90f, Math.min(90f, pitch));
	}

	public float yaw() {
		return yaw;
	}

	public float pitch() {
		return pitch;
	}
}
