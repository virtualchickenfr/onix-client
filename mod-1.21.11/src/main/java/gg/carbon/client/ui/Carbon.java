package gg.carbon.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Carbon's own visual motif: graphene. A hexagonal brand mark and a honeycomb lattice of
 * atoms and bonds, both rasterised at device resolution so every line is crisp.
 */
public final class Carbon {
	private Carbon() {}

	private record Tex(Identifier id, int w, int h) {}

	private static final Map<Long, Tex> HEX = new HashMap<>();
	private static final Map<Integer, Tex> LATTICE = new HashMap<>();
	private static int counter;

	/** Colour of lattice lines and atoms (white on dark designs, black on light ones). */
	public static int ink = 0xFFFFFFFF;

	// ------------------------------------------------------------------ hexagon mark

	/** Signed distance to a pointy-top regular hexagon with circumradius r. */
	private static float hexSd(float x, float y, float r) {
		float px = Math.abs(x), py = Math.abs(y);
		final float k0 = -0.8660254f, k1 = 0.5f, k2 = 0.57735027f;
		// rotate into flat-top formulation: swap axes
		float qx = py, qy = px;
		float d = 2 * Math.min(k0 * qx + k1 * qy, 0);
		qx -= d * k0;
		qy -= d * k1;
		float ri = r * 0.8660254f;
		qx -= Math.max(-k2 * ri, Math.min(k2 * ri, qx));
		qy -= ri;
		float len = (float) Math.sqrt(qx * qx + qy * qy);
		return len * Math.signum(qy);
	}

	private static Tex hexTexture(int radiusPx, int ringPx) {
		long key = ((long) radiusPx << 32) | ringPx;
		Tex t = HEX.get(key);
		if (t != null) return t;
		int size = radiusPx * 2 + 4;
		NativeImage img = new NativeImage(size, size, true);
		float c = size / 2f;
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float sd = hexSd(x + 0.5f - c, y + 0.5f - c, radiusPx);
				float a = ringPx > 0 ? clamp(0.5f - sd) * clamp(0.5f + sd + ringPx) : clamp(0.5f - sd);
				img.setPixel(x, y, (Math.round(a * 255) << 24) | 0xFFFFFF);
			}
		}
		Identifier id = Identifier.fromNamespaceAndPath("carbon", "dynamic/hex_" + (counter++));
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "carbon hex", img));
		t = new Tex(id, size, size);
		HEX.put(key, t);
		return t;
	}

	/** Filled (ring = 0) or outlined hexagon centred on (cx, cy). */
	public static void hexagon(GuiGraphics g, float cx, float cy, float radius, float ring, int color) {
		int col = R2D.c(color);
		if (((col >>> 24) & 0xFF) == 0) return;
		float s = R2D.scale(g);
		int r = Math.max(2, Math.round(radius * s));
		int ringPx = ring <= 0 ? 0 : Math.max(1, Math.round(ring * s));
		Tex t = hexTexture(r, ringPx);
		int[] p = R2D.point(g, cx, cy);
		R2D.beginDevice(g);
		g.blit(RenderPipelines.GUI_TEXTURED, t.id(), p[0] - t.w() / 2, p[1] - t.h() / 2, 0, 0, t.w(), t.h(), t.w(), t.h(), t.w(), t.h(), col);
		R2D.endDevice(g);
	}

	/** The Carbon mark: a hexagonal shell with a solid core — one carbon atom. */
	public static void mark(GuiGraphics g, float cx, float cy, float size, int color, int core) {
		hexagon(g, cx, cy, size / 2f, size * 0.12f, color);
		hexagon(g, cx, cy, size * 0.2f, 0, core);
	}

	// ------------------------------------------------------------------ graphene lattice

	/** Device-resolution honeycomb tile: atoms + bonds with spacing {@code a} device pixels. */
	private static Tex latticeTexture(int a) {
		Tex t = LATTICE.get(a);
		if (t != null) return t;
		int w = Math.round(1.7320508f * a), h = 3 * a;
		float hw = w / 2f;
		float[][] atoms = {{0, 0}, {hw, a / 2f}, {hw, 1.5f * a}, {0, 2f * a}, {w, 0}, {w, 2f * a}, {0, 3f * a}, {w, 3f * a}};
		int[][] bonds = {{0, 1}, {1, 2}, {2, 3}, {3, 6}, {1, 4}, {2, 5}, {5, 7}};
		NativeImage img = new NativeImage(w, h, true);
		float atomR = Math.max(0.8f, a * 0.09f);
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float px = x + 0.5f, py = y + 0.5f;
				float bond = 0;
				for (int[] b : bonds) {
					if (b[0] == b[1]) continue;
					bond = Math.max(bond, lineCoverage(px, py, atoms[b[0]], atoms[b[1]], 0.5f));
				}
				// wrap-around bonds so the tile repeats seamlessly
				bond = Math.max(bond, lineCoverage(px, py, new float[] {hw, 1.5f * a}, new float[] {w, 2f * a}, 0.5f));
				bond = Math.max(bond, lineCoverage(px, py, new float[] {hw, a / 2f}, new float[] {w, 0}, 0.5f));
				float atom = 0;
				for (float[] at : atoms) {
					float dx = px - at[0], dy = py - at[1];
					atom = Math.max(atom, clamp(atomR + 0.5f - (float) Math.sqrt(dx * dx + dy * dy)));
				}
				int alpha = Math.round(Math.max(bond * 70, atom * 255));
				img.setPixel(x, y, (alpha << 24) | 0xFFFFFF);
			}
		}
		Identifier id = Identifier.fromNamespaceAndPath("carbon", "dynamic/lattice_" + a);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "carbon lattice", img));
		t = new Tex(id, w, h);
		LATTICE.put(a, t);
		return t;
	}

	private static float lineCoverage(float px, float py, float[] a, float[] b, float halfWidth) {
		float vx = b[0] - a[0], vy = b[1] - a[1];
		float len2 = vx * vx + vy * vy;
		if (len2 == 0) return 0;
		float t = Math.max(0, Math.min(1, ((px - a[0]) * vx + (py - a[1]) * vy) / len2));
		float dx = px - (a[0] + vx * t), dy = py - (a[1] + vy * t);
		return clamp(halfWidth + 0.5f - (float) Math.sqrt(dx * dx + dy * dy));
	}

	/**
	 * Draws the lattice over a region. The pattern is anchored to the screen so it never swims;
	 * around ({@code lx}, {@code ly}) it brightens in stepped rings.
	 */
	public static void lattice(GuiGraphics g, float x, float y, float w, float h, float spacing, float alpha,
		float lx, float ly, float lightRadius, float lightAlpha) {
		float s = R2D.scale(g);
		int a = Math.max(4, Math.round(spacing * s));
		Tex t = latticeTexture(a);
		R2D.Box b = R2D.box(g, x, y, w, h);
		R2D.beginDevice(g);
		tile(g, t, b.x0(), b.y0(), b.x1(), b.y1(), alpha);
		if (lightAlpha > 0 && lightRadius > 0) {
			int[] c = R2D.point(g, lx, ly);
			int rad = Math.round(lightRadius * s);
			// stepped pixel disc: each ring is built from horizontal spans of a circle
			for (int i = 4; i >= 1; i--) {
				int r = rad * i / 4;
				int strips = 10;
				int step = Math.max(1, (2 * r) / strips);
				for (int sy = -r; sy < r; sy += step) {
					float mid = sy + step / 2f;
					int half = (int) Math.round(Math.sqrt(Math.max(0, (double) r * r - mid * mid)));
					int x0 = Math.max(b.x0(), c[0] - half), x1 = Math.min(b.x1(), c[0] + half);
					int y0 = Math.max(b.y0(), c[1] + sy), y1 = Math.min(b.y1(), c[1] + sy + step);
					if (x1 > x0 && y1 > y0) tile(g, t, x0, y0, x1, y1, lightAlpha * 0.22f);
				}
			}
		}
		R2D.endDevice(g);
	}

	private static void tile(GuiGraphics g, Tex t, int x0, int y0, int x1, int y1, float alpha) {
		int col = R2D.c(Theme.alpha(ink, alpha));
		if (((col >>> 24) & 0xFF) == 0) return;
		g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x0, y0, Math.floorMod(x0, t.w()), Math.floorMod(y0, t.h()), x1 - x0, y1 - y0,
			x1 - x0, y1 - y0, t.w(), t.h(), col);
	}

	private static float clamp(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}
}
