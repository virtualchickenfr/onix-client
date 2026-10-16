package gg.carbon.client.screen.widget;

import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Pixel;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Smooth;
import net.minecraft.client.gui.GuiGraphics;

public class SliderRow extends SettingRow<NumberSetting> {
	private static final float TRACK = 110;
	private final Smooth fill;
	private final Smooth knob = new Smooth(0, 18f);
	private boolean dragging;

	public SliderRow(NumberSetting setting) {
		super(setting);
		fill = new Smooth((float) setting.fraction(), 20f);
	}

	private float trackX() {
		return x + w - 12 - TRACK;
	}

	@Override
	protected void renderControl(GuiGraphics g, double mx, double my) {
		float tx = trackX(), cy = controlCenterY();
		fill.target((float) setting.fraction());
		float f = fill.get();
		boolean over = R2D.hover(mx, my, tx - 4, cy - 6, TRACK + 8, 12);
		knob.target(dragging ? 1 : over ? 0.6f : 0);
		float k = knob.get();

		String value = setting.display();
		float vw = Fonts.width(value, Fonts.Face.MONO_BOLD, 0.82f);
		float px = tx - 10 - vw - 8;
		R2D.rrect(g, px, cy - 7, vw + 12, 14, 3, 0xFF1A1A1E);
		Fonts.draw(g, value, px + 6, cy - 3.2f, Theme.TEXT, Fonts.Face.MONO_BOLD, 0.82f, false);

		R2D.rect(g, tx, cy - 1, TRACK, 2, 0xFF26262B);
		R2D.rect(g, tx, cy - 1, Math.max(1, TRACK * f), 2, Theme.WHITE);
		for (int i = 1; i < 10; i++) {
			R2D.rect(g, tx + TRACK * i / 10f - 0.5f, cy + 3.5f, 1, 1.5f, i / 10f <= f ? 0x66FFFFFF : 0x22FFFFFF);
		}
		float r = 3.5f + k * 0.8f;
		float kx = tx + TRACK * f;
		if (k > 0.01f) R2D.rrect(g, kx - r - 3 * k, cy - r - 3 * k, (r + 3 * k) * 2, (r + 3 * k) * 2, 2.5f, Theme.alpha(0x26FFFFFF, k));
		R2D.shadow(g, kx - r, cy - r, r * 2, r * 2, 1.5f, 2, 0x66000000);
		R2D.rrect(g, Pixel.snap(kx - r), cy - r, r * 2, r * 2, 1.5f, Theme.WHITE);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		float tx = trackX(), cy = controlCenterY();
		if (R2D.hover(mx, my, tx - 6, cy - 8, TRACK + 12, 16)) {
			dragging = true;
			update(mx);
			return true;
		}
		return false;
	}

	@Override
	public void mouseDragged(double mx, double my) {
		if (dragging) update(mx);
	}

	@Override
	public void mouseReleased(double mx, double my) {
		dragging = false;
	}

	private void update(double mx) {
		setting.setFraction((mx - trackX()) / TRACK);
	}
}
