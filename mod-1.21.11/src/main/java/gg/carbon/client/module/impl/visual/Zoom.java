package gg.carbon.client.module.impl.visual;

import com.mojang.blaze3d.platform.InputConstants;
import gg.carbon.client.module.Category;
import gg.carbon.client.module.Module;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.KeySetting;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.ui.anim.Smooth;

public class Zoom extends Module {
	private final KeySetting key = add(new KeySetting("Key", "Hold to zoom", InputConstants.KEY_C));
	private final NumberSetting factor = add(new NumberSetting("Zoom Factor", "How far to zoom in", 4, 1.5, 12, 0.5).suffix("x"));
	private final BoolSetting scroll = add(new BoolSetting("Scroll To Adjust", "Use the mouse wheel to change zoom while zooming", true));
	private final BoolSetting smooth = add(new BoolSetting("Smooth Zoom", "Animate in and out of zoom", true));
	private final BoolSetting cinematic = add(new BoolSetting("Cinematic Camera", "Smooth camera movement while zoomed", false));
	private final BoolSetting sensitivity = add(new BoolSetting("Reduce Sensitivity", "Lower mouse sensitivity while zoomed", true));
	private final BoolSetting hideHand = add(new BoolSetting("Hide Hand", "Hide your hand while zoomed", true));

	private final Smooth current = new Smooth(1, 14f);
	private float scrollFactor = 1f;
	private boolean zooming;
	private boolean prevSmoothCam;

	public Zoom() {
		super("zoom", "Zoom", "Zoom in and out of objects", Category.VISUAL);
	}

	@Override
	public void onTick() {
		boolean down = key.isDown() && mc.screen == null;
		if (down && !zooming) {
			zooming = true;
			scrollFactor = 1f;
			prevSmoothCam = mc.options.smoothCamera;
			if (cinematic.on()) mc.options.smoothCamera = true;
		} else if (!down && zooming) {
			zooming = false;
			if (cinematic.on()) mc.options.smoothCamera = prevSmoothCam;
		}
	}

	@Override
	protected void onDisable() {
		if (zooming && cinematic.on()) mc.options.smoothCamera = prevSmoothCam;
		zooming = false;
	}

	public boolean zooming() {
		return enabled() && zooming;
	}

	/** Current divisor applied to the FOV. */
	public float divisor() {
		if (!enabled()) return 1f;
		float target = zooming ? factor.f() * scrollFactor : 1f;
		if (!smooth.on()) {
			current.snap(target);
			return target;
		}
		current.target(target);
		return current.get();
	}

	public boolean onScroll(double amount) {
		if (!zooming() || !scroll.on()) return false;
		scrollFactor = (float) Math.max(0.25, Math.min(8, scrollFactor * (amount > 0 ? 1.25 : 0.8)));
		return true;
	}

	public double sensitivityMultiplier() {
		return zooming() && sensitivity.on() ? 1.0 / Math.max(1, divisor()) : 1.0;
	}

	public boolean hideHand() {
		return zooming() && hideHand.on();
	}
}
