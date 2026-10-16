package gg.carbon.client.screen.widget;

import gg.carbon.client.setting.ColorSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Smooth;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphics;

/** Colour swatch that expands into an HSB + alpha picker. */
public class ColorRow extends SettingRow<ColorSetting> {
	private static final float PICKER_H = 104;
	private static final int[] PRESETS = {0xFFFFFFFF, 0xFFD4D4D8, 0xFFA1A1AA, 0xFF71717A, 0xFF3F3F46, 0xFF18181B, 0xFF000000};

	private final Smooth open = new Smooth(0, 14f);
	private boolean expanded;
	private int drag; // 0 none, 1 sv, 2 hue, 3 alpha
	private float hue, sat, bri;

	public ColorRow(ColorSetting setting) {
		super(setting);
		syncFromSetting();
	}

	private void syncFromSetting() {
		float[] hsb = setting.hsb();
		hue = hsb[0];
		sat = hsb[1];
		bri = hsb[2];
	}

	@Override
	public float height() {
		return BASE_HEIGHT + PICKER_H * open.get();
	}

	@Override
	protected void renderControl(GuiGraphics g, double mx, double my) {
		open.target(expanded ? 1 : 0);
		float cy = controlCenterY();
		String hex = String.format("#%06X", setting.get() & 0xFFFFFF);
		float hw = Fonts.width(hex, Fonts.Face.MONO, 0.8f);
		float sx = x + w - 12 - 16;
		Fonts.draw(g, hex, sx - 8 - hw, cy - 3.2f, Theme.TEXT_DIM, Fonts.Face.MONO, 0.8f, false);
		checker(g, sx, cy - 8, 16, 16);
		boolean inv = R2D.invert;
		R2D.invert = false; // the swatch shows the real colour
		R2D.rrect(g, sx, cy - 8, 16, 16, 3, setting.color());
		R2D.invert = inv;
		R2D.outline(g, sx, cy - 8, 16, 16, 3, 0.75f, 0x33FFFFFF);

		float o = open.get();
		if (o < 0.01f) return;
		float py = y + BASE_HEIGHT - 2;
		g.enableScissor((int) x, (int) py, (int) (x + w), (int) (py + PICKER_H * o + 1));
		R2D.pushAlpha(o);
		renderPicker(g, x + 10, py + 4, mx, my);
		R2D.popAlpha();
		g.disableScissor();
	}

	private void checker(GuiGraphics g, float x, float y, float w, float h) {
		R2D.rrect(g, x, y, w, h, 3, 0xFF3A3A40);
	}

	private void renderPicker(GuiGraphics g, float px, float py, double mx, double my) {
		float svW = 120, svH = 72;
		int hueColor = 0xFF000000 | (Color.HSBtoRGB(hue, 1, 1) & 0xFFFFFF);
		boolean inv = R2D.invert;
		R2D.invert = false;
		R2D.gradientH(g, px, py, svW, svH, 0xFFFFFFFF, hueColor);
		R2D.gradientV(g, px, py, svW, svH, 0x00000000, 0xFF000000);
		R2D.invert = inv;
		R2D.outline(g, px, py, svW, svH, 3, 0.75f, 0x33FFFFFF);
		float kx = px + sat * svW, ky = py + (1 - bri) * svH;
		R2D.circleOutline(g, kx, ky, 3.5f, 1.2f, Theme.WHITE);

		float bx = px + svW + 10, bw = Math.min(150, w - svW - 40);
		// hue bar
		float hy = py + 2;
		for (int i = 0; i < 6; i++) {
			int c0 = 0xFF000000 | (Color.HSBtoRGB(i / 6f, 1, 1) & 0xFFFFFF);
			int c1 = 0xFF000000 | (Color.HSBtoRGB((i + 1) / 6f, 1, 1) & 0xFFFFFF);
			R2D.gradientH(g, bx + bw * i / 6f, hy, bw / 6f + 0.5f, 6, c0, c1);
		}
		R2D.circle(g, bx + hue * bw, hy + 3, 4, Theme.WHITE);
		R2D.circle(g, bx + hue * bw, hy + 3, 2.6f, hueColor);
		// alpha bar
		float ay = hy + 16;
		R2D.rrect(g, bx, ay, bw, 6, 3, 0xFF3A3A40);
		R2D.invert = false;
		R2D.gradientH(g, bx, ay, bw, 6, setting.get() & 0x00FFFFFF, setting.get() | 0xFF000000);
		R2D.invert = inv;
		float a = setting.alpha() / 255f;
		R2D.circle(g, bx + a * bw, ay + 3, 4, Theme.WHITE);
		// presets
		float sy = ay + 16;
		for (int i = 0; i < PRESETS.length; i++) {
			float cx = bx + i * 15;
			boolean sel = (setting.get() | 0xFF000000) == PRESETS[i];
			R2D.invert = false;
			R2D.rrect(g, cx, sy, 11, 11, 3.5f, PRESETS[i]);
			R2D.invert = inv;
			R2D.outline(g, cx, sy, 11, 11, 3.5f, sel ? 1.2f : 0.6f, sel ? 0xFFFFFFFF : 0x40FFFFFF);
		}
		// chroma toggle
		float cy = sy + 20;
		Fonts.draw(g, "Chroma", bx, cy + 2, Theme.TEXT_DIM, false, 0.85f, false);
		Ui.toggle(g, bx + 40, cy, setting.chroma() ? 1 : 0, 0);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		float cy = controlCenterY();
		if (R2D.hover(mx, my, x, y, w, BASE_HEIGHT - 3)) {
			expanded = !expanded;
			if (expanded) syncFromSetting();
			Ui.click();
			return true;
		}
		if (!expanded) return false;
		float px = x + 10, py = y + BASE_HEIGHT + 2;
		float svW = 120, svH = 72;
		float bx = px + svW + 10, bw = Math.min(150, w - svW - 40);
		if (R2D.hover(mx, my, px - 2, py - 2, svW + 4, svH + 4)) {
			drag = 1;
		} else if (R2D.hover(mx, my, bx - 4, py, bw + 8, 10)) {
			drag = 2;
		} else if (R2D.hover(mx, my, bx - 4, py + 16, bw + 8, 12)) {
			drag = 3;
		} else {
			float sy = py + 34;
			for (int i = 0; i < PRESETS.length; i++) {
				if (R2D.hover(mx, my, bx + i * 15, sy, 11, 11)) {
					setting.set((setting.get() & 0xFF000000) | (PRESETS[i] & 0xFFFFFF));
					syncFromSetting();
					Ui.click();
					return true;
				}
			}
			if (R2D.hover(mx, my, bx + 40, sy + 20, 24, 13)) {
				setting.setChroma(!setting.chroma());
				Ui.click();
				return true;
			}
			return R2D.hover(mx, my, x, y, w, height());
		}
		mouseDragged(mx, my);
		return true;
	}

	@Override
	public void mouseDragged(double mx, double my) {
		if (drag == 0) return;
		float px = x + 10, py = y + BASE_HEIGHT + 2;
		float svW = 120, svH = 72;
		float bx = px + svW + 10, bw = Math.min(150, w - svW - 40);
		int alpha = setting.alpha();
		switch (drag) {
			case 1 -> {
				sat = clamp((float) (mx - px) / svW);
				bri = 1 - clamp((float) (my - py) / svH);
			}
			case 2 -> hue = clamp((float) (mx - bx) / bw);
			case 3 -> alpha = Math.round(clamp((float) (mx - bx) / bw) * 255);
			default -> {}
		}
		setting.setHsb(hue, sat, bri, alpha);
	}

	@Override
	public void mouseReleased(double mx, double my) {
		drag = 0;
	}

	private static float clamp(float v) {
		return Math.max(0, Math.min(1, v));
	}
}
