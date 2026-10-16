package gg.carbon.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Pixel-art helpers: film grain, the stepped horizon strip, blocks and headline lettering. */
public final class Pixel {
	private Pixel() {}

	private static final Identifier GRAIN = Identifier.fromNamespaceAndPath("carbon", "dynamic/grain");
	private static final Identifier DITHER = Identifier.fromNamespaceAndPath("carbon", "dynamic/dither");
	private static boolean ready;

	private static void init() {
		if (ready) return;
		ready = true;
		Random r = new Random(7);
		NativeImage grain = new NativeImage(128, 128, true);
		for (int y = 0; y < 128; y++) {
			for (int x = 0; x < 128; x++) {
				int a = r.nextInt(100) < 45 ? 3 + r.nextInt(10) : 0;
				grain.setPixel(x, y, (a << 24) | 0xFFFFFF);
			}
		}
		Minecraft.getInstance().getTextureManager().register(GRAIN, new DynamicTexture(() -> "carbon grain", grain));

		NativeImage dither = new NativeImage(64, 8, true);
		for (int x = 0; x < 64; x++) {
			int height = 2 + (int) Math.round(3 + 2.5 * Math.sin(x * 0.45) + 1.5 * Math.sin(x * 1.7 + 1) + r.nextInt(2));
			for (int y = 0; y < 8; y++) {
				int a = 0;
				if (8 - y <= height) a = (height - (8 - y)) == 0 ? 120 : 40 + r.nextInt(30);
				dither.setPixel(x, y, (a << 24) | 0xFFFFFF);
			}
		}
		Minecraft.getInstance().getTextureManager().register(DITHER, new DynamicTexture(() -> "carbon dither", dither));
	}

	/** Positions are snapped by the renderer itself now; kept for call sites. */
	public static float snap(float v) {
		return v;
	}

	/** Tiled film grain, one texel per device pixel. */
	public static void grain(GuiGraphics g, float x, float y, float w, float h, float strength) {
		init();
		R2D.Box b = R2D.box(g, x, y, w, h);
		if (b.w() <= 0 || b.h() <= 0) return;
		R2D.beginDevice(g);
		g.blit(RenderPipelines.GUI_TEXTURED, GRAIN, b.x0(), b.y0(), 0, 0, b.w(), b.h(), b.w(), b.h(), 128, 128,
			R2D.c(Theme.alpha(0xFFFFFFFF, strength)));
		R2D.endDevice(g);
	}

	/** Stepped block horizon; every block is {@code block} local units, snapped to whole device pixels. */
	public static void horizon(GuiGraphics g, float x, float y, float w, float block, float alpha, float offset) {
		init();
		R2D.Box b = R2D.box(g, x, y, w, 1);
		int bp = Math.max(1, Math.round(block * b.scale()));
		if (b.w() <= 0) return;
		R2D.beginDevice(g);
		int texels = Math.max(1, b.w() / bp);
		g.blit(RenderPipelines.GUI_TEXTURED, DITHER, b.x0(), b.y0(), offset, 0, texels * bp, bp * 8, texels, 8, 64, 8,
			R2D.c(Theme.alpha(0xFFFFFFFF, alpha)));
		R2D.endDevice(g);
	}

	public static void block(GuiGraphics g, float x, float y, float size, int color) {
		R2D.rect(g, x, y, size, size, color);
	}

	public static float width(String text, float scale) {
		return Fonts.displayWidth(text, 8 * scale);
	}

	/** Pixelify headline with a hard one-step drop shadow. {@code scale} 1 = 8 units em. */
	public static void headline(GuiGraphics g, String text, float x, float y, int color, float scale, boolean shadow) {
		float size = 8 * scale;
		if (shadow) {
			float d = Math.max(1f / R2D.scale(g), size * 0.07f);
			Fonts.display(g, text, x, y + d, Theme.alpha(0xFF26262B, ((color >>> 24) & 0xFF) / 255f), size);
		}
		Fonts.display(g, text, x, y, color, size);
	}

	/** Block lettering with a stepped extrusion below the face. */
	public static void logo(GuiGraphics g, String text, float x, float y, float size, float alpha) {
		float unit = 1f / R2D.scale(g);
		int steps = Math.max(2, Math.round(size * 0.12f / unit));
		float depth = steps * unit;
		// the logo has its own light palette, so a remapping screen must not invert it again
		boolean light = gg.carbon.client.screen.widget.Ui.light || R2D.invert;
		boolean inverted = R2D.invert;
		R2D.invert = false;
		R2D.pushAlpha(alpha);
		for (int i = steps; i >= 1; i--) {
			int c = light ? (i == steps ? 0xFF8A8A92 : 0xFFC2C2C9) : (i == steps ? 0xFF111114 : 0xFF2E2E35);
			Fonts.display(g, text, x, y + depth * i / steps, c, size);
		}
		Fonts.display(g, text, x, y, light ? 0xFF141417 : 0xFFF4F4F6, size);
		R2D.popAlpha();
		R2D.invert = inverted;
	}
}
