package gg.carbon.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/** Shared look for all HUD elements: a translucent stone tile with a thin depth edge. */
public final class HudStyle {
	private HudStyle() {}

	public static final float PAD_X = 6f;
	public static final float HEIGHT = 16f;
	public static final float RADIUS = 4f;

	public static void panel(GuiGraphics g, float x, float y, float w, float h, float opacity) {
		int a = Math.round(opacity * 255);
		R2D.rrect(g, x, y, w, h, RADIUS, Theme.withAlpha(0x050506, Math.round(a * 1.1f)));
		R2D.rrect(g, x, y, w, h - 1.5f, RADIUS, Theme.withAlpha(0x141417, a));
		R2D.outline(g, x, y, w, h, RADIUS, 0.5f, Theme.withAlpha(0xFFFFFF, Math.round(a * 0.14f)));
		R2D.rect(g, x + RADIUS, y + 0.75f, w - RADIUS * 2, 0.5f, Theme.withAlpha(0xFFFFFF, Math.round(a * 0.10f)));
	}
}
