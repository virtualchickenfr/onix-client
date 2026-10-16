package gg.carbon.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

/**
 * Carbon's pixel-exact 2D renderer.
 *
 * <p>Every primitive is resolved through the full pose matrix into physical screen pixels,
 * rounded, and drawn in an identity "device" space. Rounded shapes are nine-slices of
 * anti-aliased textures generated for the exact physical radius. Result: nothing lands on half
 * pixels, nothing is resampled, edges stay crisp at every GUI scale and during animations.</p>
 */
public final class R2D {
	private R2D() {}

	private static final Map<Long, Slice> CACHE = new HashMap<>();
	private static final Deque<Float> ALPHA = new ArrayDeque<>();
	private static float alpha = 1f;
	private static GuiGraphics current;
	private static final Identifier RAMP = Identifier.fromNamespaceAndPath("carbon", "dynamic/ramp");
	private static boolean rampReady;

	private record Slice(Identifier id, int size, int corner) {}

	/** Physical rectangle in device pixels. */
	public record Box(int x0, int y0, int x1, int y1, float scale) {
		public int w() {
			return x1 - x0;
		}

		public int h() {
			return y1 - y0;
		}
	}

	// ---------------------------------------------------------------- alpha stack

	public static void pushAlpha(float a) {
		ALPHA.push(alpha);
		alpha *= Math.max(0, Math.min(1, a));
	}

	public static void popAlpha() {
		alpha = ALPHA.isEmpty() ? 1f : ALPHA.pop();
	}

	public static float alpha() {
		return alpha;
	}

	/**
	 * Light mode for screens that are designed dark (mod menu, HUD editor, picker chrome): every
	 * grey that passes through {@link #c} is mapped to its light-palette counterpart.
	 */
	public static boolean invert;

	public static int c(int color) {
		if (invert) color = Theme.lightOf(color);
		if (alpha >= 1f) return color;
		return Theme.alpha(color, alpha);
	}

	/** Alpha-only variant for texture tints (skins, block art) that must never be remapped. */
	public static int tint(int color) {
		if (alpha >= 1f) return color;
		return Theme.alpha(color, alpha);
	}

	private static int shadowColor(int color) {
		if (!invert) return c(color);
		// shadows stay dark in light mode, just softer; glows become faint shade
		int a = (color >>> 24) & 0xFF;
		boolean dark = ((color >> 16) & 0xFF) < 128;
		return tint(Theme.withAlpha(0x1A1A22, Math.round(a * (dark ? 0.42f : 0.35f))));
	}

	// ---------------------------------------------------------------- device space

	public static float guiScale() {
		return Minecraft.getInstance().getWindow().getGuiScale();
	}

	/** Uniform scale of a pose matrix. */
	public static float poseScale(Matrix3x2f m) {
		return (float) Math.sqrt(m.m00 * m.m00 + m.m01 * m.m01);
	}

	/** Physical pixels per local unit for the given graphics' current pose. */
	public static float scale(GuiGraphics g) {
		current = g;
		return guiScale() * poseScale(g.pose());
	}

	/** Physical pixels per local unit for the most recently used graphics context. */
	public static float scale() {
		return current == null ? guiScale() : guiScale() * poseScale(current.pose());
	}

	/** Kept for API compatibility; scaling is now read from the pose matrix. */
	public static void setUiScale(float scale) {}

	public static float uiScale() {
		return 1f;
	}

	/** Resolves a local rectangle to rounded device pixels. */
	public static Box box(GuiGraphics g, float x, float y, float w, float h) {
		current = g;
		Matrix3x2f m = g.pose();
		float gs = guiScale();
		float ax = (m.m00 * x + m.m10 * y + m.m20) * gs, ay = (m.m01 * x + m.m11 * y + m.m21) * gs;
		float bx = (m.m00 * (x + w) + m.m10 * (y + h) + m.m20) * gs, by = (m.m01 * (x + w) + m.m11 * (y + h) + m.m21) * gs;
		int x0 = Math.round(Math.min(ax, bx)), x1 = Math.round(Math.max(ax, bx));
		int y0 = Math.round(Math.min(ay, by)), y1 = Math.round(Math.max(ay, by));
		return new Box(x0, y0, x1, y1, gs * poseScale(m));
	}

	/** Device pixel (rounded) for a local point. */
	public static int[] point(GuiGraphics g, float x, float y) {
		current = g;
		Matrix3x2f m = g.pose();
		float gs = guiScale();
		return new int[] {Math.round((m.m00 * x + m.m10 * y + m.m20) * gs), Math.round((m.m01 * x + m.m11 * y + m.m21) * gs)};
	}

	/** Depth of {@link #beginDevice} nesting: non-zero while Carbon itself is drawing. */
	public static int device;

	/** Switches the pose to device pixels; must be paired with {@link #endDevice}. */
	public static void beginDevice(GuiGraphics g) {
		device++;
		Matrix3x2fStack p = g.pose();
		p.pushMatrix();
		p.identity();
		float inv = 1f / guiScale();
		p.scale(inv, inv);
	}

	public static void endDevice(GuiGraphics g) {
		device = Math.max(0, device - 1);
		g.pose().popMatrix();
	}

	private static boolean invisible(int color) {
		return ((tint(color) >>> 24) & 0xFF) == 0;
	}

	// ---------------------------------------------------------------- primitives

	public static void rect(GuiGraphics g, float x, float y, float w, float h, int color) {
		if (w <= 0 || h <= 0 || invisible(color)) return;
		Box b = box(g, x, y, w, h);
		if (b.w() <= 0 || b.h() <= 0) {
			// keep hairlines visible: never collapse below one device pixel
			if (w > 0 && h > 0) b = new Box(b.x0(), b.y0(), b.x0() + Math.max(1, b.w()), b.y0() + Math.max(1, b.h()), b.scale());
			else return;
		}
		beginDevice(g);
		g.fill(b.x0(), b.y0(), b.x1(), b.y1(), c(color));
		endDevice(g);
	}

	/**
	 * Rectangle given by its edges. Each edge is rounded on its own, so neighbours that share an
	 * edge coordinate always meet exactly — no gaps, no one-pixel overlaps.
	 */
	public static void rectEdges(GuiGraphics g, float x0, float y0, float x1, float y1, int color) {
		if (x1 <= x0 || y1 <= y0 || invisible(color)) return;
		int[] a = point(g, x0, y0), b = point(g, x1, y1);
		int ax = Math.min(a[0], b[0]), bx = Math.max(a[0], b[0]), ay = Math.min(a[1], b[1]), by = Math.max(a[1], b[1]);
		if (bx <= ax || by <= ay) return;
		beginDevice(g);
		g.fill(ax, ay, bx, by, c(color));
		endDevice(g);
	}

	public static void gradientV(GuiGraphics g, float x, float y, float w, float h, int top, int bottom) {
		if (w <= 0 || h <= 0) return;
		Box b = box(g, x, y, w, h);
		if (b.w() <= 0 || b.h() <= 0) return;
		beginDevice(g);
		g.fillGradient(b.x0(), b.y0(), b.x1(), b.y1(), c(top), c(bottom));
		endDevice(g);
	}

	private static void ramp() {
		if (rampReady) return;
		rampReady = true;
		NativeImage img = new NativeImage(256, 1, true);
		for (int i = 0; i < 256; i++) img.setPixel(i, 0, (i << 24) | 0xFFFFFF);
		Minecraft.getInstance().getTextureManager().register(RAMP, new DynamicTexture(() -> "carbon ramp", img));
	}

	/** Horizontal gradient, exact for the common "fade to transparent" and "opaque to anything" cases. */
	public static void gradientH(GuiGraphics g, float x, float y, float w, float h, int left, int right) {
		if (w <= 0 || h <= 0) return;
		Box b = box(g, x, y, w, h);
		if (b.w() <= 0 || b.h() <= 0) return;
		ramp();
		int la = (left >>> 24) & 0xFF, ra = (right >>> 24) & 0xFF;
		beginDevice(g);
		if (la == 0) {
			rampBlit(g, b, right | 0xFF000000, ra, false);
		} else if (ra == 0) {
			rampBlit(g, b, left | 0xFF000000, la, true);
		} else {
			g.fill(b.x0(), b.y0(), b.x1(), b.y1(), c(right));
			rampBlit(g, b, left | 0xFF000000, la, true);
		}
		endDevice(g);
	}

	private static void rampBlit(GuiGraphics g, Box b, int rgb, int maxAlpha, boolean descending) {
		int col = c(Theme.withAlpha(rgb, maxAlpha));
		g.blit(RenderPipelines.GUI_TEXTURED, RAMP, b.x0(), b.y0(), descending ? 256 : 0, 0, b.w(), b.h(), descending ? -256 : 256, 1, 256, 1, col);
	}

	private static final Identifier SHEEN = Identifier.fromNamespaceAndPath("carbon", "dynamic/sheen");
	private static boolean sheenReady;

	/**
	 * A soft vertical light band at {@code bandX..bandX+bandW}, clipped analytically to
	 * {@code clipX0..clipX1} (no scissor, so it never spills past rounded corners).
	 */
	public static void sheen(GuiGraphics g, float clipX0, float clipX1, float y, float h, float bandX, float bandW, int color) {
		if (!sheenReady) {
			sheenReady = true;
			NativeImage img = new NativeImage(256, 1, true);
			for (int i = 0; i < 256; i++) {
				double t = (i + 0.5) / 256.0 * 2 - 1;
				int a = (int) Math.round(255 * Math.exp(-t * t * 4.5));
				img.setPixel(i, 0, (a << 24) | 0xFFFFFF);
			}
			Minecraft.getInstance().getTextureManager().register(SHEEN, new DynamicTexture(() -> "carbon sheen", img));
		}
		float x0 = Math.max(bandX, clipX0), x1 = Math.min(bandX + bandW, clipX1);
		if (x1 <= x0 || h <= 0 || invisible(color)) return;
		Box b = box(g, x0, y, x1 - x0, h);
		if (b.w() <= 0 || b.h() <= 0) return;
		float u0 = (x0 - bandX) / bandW * 256f, u1 = (x1 - bandX) / bandW * 256f;
		beginDevice(g);
		g.blit(RenderPipelines.GUI_TEXTURED, SHEEN, b.x0(), b.y0(), u0, 0, b.w(), b.h(), Math.max(1, Math.round(u1 - u0)), 1, 256, 1, c(color));
		endDevice(g);
	}

	public static void rrect(GuiGraphics g, float x, float y, float w, float h, float r, int color) {
		shape(g, x, y, w, h, r, 0, 0, color);
	}

	public static void outline(GuiGraphics g, float x, float y, float w, float h, float r, float thickness, int color) {
		float s = scale(g);
		shape(g, x, y, w, h, r, 0, Math.max(1, Math.round(thickness * s)), color);
	}

	/** Soft drop shadow; {@code blur} (local units) grows the shadow outwards. */
	public static void shadow(GuiGraphics g, float x, float y, float w, float h, float r, float blur, int color) {
		float s = scale(g);
		int b = Math.max(1, Math.round(blur * s));
		float bl = b / s;
		shape(g, x - bl, y - bl, w + bl * 2, h + bl * 2, r, b, 0, color);
	}

	public static void circle(GuiGraphics g, float cx, float cy, float radius, int color) {
		rrect(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
	}

	private static final Map<Integer, Identifier> DISCS = new HashMap<>();

	/** A small anti-aliased disc drawn with a single textured quad (cheap enough for thousands). */
	public static void disc(GuiGraphics g, float cx, float cy, float radius, int color) {
		int col = c(color);
		if (((col >>> 24) & 0xFF) == 0) return;
		float s = scale(g);
		int dr = Math.max(1, Math.round(radius * s * 2));
		Identifier id = DISCS.get(dr);
		if (id == null) {
			NativeImage img = new NativeImage(dr + 2, dr + 2, true);
			float c = (dr + 2) / 2f, r = dr / 2f;
			for (int y = 0; y < dr + 2; y++) {
				for (int x = 0; x < dr + 2; x++) {
					float dx = x + 0.5f - c, dy = y + 0.5f - c;
					float a = clamp(r + 0.5f - (float) Math.sqrt(dx * dx + dy * dy));
					img.setPixel(x, y, (Math.round(a * 255) << 24) | 0xFFFFFF);
				}
			}
			id = Identifier.fromNamespaceAndPath("carbon", "dynamic/disc_" + dr);
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "carbon disc", img));
			DISCS.put(dr, id);
		}
		int size = dr + 2;
		int[] p = point(g, cx, cy);
		beginDevice(g);
		g.blit(RenderPipelines.GUI_TEXTURED, id, p[0] - size / 2, p[1] - size / 2, 0, 0, size, size, size, size, size, size, col);
		endDevice(g);
	}

	public static void circleOutline(GuiGraphics g, float cx, float cy, float radius, float thickness, int color) {
		outline(g, cx - radius, cy - radius, radius * 2, radius * 2, radius, thickness, color);
	}

	// ---------------------------------------------------------------- nine slice core

	private static void shape(GuiGraphics g, float x, float y, float w, float h, float r, int blurPx, int ringPx, int color) {
		if (w <= 0 || h <= 0 || invisible(color)) return;
		Box b = box(g, x, y, w, h);
		int W = b.w(), H = b.h();
		if (W <= 0 || H <= 0) return;
		int maxCorner = Math.min(W, H) / 2;
		int R = Math.max(0, Math.round(r * b.scale()));
		if (R + blurPx > maxCorner) {
			R = Math.max(0, maxCorner - blurPx);
			if (R + blurPx > maxCorner) blurPx = maxCorner - R;
		}
		int col = blurPx > 0 ? shadowColor(color) : c(color);
		beginDevice(g);
		if (R == 0 && blurPx == 0 && ringPx == 0) {
			g.fill(b.x0(), b.y0(), b.x1(), b.y1(), col);
		} else {
			Slice slice = slice(R, blurPx, ringPx);
			nine(g, slice, b.x0(), b.y0(), W, H, col, ringPx == 0 && blurPx == 0, ringPx);
		}
		endDevice(g);
	}

	private static void nine(GuiGraphics g, Slice t, int X, int Y, int W, int H, int color, boolean solidCenter, int ring) {
		int C = t.corner(), N = t.size(), mid = C + 1, far = N - C;
		int iw = W - 2 * C, ih = H - 2 * C;
		Identifier id = t.id();
		if (C > 0) {
			blit(g, id, X, Y, 0, 0, C, C, C, C, N, color);
			blit(g, id, X + W - C, Y, far, 0, C, C, C, C, N, color);
			blit(g, id, X, Y + H - C, 0, far, C, C, C, C, N, color);
			blit(g, id, X + W - C, Y + H - C, far, far, C, C, C, C, N, color);
		}
		if (iw > 0 && C > 0) {
			blit(g, id, X + C, Y, mid, 0, iw, C, 1, C, N, color);
			blit(g, id, X + C, Y + H - C, mid, far, iw, C, 1, C, N, color);
		}
		if (ih > 0 && C > 0) {
			blit(g, id, X, Y + C, 0, mid, C, ih, C, 1, N, color);
			blit(g, id, X + W - C, Y + C, far, mid, C, ih, C, 1, N, color);
		}
		if (iw > 0 && ih > 0) {
			if (solidCenter) g.fill(X + C, Y + C, X + C + iw, Y + C + ih, color);
			else if (ring == 0) blit(g, id, X + C, Y + C, mid, mid, iw, ih, 1, 1, N, color);
		}
		if (ring > 0 && C == 0) {
			g.fill(X, Y, X + W, Y + ring, color);
			g.fill(X, Y + H - ring, X + W, Y + H, color);
			g.fill(X, Y + ring, X + ring, Y + H - ring, color);
			g.fill(X + W - ring, Y + ring, X + W, Y + H - ring, color);
		}
	}

	private static void blit(GuiGraphics g, Identifier id, int x, int y, int u, int v, int w, int h, int sw, int sh, int n, int color) {
		g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, sw, sh, n, n, color);
	}

	private static Slice slice(int r, int blur, int ring) {
		long key = ((long) r << 40) | ((long) blur << 20) | ring;
		Slice cached = CACHE.get(key);
		if (cached != null) return cached;

		int corner = r + blur;
		int n = corner * 2 + 3;
		NativeImage img = new NativeImage(n, n, true);
		float c = n / 2f;
		float half = (n - 2f * blur) / 2f;
		for (int py = 0; py < n; py++) {
			for (int px = 0; px < n; px++) {
				float qx = Math.abs(px + 0.5f - c) - (half - r);
				float qy = Math.abs(py + 0.5f - c) - (half - r);
				float ox = Math.max(qx, 0), oy = Math.max(qy, 0);
				float sd = (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0) - r;
				float a;
				if (blur > 0) {
					float t = clamp((sd + blur * 0.35f) / (blur * 1.35f));
					a = 1 - t;
					a = a * a * (3 - 2 * a);
					a *= a;
				} else if (ring > 0) {
					a = clamp(0.5f - sd) * clamp(0.5f + sd + ring);
				} else {
					a = clamp(0.5f - sd);
				}
				int ai = Math.round(a * 255);
				img.setPixel(px, py, (ai << 24) | 0xFFFFFF);
			}
		}
		Identifier id = Identifier.fromNamespaceAndPath("carbon", "dynamic/shape_" + r + "_" + blur + "_" + ring);
		DynamicTexture texture = new DynamicTexture(() -> "carbon shape " + key, img);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		Slice slice = new Slice(id, n, corner);
		CACHE.put(key, slice);
		return slice;
	}

	private static float clamp(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}

	// ---------------------------------------------------------------- transforms

	public static void push(GuiGraphics g) {
		g.pose().pushMatrix();
	}

	public static void pop(GuiGraphics g) {
		g.pose().popMatrix();
	}

	public static void scaleAt(GuiGraphics g, float px, float py, float scale) {
		Matrix3x2fStack p = g.pose();
		p.translate(px, py);
		p.scale(scale, scale);
		p.translate(-px, -py);
	}

	public static void translate(GuiGraphics g, float x, float y) {
		g.pose().translate(x, y);
	}

	public static boolean hover(double mx, double my, float x, float y, float w, float h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
