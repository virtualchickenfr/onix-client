package gg.carbon.client.module.impl.hud;

import gg.carbon.client.module.Category;
import gg.carbon.client.module.HudModule;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.ui.HudStyle;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.util.Stats;
import net.minecraft.client.gui.GuiGraphics;

public class Mousestrokes extends HudModule {
	private final NumberSetting size = add(new NumberSetting("Size", "Size of the pad", 40, 24, 80, 1));
	private final NumberSetting sensitivity = add(new NumberSetting("Sensitivity", "How far the dot travels", 1.0, 0.2, 3, 0.1));
	private final NumberSetting bgOpacity = add(new NumberSetting("Background Opacity", "Panel transparency", 0.45, 0, 1, 0.05));
	private float dx, dy;
	private long last = System.nanoTime();

	public Mousestrokes() {
		super("mousestrokes", "Mousestrokes", "Visualises your mouse movement", Category.PVP, 0.005f, 0.72f);
	}

	@Override
	public float width() {
		return size.f();
	}

	@Override
	public float height() {
		return size.f();
	}

	@Override
	public void render(GuiGraphics g, boolean editor) {
		float s = size.f();
		HudStyle.panel(g, 0, 0, s, s, bgOpacity.f());
		R2D.rect(g, s / 2f - 0.25f, 4, 0.5f, s - 8, 0x22FFFFFF);
		R2D.rect(g, 4, s / 2f - 0.25f, s - 8, 0.5f, 0x22FFFFFF);

		long now = System.nanoTime();
		float dt = Math.min((now - last) / 1e9f, 0.1f);
		last = now;
		float k = 1 - (float) Math.exp(-18 * dt);
		float range = s / 2f - 5;
		float tx = (float) Math.max(-range, Math.min(range, Stats.mouseDX() * 0.35 * sensitivity.f()));
		float ty = (float) Math.max(-range, Math.min(range, Stats.mouseDY() * 0.35 * sensitivity.f()));
		dx += (tx - dx) * k;
		dy += (ty - dy) * k;

		float cx = s / 2f + dx, cy = s / 2f + dy;
		R2D.shadow(g, cx - 3, cy - 3, 6, 6, 3, 3, 0x66FFFFFF);
		R2D.circle(g, cx, cy, 2.5f, Theme.WHITE);
	}
}
