package gg.carbon.client.ui;

/** The menu palette: the Onix look (deep navy, one blue accent), the same as the launcher. ARGB. */
public final class Theme {
	private Theme() {}

	public static final int BG = 0xFF07090C;
	public static final int BG_2 = 0xFF0A0D11;
	public static final int STONE = 0xFF0E1216;
	public static final int STONE_2 = 0xFF13181D;
	public static final int STONE_3 = 0xFF192026;
	public static final int STONE_EDGE = 0xFF222A33;
	public static final int STONE_DARK = 0xFF040506;
	public static final int ICE = 0xFFF4F4F5;
	public static final int ICE_DEEP = 0xFFA6A6AE;

	public static final int WINDOW = 0xF20A0D11;
	public static final int SIDEBAR = 0xFF07090C;
	public static final int SURFACE = STONE;
	public static final int SURFACE_2 = STONE_2;
	public static final int SURFACE_3 = STONE_3;
	public static final int STROKE = STONE_EDGE;
	public static final int STROKE_SOFT = 0x1FFFFFFF;
	public static final int TEXT = 0xFFE6E9FF;
	public static final int TEXT_DIM = 0xFFA3A8BD;
	public static final int TEXT_MUTED = 0xFF6F7486;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int BLACK = 0xFF000000;
	public static final int SHADOW = 0x99000000;
	/** The Onix accent (switches, the selected page). */
	public static final int ACCENT = 0xFF2B4BFF;
	public static final int ACCENT_DARK = 0xFF1F37C7;

	public static int alpha(int color, float alpha) {
		int a = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, alpha)));
		return (a << 24) | (color & 0x00FFFFFF);
	}

	public static int withAlpha(int color, int a) {
		return (Math.max(0, Math.min(255, a)) << 24) | (color & 0x00FFFFFF);
	}

	public static int lerp(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
			| (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
	}

	/**
	 * Maps a colour of the dark palette to the light palette. Surfaces (very dark greys) become
	 * near-white while keeping their elevation order; everything brighter — strokes, text, icons,
	 * white overlays — is inverted into dark ink. Saturated colours are left untouched.
	 */
	public static int lightOf(int color) {
		int a = (color >>> 24) & 0xFF;
		int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
		int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		if (max - min > 28) return color;
		float v = (r + g + b) / 765f;
		float nv;
		if (a < 0x80 && v < 0.05f) {
			// translucent black (inner shadows, scrims) stays black but lighter
			return withAlpha(0x000000, Math.round(a * 0.45f));
		}
		if (v <= 0.185f) nv = 0.925f + v * 0.4f;
		else nv = Math.max(0.07f, 0.76f - (v - 0.2f) * 0.86f);
		int n = Math.round(Math.min(1f, nv) * 255);
		// a hint of blue in the light greys keeps them from looking dirty
		int nb = Math.min(255, n + (n > 200 ? 3 : 2));
		return (a << 24) | (n << 16) | (n << 8) | nb;
	}

	public static int gray(int v, int a) {
		return (a << 24) | (v << 16) | (v << 8) | v;
	}
}
