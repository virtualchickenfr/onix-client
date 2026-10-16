package gg.carbon.client.screen.widget;

import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.ColorSetting;
import gg.carbon.client.setting.KeySetting;
import gg.carbon.client.setting.ModeSetting;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.setting.Setting;
import gg.carbon.client.setting.TextSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Smooth;
import net.minecraft.client.gui.GuiGraphics;

/** One row in the module settings page. Subclasses implement the control on the right side. */
public abstract class SettingRow<S extends Setting<?>> {
	public static final float BASE_HEIGHT = 34;

	protected final S setting;
	protected final Smooth hover = new Smooth(0, 16f);
	protected final Smooth visibility;
	protected float x, y, w;

	protected SettingRow(S setting) {
		this.setting = setting;
		this.visibility = new Smooth(setting.visible() ? 1 : 0, 14f);
	}

	public S setting() {
		return setting;
	}

	public static SettingRow<?> of(Setting<?> s) {
		if (s instanceof BoolSetting b) return new BoolRow(b);
		if (s instanceof NumberSetting n) return new SliderRow(n);
		if (s instanceof ModeSetting m) return new ModeRow(m);
		if (s instanceof ColorSetting c) return new ColorRow(c);
		if (s instanceof KeySetting k) return new KeyRow(k);
		if (s instanceof TextSetting t) return new TextRow(t);
		throw new IllegalArgumentException("Unknown setting " + s);
	}

	/** Animated visibility (rows collapse when their dependency is off). */
	public float visibleFactor() {
		visibility.target(setting.visible() ? 1 : 0);
		return visibility.get();
	}

	public float height() {
		return BASE_HEIGHT;
	}

	public void layout(float x, float y, float w) {
		this.x = x;
		this.y = y;
		this.w = w;
	}

	public void render(GuiGraphics g, double mx, double my) {
		boolean hovered = R2D.hover(mx, my, x, y, w, BASE_HEIGHT);
		hover.target(hovered ? 1 : 0);
		float h = hover.get();
		R2D.rrect(g, x, y, w, BASE_HEIGHT - 3, 5, Theme.lerp(0xFF111113, 0xFF161619, h));
		R2D.outline(g, x, y, w, BASE_HEIGHT - 3, 5, 0.75f, Theme.alpha(0xFFFFFFFF, 0.05f + 0.06f * h));
		R2D.rect(g, x, y + 8, 1.5f * h, BASE_HEIGHT - 19, Theme.alpha(0xFFFFFFFF, h));
		Fonts.draw(g, setting.name(), x + 10, y + 7, Theme.TEXT, true, 1f, false);
		String desc = Fonts.trim(description(), w - 140, false);
		Fonts.draw(g, desc, x + 10, y + 18, Theme.TEXT_MUTED, false, 0.8f, false);
		renderControl(g, mx, my);
	}

	protected String description() {
		return setting.description();
	}

	protected abstract void renderControl(GuiGraphics g, double mx, double my);

	public boolean mouseClicked(double mx, double my, int button) {
		return false;
	}

	public void mouseDragged(double mx, double my) {}

	public void mouseReleased(double mx, double my) {}

	public boolean keyPressed(int key) {
		return false;
	}

	public boolean charTyped(String chars) {
		return false;
	}

	/** True while the row wants all keyboard input (key binding, text field). */
	public boolean capturing() {
		return false;
	}

	public void blur() {}

	protected float controlCenterY() {
		return y + (BASE_HEIGHT - 3) / 2f;
	}
}
