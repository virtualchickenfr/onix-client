package gg.carbon.client.module;

import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.ColorSetting;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.HudStyle;
import net.minecraft.client.gui.GuiGraphics;

/** Single-line "LABEL value" HUD element in Carbon style. */
public abstract class TextHudModule extends HudModule {
	public final BoolSetting background = add(new BoolSetting("Background", "Draw a rounded panel behind the text", true));
	public final NumberSetting bgOpacity = add(new NumberSetting("Background Opacity", "Panel transparency", 0.45, 0, 1, 0.05)).visibleIf(background::on);
	public final BoolSetting showLabel = add(new BoolSetting("Show Label", "Show the name next to the value", true));
	public final ColorSetting valueColor = add(new ColorSetting("Value Color", "Colour of the value", 0xFFFFFFFF));
	public final ColorSetting labelColor = add(new ColorSetting("Label Color", "Colour of the label", 0xFF9A9AA2)).visibleIf(showLabel::on);
	public final BoolSetting shadow = add(new BoolSetting("Text Shadow", "Draw a drop shadow under the text", false));

	protected TextHudModule(String id, String name, String description, float rx, float ry) {
		super(id, name, description, rx, ry);
	}

	protected TextHudModule(String id, String name, String description, Category category, float rx, float ry) {
		super(id, name, description, category, rx, ry);
	}

	protected abstract String label();

	protected abstract String value();

	private String labelText() {
		return showLabel.on() ? label().toUpperCase() : "";
	}

	private float textWidth() {
		String l = labelText();
		float w = Fonts.width(value(), true, 1f);
		if (!l.isEmpty()) w += Fonts.width(l, Fonts.Face.MEDIUM, 0.84f) + 5;
		return w;
	}

	@Override
	public float width() {
		return textWidth() + (background.on() ? HudStyle.PAD_X * 2 : 0);
	}

	@Override
	public float height() {
		return background.on() ? HudStyle.HEIGHT : 9;
	}

	@Override
	public void render(GuiGraphics g, boolean editor) {
		float w = width(), h = height();
		if (background.on()) HudStyle.panel(g, 0, 0, w, h, bgOpacity.f());
		float x = background.on() ? HudStyle.PAD_X : 0;
		float ty = (h - 8) / 2f;
		String l = labelText();
		if (!l.isEmpty()) {
			Fonts.draw(g, l, x, ty + 0.7f, labelColor.color(), Fonts.Face.MEDIUM, 0.84f, shadow.on());
			x += Fonts.width(l, Fonts.Face.MEDIUM, 0.84f) + 5;
		}
		Fonts.draw(g, value(), x, ty, valueColor.color(), true, 1f, shadow.on());
	}
}
