package gg.carbon.client.screen;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.ui.Carbon;
import gg.carbon.client.ui.Pixel;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Carbon's skin for vanilla menu screens (singleplayer, multiplayer, options, …) outside a world:
 * a calm backdrop in dark or light, flat list panels, rounded selections, Carbon text fields,
 * scroll bars and tooltips. In light mode vanilla's light-on-dark colours are remapped to ink.
 */
public final class MenuSkin {
	private MenuSkin() {}

	private static final Map<Integer, Integer> TEXT_CACHE = new HashMap<>();

	/** True while a vanilla menu screen is shown with Carbon's backdrop. */
	public static boolean active() {
		if (CarbonClient.modules() == null || !CarbonClient.modules().client.menuBackgrounds.on()) return false;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level != null) return false;
		return skins(mc.screen);
	}

	public static boolean skins(Screen screen) {
		if (screen == null) return false;
		if (screen instanceof TitleScreen || dev.onix.client.gui.LoadingScreens.replacesBackground(screen)) return false;
		// Onix and Carbon screens draw their own surfaces
		String pkg = screen.getClass().getName();
		return !(pkg.startsWith("dev.onix.") || pkg.startsWith("gg.carbon."));
	}

	/** Active and in light mode. */
	public static boolean light() {
		return active() && CarbonClient.modules().client.menuLight.on();
	}

	// ------------------------------------------------------------------ backdrop

	/** The backdrop: a quiet gradient with the graphene lattice that wakes up around the cursor. */
	public static void backdrop(GuiGraphics g, int w, int h, float mx, float my) {
		boolean light = CarbonClient.modules().client.menuLight.on();
		if (light) {
			// a soft grey, so white buttons and fields stand out instead of melting into the page
			R2D.gradientV(g, 0, 0, w, h, 0xFFDEDEE4, 0xFFCDCDD5);
			float r = Math.max(w, h) * 0.35f;
			R2D.shadow(g, w / 2f - r * 0.3f, -r * 0.45f, r * 0.6f, r * 0.6f, r * 0.3f, r * 0.9f, 0x5CFFFFFF);
		} else {
			R2D.gradientV(g, 0, 0, w, h, 0xFF111114, 0xFF070708);
			float r = Math.max(w, h) * 0.35f;
			R2D.shadow(g, w / 2f - r * 0.3f, -r * 0.45f, r * 0.6f, r * 0.6f, r * 0.3f, r * 0.9f, 0x0EFFFFFF);
		}
		Carbon.ink = light ? 0xFF000000 : 0xFFFFFFFF;
		Carbon.lattice(g, 0, 0, w, h, 14, light ? 0.055f : 0.04f, mx, my, mx < 0 ? 0 : 120, 0.45f);
		Carbon.ink = 0xFFFFFFFF;
		// the lattice fades out towards the bottom, where lists and buttons live
		R2D.gradientV(g, 0, h * 0.35f, w, h * 0.65f, light ? 0x00D0D0D8 : 0x00080809, light ? 0xE6D0D0D8 : 0xE0080809);
		if (!light) Pixel.grain(g, 0, 0, w, h, 0.55f);
	}

	// ------------------------------------------------------------------ replacements

	/** Panel behind a scrolling list. */
	public static void listPanel(GuiGraphics g, float x, float y, float w, float h) {
		R2D.rect(g, x, y, w, h, light() ? 0x40FFFFFF : 0x36000000);
	}

	/** Thin rule replacing the header/footer separator textures (2 units tall in vanilla). */
	public static void separator(GuiGraphics g, float x, float y, float w, boolean header) {
		float ly = header ? y + 1 : y;
		R2D.rect(g, x, ly, w, 1, light() ? 0x26000000 : 0x24FFFFFF);
	}

	public static void selection(GuiGraphics g, float x, float y, float w, float h, boolean focused) {
		boolean light = light();
		R2D.rrect(g, x, y, w, h, 4, light ? 0x80FFFFFF : 0x10FFFFFF);
		R2D.outline(g, x, y, w, h, 4, 1f, light ? (focused ? 0x8C000000 : 0x33000000) : (focused ? 0x99FFFFFF : 0x3DFFFFFF));
	}

	public static void textField(GuiGraphics g, float x, float y, float w, float h, boolean focused) {
		boolean light = light();
		R2D.rrect(g, x, y, w, h, 4, light ? 0xFFFFFFFF : 0xFF0C0C0E);
		R2D.rect(g, x + 3, y + 1, w - 6, 1, light ? 0x0F000000 : 0x60000000);
		int edge = light ? (focused ? 0xFF3A3A42 : 0xFFB4B4BC) : (focused ? 0xFFB8B8C0 : 0xFF34343A);
		R2D.outline(g, x, y, w, h, 4, focused ? 1f : 0.75f, edge);
	}

	public static void scrollTrack(GuiGraphics g, float x, float y, float w, float h) {
		R2D.rrect(g, x + w / 2f - 1.5f, y + 2, 3, h - 4, 1.5f, light() ? 0x12000000 : 0x14FFFFFF);
	}

	public static void scrollThumb(GuiGraphics g, float x, float y, float w, float h) {
		R2D.rrect(g, x + w / 2f - 1.5f, y + 2, 3, h - 4, 1.5f, light() ? 0x66000000 : 0x6EFFFFFF);
	}

	/** Tooltip box; vanilla passes the sprite rectangle including its 9 unit transparent margin. */
	public static void tooltip(GuiGraphics g, float x, float y, float w, float h) {
		boolean light = light();
		float m = 9;
		x += m;
		y += m;
		w -= m * 2;
		h -= m * 2;
		R2D.shadow(g, x, y + 3, w, h, 5, 10, light ? 0x30000000 : 0xA0000000);
		R2D.rrect(g, x, y, w, h, 5, light ? 0xFCFFFFFF : 0xF5101013);
		R2D.outline(g, x, y, w, h, 5, 0.75f, light ? 0x24000000 : 0x2EFFFFFF);
	}

	public static void tab(GuiGraphics g, float x, float y, float w, float h, boolean selected, boolean hovered) {
		boolean light = light();
		if (selected) {
			R2D.rrect(g, x + 2, y + 2, w - 4, h - 2, 4, light ? 0xB3FFFFFF : 0x1AFFFFFF);
		} else if (hovered) {
			R2D.rrect(g, x + 2, y + 4, w - 4, h - 6, 4, light ? 0x0D000000 : 0x0DFFFFFF);
		}
	}

	// ------------------------------------------------------------------ light text

	/** Remaps a vanilla text colour for light mode (light greys become ink, saturated colours stay). */
	public static int textColor(int color) {
		Integer cached = TEXT_CACHE.get(color);
		if (cached != null) return cached;
		int a = (color >>> 24) & 0xFF;
		int r = (color >> 16) & 0xFF, gr = (color >> 8) & 0xFF, b = color & 0xFF;
		int max = Math.max(r, Math.max(gr, b)), min = Math.min(r, Math.min(gr, b));
		int out = color;
		float v = (r + gr + b) / 765f;
		if (max - min <= 28 && v > 0.28f) {
			float nv = Math.max(0.08f, 0.86f - v * 0.78f);
			int n = Math.round(nv * 255);
			out = (a << 24) | (n << 16) | (n << 8) | Math.min(255, n + 3);
		} else if (max - min > 28 && v > 0.55f) {
			// bright accents (yellow, aqua, …) would vanish on white: deepen them
			out = (a << 24) | ((r * 6 / 10) << 16) | ((gr * 6 / 10) << 8) | (b * 6 / 10);
		}
		TEXT_CACHE.put(color, out);
		return out;
	}

	/** Remaps vanilla fills (selection boxes, cursors, underlines) for light mode. */
	public static int fillColor(int color) {
		int r = (color >> 16) & 0xFF, gr = (color >> 8) & 0xFF, b = color & 0xFF;
		int max = Math.max(r, Math.max(gr, b)), min = Math.min(r, Math.min(gr, b));
		if (max - min > 28) return color;
		return Theme.lightOf(color);
	}
}
