package gg.carbon.client.ui;

import gg.carbon.client.ui.text.CarbonFont;
import gg.carbon.client.ui.text.TextDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;

/**
 * Carbon typography, rendered by {@link TextDraw}: every glyph is rasterised by FreeType at the
 * exact on-screen pixel size and placed on whole device pixels.
 *
 * <ul>
 *   <li>Inter — interface text</li>
 *   <li>Pixelify Sans — large headlines only (1-bit rendered, so the pixel edges stay hard)</li>
 *   <li>JetBrains Mono — numbers, values, key caps</li>
 * </ul>
 */
public final class Fonts {
	private Fonts() {}

	public enum Face {
		BODY(new CarbonFont("inter_500.ttf", false, 0.80f), 8.5f),
		BODY_BOLD(new CarbonFont("inter_700.ttf", false, 0.80f), 8.5f),
		MEDIUM(new CarbonFont("inter_600.ttf", false, 0.80f), 8.5f),
		LIGHT(new CarbonFont("inter_400.ttf", false, 0.80f), 8.5f),
		/** Legacy alias: small pixel-font labels are now set in Inter Bold for legibility. */
		PIXEL(new CarbonFont("inter_700.ttf", false, 0.80f), 8.5f),
		DISPLAY(new CarbonFont("pixelify_700.ttf", true, 0.80f), 8f),
		MONO(new CarbonFont("mono_500.ttf", false, 0.80f), 8f),
		MONO_BOLD(new CarbonFont("mono_700.ttf", false, 0.80f), 8f);
		public final CarbonFont font;
		/** Em size in GUI units at scale 1. */
		public final float em;

		Face(CarbonFont font, float em) {
			this.font = font;
			this.em = em;
		}
	}

	/** Toggled by the "Custom Font" setting; falls back to the vanilla font when disabled. */
	public static boolean enabled = true;

	public static Font font() {
		return Minecraft.getInstance().font;
	}

	public static float width(String text, Face face, float scale) {
		if (!enabled) return font().width(text) * scale;
		return face.font.width(text) * face.em * scale;
	}

	public static void draw(GuiGraphics g, String text, float x, float y, int color, Face face, float scale, boolean shadow) {
		int col = R2D.c(color);
		if (((col >>> 24) & 0xFF) < 4 || text.isEmpty()) return;
		if (!enabled) {
			Matrix3x2fStack p = g.pose();
			p.pushMatrix();
			p.translate(x, y);
			if (scale != 1f) p.scale(scale, scale);
			g.drawString(font(), text, 0, 0, col, shadow);
			p.popMatrix();
			return;
		}
		TextDraw.draw(g, face.font, text, x, y, face.em * scale, col, shadow);
	}

	public static void centered(GuiGraphics g, String text, float cx, float y, int color, Face face, float scale) {
		draw(g, text, cx - width(text, face, scale) / 2f, y, color, face, scale, false);
	}

	// ---------------------------------------------------------------- body text shortcuts

	public static float width(String text) {
		return width(text, Face.BODY, 1f);
	}

	public static float widthBold(String text) {
		return width(text, Face.BODY_BOLD, 1f);
	}

	public static float width(String text, boolean bold, float scale) {
		return width(text, bold ? Face.BODY_BOLD : Face.BODY, scale);
	}

	public static void draw(GuiGraphics g, String text, float x, float y, int color) {
		draw(g, text, x, y, color, Face.BODY, 1f, false);
	}

	public static void bold(GuiGraphics g, String text, float x, float y, int color) {
		draw(g, text, x, y, color, Face.BODY_BOLD, 1f, false);
	}

	public static void draw(GuiGraphics g, String text, float x, float y, int color, boolean bold, float scale, boolean shadow) {
		draw(g, text, x, y, color, bold ? Face.BODY_BOLD : Face.BODY, scale, shadow);
	}

	public static void centered(GuiGraphics g, String text, float cx, float y, int color, boolean bold, float scale) {
		centered(g, text, cx, y, color, bold ? Face.BODY_BOLD : Face.BODY, scale);
	}

	public static void drawComponent(GuiGraphics g, Component text, float x, float y, int color, float scale, boolean shadow) {
		draw(g, text.getString(), x, y, color, Face.BODY, scale, shadow);
	}

	// ---------------------------------------------------------------- display

	/** Pixelify headline; {@code size} is the em size in GUI units. */
	public static void display(GuiGraphics g, String text, float x, float y, int color, float size) {
		draw(g, text, x, y, color, Face.DISPLAY, size / Face.DISPLAY.em, false);
	}

	public static float displayWidth(String text, float size) {
		return width(text, Face.DISPLAY, size / Face.DISPLAY.em);
	}

	/** Trims text with an ellipsis so it fits in {@code maxWidth}. */
	public static String trim(String text, float maxWidth, boolean bold) {
		if (width(text, bold, 1f) <= maxWidth) return text;
		String ell = "…";
		for (int i = text.length() - 1; i > 0; i--) {
			String t = text.substring(0, i).stripTrailing() + ell;
			if (width(t, bold, 1f) <= maxWidth) return t;
		}
		return ell;
	}
}
