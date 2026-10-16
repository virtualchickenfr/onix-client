package gg.carbon.client.screen.widget;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Fonts.Face;
import gg.carbon.client.ui.Pixel;
import gg.carbon.client.ui.PixelIcons;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Easing;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

/** Carbon's component kit: block buttons, stone cards, inventory slots, labels and tickers. */
public final class Ui {
	private Ui() {}

	public static void click() {
		if (CarbonClient.modules() != null && !CarbonClient.modules().client.clickSounds.on()) return;
		AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
	}

	/** Light palette for the main menu designs (set by the menu host while a design renders). */
	public static boolean light;

	public static float speed() {
		return CarbonClient.modules() == null ? 1f : CarbonClient.modules().client.animationSpeed.f();
	}

	public static long ms(long base) {
		return Math.round(base / speed());
	}

	// ------------------------------------------------------------------ block surfaces

	/**
	 * A rounded block with depth: a darker band along the bottom edge and a faint top highlight,
	 * like a Minecraft block seen slightly from above.
	 */
	public static void block(GuiGraphics g, float x, float y, float w, float h, float r, float depth, int face, int dark, int edge) {
		R2D.rrect(g, x, y, w, h, r, dark);
		R2D.rrect(g, x, y, w, h - depth, r, face);
		if (depth > 0) {
			// square off the face's lower corners, but stay inside the outer rounding
			float in = depth < r ? r - (float) Math.sqrt(r * r - (r - depth) * (r - depth)) : 0;
			R2D.rect(g, x + Math.max(0.5f, in), y + h - depth - Math.min(r, depth), w - 2 * Math.max(0.5f, in), Math.min(r, depth), face);
		}
		if (edge != 0) R2D.outline(g, x, y, w, h, r, 0.75f, edge);
		R2D.rect(g, x + r, y + 0.75f, w - r * 2, 0.75f, light ? 0x60FFFFFF : 0x14FFFFFF);
	}

	/**
	 * Block button. {@code hover} lifts it and sweeps a sheen across, {@code press} pushes it
	 * into its depth band.
	 */
	public static void button(GuiGraphics g, String label, String icon, float x, float y, float w, float h,
		boolean primary, float hover, float press, float sheen) {
		// whole-pixel motion: the button moves as one piece, so edges and text never shimmer
		float s = R2D.scale(g);
		float lift = Math.round((-2.5f * hover + 2f * press) * s) / s;
		float depth = Math.round((4 - 2.5f * press) * s) / s;
		int face, dark, text, edge;
		if (light) {
			face = primary ? Theme.lerp(0xFF1B1B1F, 0xFF2A2A30, hover) : Theme.lerp(0xFFFFFFFF, 0xFFF3F3F5, hover);
			dark = primary ? 0xFF000000 : 0xFFC4C4CB;
			text = primary ? 0xFFF4F4F6 : 0xFF17171A;
			edge = primary ? 0 : Theme.alpha(0xFF000000, 0.10f + 0.10f * hover);
		} else {
			face = primary ? Theme.lerp(0xFFE6E6E9, 0xFFFFFFFF, hover) : Theme.lerp(Theme.STONE_2, Theme.STONE_3, hover);
			dark = primary ? 0xFF8C8C94 : Theme.STONE_DARK;
			text = primary ? 0xFF0B0B0D : Theme.TEXT;
			edge = primary ? 0 : 0x22FFFFFF;
		}
		R2D.push(g);
		R2D.translate(g, 0, lift);
		R2D.shadow(g, x + 2, y + h - 6, w - 4, 8, 4, 9, Theme.alpha(light ? 0x40000000 : 0x90000000, 0.55f + 0.45f * hover));
		block(g, x, y, w, h, 5, depth, face, dark, edge);
		if (sheen > 0 && sheen < 1) {
			float bw = w * 0.45f;
			float sx = x - bw + (w + bw) * Easing.SMOOTH.apply(sheen);
			int sheenColor = light ? (primary ? 0x30FFFFFF : 0x10000000) : (primary ? 0x70FFFFFF : 0x24FFFFFF);
			R2D.sheen(g, x + 4, x + w - 4, y + 1.5f, h - depth - 2.5f, sx, bw, sheenColor);
		}
		float tw = Fonts.width(label, Face.MEDIUM, 1f);
		float iw = icon == null ? 0 : (label.isEmpty() ? 9 : 14);
		float cx = x + (w - tw - iw) / 2f;
		float ty = y + (h - depth - 8.5f) / 2f;
		if (icon != null) PixelIcons.draw(g, icon, cx, ty - 0.2f, 9, text);
		Fonts.draw(g, label, cx + iw, ty, text, Face.MEDIUM, 1f, false);
		R2D.pop(g);
	}

	/** Stone card with depth and a soft light that follows the cursor. */
	public static void card(GuiGraphics g, float x, float y, float w, float h, float hover, float active, double mx, double my) {
		R2D.shadow(g, x, y + 6, w, h, 10, 12, Theme.alpha(0x80000000, 0.55f + 0.45f * hover));
		int face = Theme.lerp(Theme.STONE, 0xFF1C1C20, hover);
		int edge = Theme.lerp(Theme.withAlpha(0x34343A, 255), 0xFF5A5A62, Math.max(hover, active * 0.6f));
		block(g, x, y, w, h, 9, 4, face, Theme.STONE_DARK, edge);
		if (hover > 0.01f) {
			// glow is clamped so its soft edge always stays inside the card (no scissor = no edge artefacts)
			float reach = 6 + 14;
			float gx = (float) Math.max(x + reach, Math.min(x + w - reach, mx));
			float gy = (float) Math.max(y + reach, Math.min(y + h - 4 - reach, my));
			R2D.shadow(g, gx - 6, gy - 6, 12, 12, 6, 14, Theme.alpha(0x18FFFFFF, hover));
		}
	}

	/** Inventory-style slot holding a pixel icon. Active slots glow. */
	public static void slot(GuiGraphics g, String icon, float x, float y, float size, float active, float hover) {
		R2D.rrect(g, x, y, size, size, 4, Theme.STONE_DARK);
		float b = Math.max(1.5f, size * 0.07f);
		R2D.rect(g, x + 2, y + 1, size - 4, b, 0x70000000);
		R2D.rect(g, x + 1, y + 2, b, size - 4, 0x70000000);
		R2D.rect(g, x + 2, y + size - 1 - b, size - 4, b, 0x10FFFFFF);
		R2D.rect(g, x + size - 1 - b, y + 2, b, size - 4, 0x10FFFFFF);
		R2D.outline(g, x, y, size, size, 4, 0.75f, Theme.STONE_EDGE);
		float glow = Math.max(active * 0.75f, hover * 0.5f);
		if (glow > 0.01f) {
			float c = size / 2f;
			R2D.shadow(g, x + c - size * 0.18f, y + c - size * 0.18f, size * 0.36f, size * 0.36f, size * 0.18f, size * 0.3f,
				Theme.alpha(0x40FFFFFF, glow));
		}
		float inner = size * 0.56f;
		int col = Theme.lerp(0xFF7C7C86, Theme.WHITE, Math.max(active, hover * 0.6f));
		PixelIcons.draw(g, icon, x + (size - inner) / 2f, y + (size - inner) / 2f, inner, col);
	}

	/** "— LABEL" kicker line above headings. */
	public static void eyebrow(GuiGraphics g, String text, float x, float y, int color) {
		R2D.rect(g, x, y + 3, 8, 1, color);
		float cx = x + 13;
		for (char ch : text.toUpperCase().toCharArray()) {
			String s = String.valueOf(ch);
			Fonts.draw(g, s, cx, y, color, Face.BODY_BOLD, 0.74f, false);
			cx += Fonts.width(s, Face.BODY_BOLD, 0.74f) + 0.9f;
		}
	}

	/** Endless marquee of pixel caps separated by small squares. */
	public static void ticker(GuiGraphics g, List<String> items, float x, float y, float w, float speed, int color) {
		float gap = 18;
		float total = 0;
		for (String s : items) total += Fonts.width(s.toUpperCase(), Face.PIXEL, 0.95f) + gap * 2;
		if (total <= 0) return;
		float off = (float) ((System.currentTimeMillis() / 1000.0 * speed) % total);
		g.enableScissor((int) x, (int) y - 2, (int) (x + w), (int) (y + 12));
		float cx = x - off;
		while (cx < x + w) {
			for (String s : items) {
				String up = s.toUpperCase();
				float sw = Fonts.width(up, Face.PIXEL, 0.95f);
				if (cx + sw > x - 20 && cx < x + w + 20) {
					Fonts.draw(g, up, cx, y, color, Face.PIXEL, 0.95f, false);
				}
				cx += sw + gap;
				R2D.shadow(g, cx - 2.5f, y + 1.8f, 5, 5, 0.5f, 3, Theme.alpha(color, 0.25f));
				Pixel.block(g, cx - 2.5f, y + 1.8f, 5, Theme.alpha(color, 0.9f));
				cx += gap;
			}
		}
		g.disableScissor();
		R2D.gradientH(g, x, y - 2, 30, 14, 0xFF0B0B0D, 0x000B0B0D);
		R2D.gradientH(g, x + w - 30, y - 2, 30, 14, 0x000B0B0D, 0xFF0B0B0D);
	}

	// ------------------------------------------------------------------ controls

	/** Lever-style switch with block depth. */
	public static void toggle(GuiGraphics g, float x, float y, float t, float hover) {
		float w = toggleWidth(), h = toggleHeight();
		R2D.rrect(g, x, y, w, h, 3, Theme.STONE_DARK);
		R2D.rect(g, x + 2, y + 1, w - 4, 1, 0x70000000);
		int fill = Theme.lerp(Theme.withAlpha(0x2A2A2F, 255), 0xFFE6E6E9, t);
		if (t > 0.01f) R2D.rrect(g, x + 1, y + 1, (w - 2) * Math.max(0.35f, t), h - 2, 2.5f, Theme.alpha(fill, t));
		R2D.outline(g, x, y, w, h, 3, 0.75f, Theme.alpha(0xFFFFFFFF, 0.12f + 0.12f * hover));
		float k = h - 3;
		float kx = Pixel.snap(x + 1.5f + (w - 3 - k) * t);
		int knob = Theme.lerp(Theme.lerp(0xFF5A5A62, 0xFF6E6E78, hover), 0xFF0B0B0D, t);
		R2D.rrect(g, kx, y + 1.5f, k, k, 2, knob);
		R2D.rect(g, kx + 1.5f, y + 1.5f + k - 1.5f, k - 3, 1, Theme.alpha(0xFF000000, 0.5f));
	}

	public static float toggleWidth() {
		return 24;
	}

	public static float toggleHeight() {
		return 13;
	}

	/** Small rounded key cap in mono font, e.g. ESC or a keybind. */
	public static float keycap(GuiGraphics g, String key, float x, float y, float hover) {
		float w = Math.max(16, Fonts.width(key, Face.MONO_BOLD, 0.72f) + 10), h = 13;
		if (light) {
			block(g, x, y, w, h, 3, 2, 0xFFFFFFFF, 0xFFC8C8CE, 0x1A000000);
			Fonts.centered(g, key, x + w / 2f, y + 2.6f, 0xFF3A3A42, Face.MONO_BOLD, 0.72f);
			return w;
		}
		block(g, x, y, w, h, 3, 2, Theme.lerp(Theme.STONE_2, Theme.STONE_3, hover), Theme.STONE_DARK, 0x22FFFFFF);
		Fonts.centered(g, key, x + w / 2f, y + 2.6f, Theme.lerp(Theme.TEXT_DIM, Theme.WHITE, hover), Face.MONO_BOLD, 0.72f);
		return w;
	}

	// legacy helpers still used by a few widgets

	public static void chevronLeft(GuiGraphics g, float x, float y, int color) {
		PixelIcons.draw(g, "arrowleft", x - 0.5f, y - 0.5f, 9, color);
	}

	public static void chevronRight(GuiGraphics g, float x, float y, int color) {
		PixelIcons.draw(g, "arrow", x - 0.5f, y - 0.5f, 9, color);
	}

	public static void slidersIcon(GuiGraphics g, float x, float y, int color) {
		PixelIcons.draw(g, "gear", x, y, 9, color);
	}

	public static void searchIcon(GuiGraphics g, float x, float y, int color) {
		PixelIcons.draw(g, "magnifier", x, y, 9, color);
	}

	public static void iconTile(GuiGraphics g, String icon, float x, float y, float size, float active, float hover) {
		slot(g, icon, x, y, size, active, hover);
	}

	public static void brackets(GuiGraphics g, float x, float y, float w, float h, float len, int color) {
		R2D.rect(g, x, y, len, 1, color);
		R2D.rect(g, x, y, 1, len, color);
		R2D.rect(g, x + w - len, y, len, 1, color);
		R2D.rect(g, x + w - 1, y, 1, len, color);
		R2D.rect(g, x, y + h - 1, len, 1, color);
		R2D.rect(g, x, y + h - len, 1, len, color);
		R2D.rect(g, x + w - len, y + h - 1, len, 1, color);
		R2D.rect(g, x + w - 1, y + h - len, 1, len, color);
	}
}
