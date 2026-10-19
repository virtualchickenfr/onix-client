package dev.onix.compat;

/**
 * Minecraft 1.21.2 to 1.21.5: GUI drawing is batched, so the old "shader colour" around a texture draw becomes
 * a colour argument. {@link #set} remembers the tint, {@link #argb} hands it to the draw call.
 */
public final class Tint {
	private static int argb = -1;

	private Tint() {}

	public static void set(float r, float g, float b, float a) {
		argb = Math.round(Math.max(0, Math.min(1, a)) * 255) << 24 | Math.round(Math.max(0, Math.min(1, r)) * 255) << 16
			| Math.round(Math.max(0, Math.min(1, g)) * 255) << 8 | Math.round(Math.max(0, Math.min(1, b)) * 255);
	}

	public static int argb() {
		return argb;
	}

	/** ABGR ⇄ ARGB (swaps red and blue). */
	public static int swap(int c) {
		return (c & 0xFF00FF00) | (c & 0xFF) << 16 | (c >> 16) & 0xFF;
	}
}
