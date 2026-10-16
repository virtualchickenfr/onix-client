package gg.carbon.client.screen.widget;

import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.anim.Smooth;
import net.minecraft.client.gui.GuiGraphics;

public class BoolRow extends SettingRow<BoolSetting> {
	private final Smooth state;

	public BoolRow(BoolSetting setting) {
		super(setting);
		state = new Smooth(setting.on() ? 1 : 0, 18f);
	}

	@Override
	protected void renderControl(GuiGraphics g, double mx, double my) {
		state.target(setting.on() ? 1 : 0);
		float tx = x + w - 12 - Ui.toggleWidth();
		float ty = controlCenterY() - Ui.toggleHeight() / 2f;
		Ui.toggle(g, tx, ty, state.get(), hover.get());
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (R2D.hover(mx, my, x, y, w, BASE_HEIGHT - 3)) {
			setting.toggle();
			Ui.click();
			return true;
		}
		return false;
	}
}
