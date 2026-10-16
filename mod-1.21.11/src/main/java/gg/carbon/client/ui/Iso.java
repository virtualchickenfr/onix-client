package gg.carbon.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Pixel-art isometric blocks (2:1 projection). Every cube is generated once per device size and
 * variant: three shaded faces, crisp outlines and an 8x8 texel texture per face — drawn 1:1.
 */
public final class Iso {
	private Iso() {}

	public enum Block { GRASS, STONE, ORE, WOOD, LEAVES }

	private static final Map<Long, Identifier> CACHE = new HashMap<>();

	private static float hash(int x, int y, int seed) {
		int h = x * 374761393 + y * 668265263 + seed * 2147483647;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
	}

	private static Identifier texture(int a, Block block) {
		long key = ((long) a << 8) | block.ordinal();
		Identifier id = CACHE.get(key);
		if (id != null) return id;
		int size = a * 2;
		// one transparent texel of padding so filtered edges fade out instead of clamping
		NativeImage img = new NativeImage(size + 2, size + 2, true);
		img.fillRect(0, 0, size + 2, size + 2, 0);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				float xc = x + 0.5f, yc = y + 0.5f;
				float dx = Math.abs(a - xc) / 2f;
				if (yc < dx || yc > 2 * a - dx) continue;
				boolean top = yc < a - dx;
				boolean left = !top && xc < a;
				float u, v, shade;
				int face;
				if (top) {
					u = ((xc - a) / a + 2 * yc / a) / 2f;
					v = (2 * yc / a - (xc - a) / a) / 2f;
					shade = 1f;
					face = 0;
				} else if (left) {
					u = xc / a;
					v = (yc - a / 2f - xc / 2f) / a;
					shade = 0.74f;
					face = 1;
				} else {
					u = (xc - a) / a;
					v = (yc - a + (xc - a) / 2f) / a;
					shade = 0.55f;
					face = 2;
				}
				int tu = Math.max(0, Math.min(7, (int) (u * 8))), tv = Math.max(0, Math.min(7, (int) (v * 8)));
				float n = hash(tu, tv, face * 31 + block.ordinal() * 7);
				float tex = 1f + (n - 0.5f) * 0.12f;
				switch (block) {
					case GRASS -> {
						if (face == 0) tex *= 1.04f + (hash(tu, tv, 99) > 0.8f ? -0.12f : 0);
						else if (tv < 2 || (tv == 2 && n > 0.5f)) tex *= 1.22f;
					}
					case ORE -> {
						if (hash(tu, tv, face + 50) > 0.84f) tex *= 0.55f;
					}
					case WOOD -> {
						if (face == 0) tex *= (Math.abs(tu - 3.5f) + Math.abs(tv - 3.5f)) % 3 < 1 ? 0.8f : 1f;
						else tex *= tu % 3 == 0 ? 0.8f : 1f;
					}
					case LEAVES -> {
						if (hash(tu, tv, face + 70) > 0.72f) tex *= 0.7f;
					}
					default -> {}
				}
				// outlines: silhouette and the inner edges between faces
				float edgeTop = yc - dx, edgeBottom = 2 * a - dx - yc, inner = Math.abs(yc - (a - dx));
				float vertical = Math.abs(xc - a);
				float outline = 1f;
				if (edgeTop < 1 || edgeBottom < 1 || x == 0 || x == size - 1) outline = 0.55f;
				else if (!top && vertical < 0.6f) outline = 0.82f;
				else if (inner < 0.75f) outline = top ? 1.15f : 0.85f;
				float val = Math.max(0, Math.min(1, shade * tex * outline));
				int g = Math.round(val * 255);
				img.setPixel(x + 1, y + 1, 0xFF000000 | (g << 16) | (g << 8) | g);
			}
		}
		id = Identifier.fromNamespaceAndPath("carbon", "dynamic/iso_" + a + "_" + block.name().toLowerCase());
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "carbon iso", img) {
			{
				// linear filtering: exact on whole pixels, smooth when a cube sits between pixels
				this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			}
		});
		CACHE.put(key, id);
		return id;
	}

	/** Device half-width of a cube whose half-width is {@code half} local units (always even). */
	public static int deviceHalf(GuiGraphics g, float half) {
		int a = Math.max(4, Math.round(half * R2D.scale(g)));
		return a + (a & 1);
	}

	/**
	 * Draws a cube whose top vertex sits at device pixel (dx, dy). Must be called inside
	 * {@link R2D#beginDevice}. {@code tint} multiplies the greyscale texture.
	 */
	public static void cube(GuiGraphics g, Block block, int a, int dx, int dy, int tint) {
		Identifier id = texture(a, block);
		int size = a * 2 + 2;
		g.blit(RenderPipelines.GUI_TEXTURED, id, dx - a - 1, dy - 1, 0, 0, size, size, size, size, size, size, R2D.tint(tint));
	}

	/**
	 * Sub-pixel variant: the cube is offset by the fractional part of (dx, dy), which the linear
	 * sampler turns into smooth motion (a bobbing island glides instead of stepping pixel by pixel).
	 */
	public static void cube(GuiGraphics g, Block block, int a, float dx, float dy, int tint) {
		int ix = (int) Math.floor(dx), iy = (int) Math.floor(dy);
		float fx = dx - ix, fy = dy - iy;
		if (fx < 0.02f && fy < 0.02f) {
			cube(g, block, a, ix, iy, tint);
			return;
		}
		g.pose().pushMatrix();
		g.pose().translate(fx, fy);
		cube(g, block, a, ix, iy, tint);
		g.pose().popMatrix();
	}
}
