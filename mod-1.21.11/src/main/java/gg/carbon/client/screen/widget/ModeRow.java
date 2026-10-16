package gg.carbon.client.screen.widget;

import gg.carbon.client.setting.ModeSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Smooth;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/** Segmented control when the options fit, otherwise a ‹ value › cycler. */
public class ModeRow extends SettingRow<ModeSetting> {
	private final Smooth pill = new Smooth(-1, 18f);
	private final Smooth pillW = new Smooth(0, 18f);
	private final Smooth swap = new Smooth(0, 14f);
	private int lastIndex = -1;

	public ModeRow(ModeSetting setting) {
		super(setting);
	}

	private float segmentWidth(String mode) {
		return Fonts.width(mode, true, 0.85f) + 14;
	}

	private float segmentedWidth() {
		float sum = 0;
		for (String m : setting.modes()) sum += segmentWidth(m);
		return sum + 4;
	}

	private boolean segmented() {
		return setting.modes().size() <= 4 && segmentedWidth() < w * 0.48f;
	}

	@Override
	protected void renderControl(GuiGraphics g, double mx, double my) {
		float cy = controlCenterY();
		if (segmented()) {
			float total = segmentedWidth();
			float sx = x + w - 12 - total;
			R2D.rrect(g, sx, cy - 8, total, 16, 3.5f, 0xFF1A1A1E);
			float px = sx + 2;
			List<String> modes = setting.modes();
			for (String m : modes) {
				float sw = segmentWidth(m);
				if (setting.is(m)) {
					if (pill.target() < 0 || pill.get() < 0) {
						pill.snap(px);
						pillW.snap(sw);
					}
					pill.target(px);
					pillW.target(sw);
				}
				px += sw;
			}
			R2D.rrect(g, pill.get(), cy - 6, pillW.get(), 12, 2.5f, Theme.WHITE);
			px = sx + 2;
			for (String m : modes) {
				float sw = segmentWidth(m);
				float center = px + sw / 2f;
				float pc = pill.get() + pillW.get() / 2f;
				float near = 1f - Math.min(1f, Math.abs(center - pc) / (sw * 0.6f));
				int col = Theme.lerp(Theme.TEXT_DIM, 0xFF0A0A0C, near);
				Fonts.centered(g, m, center, cy - 3.4f, col, true, 0.85f);
				px += sw;
			}
		} else {
			if (lastIndex != setting.index()) {
				if (lastIndex >= 0) swap.snap(0.0f);
				swap.target(1);
				lastIndex = setting.index();
			}
			float s = swap.get();
			String v = setting.get();
			float vw = Math.max(Fonts.width(v, true, 0.85f) + 36, 90);
			float bx = x + w - 12 - vw;
			R2D.rrect(g, bx, cy - 8, vw, 16, 3.5f, 0xFF1A1A1E);
			boolean left = R2D.hover(mx, my, bx, cy - 8, 18, 16);
			boolean right = R2D.hover(mx, my, bx + vw - 18, cy - 8, 18, 16);
			Ui.chevronLeft(g, bx + 6, cy - 4, left ? Theme.WHITE : Theme.TEXT_DIM);
			Ui.chevronRight(g, bx + vw - 14, cy - 4, right ? Theme.WHITE : Theme.TEXT_DIM);
			R2D.pushAlpha(s);
			Fonts.centered(g, v, bx + vw / 2f, cy - 3.4f + (1 - s) * 3, Theme.TEXT, true, 0.85f);
			R2D.popAlpha();
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		float cy = controlCenterY();
		if (segmented()) {
			float total = segmentedWidth();
			float px = x + w - 12 - total + 2;
			for (String m : setting.modes()) {
				float sw = segmentWidth(m);
				if (R2D.hover(mx, my, px, cy - 8, sw, 16)) {
					if (!setting.is(m)) {
						setting.set(m);
						Ui.click();
					}
					return true;
				}
				px += sw;
			}
			return false;
		}
		String v = setting.get();
		float vw = Math.max(Fonts.width(v, true, 0.85f) + 36, 90);
		float bx = x + w - 12 - vw;
		if (R2D.hover(mx, my, bx, cy - 8, vw, 16)) {
			setting.cycle(mx < bx + vw / 2f && button != 1 ? -1 : 1);
			Ui.click();
			return true;
		}
		return false;
	}
}
