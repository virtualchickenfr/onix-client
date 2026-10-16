package gg.carbon.client.module;

import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A draggable on-screen element. Its position is stored relative to the free screen space, so
 * layouts survive window and GUI-scale changes.
 */
public abstract class HudModule extends Module {
	private float rx;
	private float ry;
	private final float defaultRx;
	private final float defaultRy;
	public final NumberSetting scale = add(new NumberSetting("Scale", "Size of this element", 1.0, 0.5, 2.5, 0.05));

	protected HudModule(String id, String name, String description, float rx, float ry) {
		this(id, name, description, Category.HUD, rx, ry);
	}

	protected HudModule(String id, String name, String description, Category category, float rx, float ry) {
		super(id, name, description, category);
		this.rx = rx;
		this.ry = ry;
		this.defaultRx = rx;
		this.defaultRy = ry;
	}

	public float defaultRx() {
		return defaultRx;
	}

	public float defaultRy() {
		return defaultRy;
	}

	/** Unscaled width in GUI units. */
	public abstract float width();

	/** Unscaled height in GUI units. */
	public abstract float height();

	/** Draws the element at the origin (0,0); the caller applies position and scale. */
	public abstract void render(GuiGraphics g, boolean editor);

	/** Whether the element should currently be drawn in-game. */
	public boolean shouldRender() {
		return true;
	}

	public float scaledWidth() {
		return width() * scale.f();
	}

	public float scaledHeight() {
		return height() * scale.f();
	}

	public float x() {
		return rx * Math.max(0, screenW() - scaledWidth());
	}

	public float y() {
		return ry * Math.max(0, screenH() - scaledHeight());
	}

	public void setPosition(float x, float y) {
		float fw = Math.max(1, screenW() - scaledWidth());
		float fh = Math.max(1, screenH() - scaledHeight());
		rx = Math.max(0, Math.min(1, x / fw));
		ry = Math.max(0, Math.min(1, y / fh));
	}

	public float rx() {
		return rx;
	}

	public float ry() {
		return ry;
	}

	public void setRelative(float rx, float ry) {
		this.rx = Math.max(0, Math.min(1, rx));
		this.ry = Math.max(0, Math.min(1, ry));
	}

	protected static float screenW() {
		return mc.getWindow().getGuiScaledWidth();
	}

	protected static float screenH() {
		return mc.getWindow().getGuiScaledHeight();
	}

	protected static BoolSetting bool(String name, String desc, boolean def) {
		return new BoolSetting(name, desc, def);
	}
}
