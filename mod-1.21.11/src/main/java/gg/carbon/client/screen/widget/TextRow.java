package gg.carbon.client.screen.widget;

import gg.carbon.client.setting.TextSetting;
import gg.carbon.client.ui.R2D;
import net.minecraft.client.gui.GuiGraphics;

public class TextRow extends SettingRow<TextSetting> {
	private final TextField field;

	public TextRow(TextSetting setting) {
		super(setting);
		field = new TextField(setting.get(), "Type here…", setting.maxLength(), setting::set);
	}

	private float fieldW() {
		return Math.min(170, w * 0.45f);
	}

	@Override
	protected void renderControl(GuiGraphics g, double mx, double my) {
		float fw = fieldW();
		field.render(g, x + w - 12 - fw, controlCenterY() - 8, fw, 16, mx, my, true);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		float fw = fieldW();
		boolean inside = R2D.hover(mx, my, x + w - 12 - fw, controlCenterY() - 8, fw, 16);
		field.setFocused(inside);
		return inside;
	}

	@Override
	public boolean keyPressed(int key) {
		return field.keyPressed(key, false);
	}

	@Override
	public boolean charTyped(String chars) {
		return field.charTyped(chars);
	}

	@Override
	public boolean capturing() {
		return field.focused();
	}

	@Override
	public void blur() {
		field.setFocused(false);
	}
}
