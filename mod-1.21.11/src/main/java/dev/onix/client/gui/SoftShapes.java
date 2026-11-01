package dev.onix.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Soft round light and a vignette as small textures, drawn with one blit each. Building them from hundreds of
 * filled rectangles every frame made the title screen lag.
 */
public final class SoftShapes {
    private SoftShapes() {}

    private static final int SIZE = 256;
    private static Identifier glow;
    private static Identifier vignette;

    private static Identifier make(String name, boolean edge) {
        NativeImage image = new NativeImage(SIZE, SIZE, true);
        float c = (SIZE - 1) / 2f;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                float dx = (x - c) / c;
                float dy = (y - c) / c;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float a;
                if (edge) {
                    // dark towards the corners, clear in the middle
                    float t = Math.max(0f, Math.min(1f, (d - 0.45f) / 0.95f));
                    a = t * t * (3 - 2 * t);
                } else {
                    float t = Math.max(0f, 1f - d);
                    a = t * t * (3 - 2 * t);
                }
                int alpha = Math.round(a * 255);
                image.setPixelABGR(x, y, (alpha << 24) | 0xFFFFFF);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("onix", "soft/" + name);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "onix soft " + name, image));
        return id;
    }

    private static void blit(GuiGraphics ctx, Identifier id, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, SIZE, SIZE, SIZE, SIZE, argb);
    }

    /** A round soft light centred on (cx, cy); the colour's alpha is the brightness in the middle. */
    public static void glow(GuiGraphics ctx, float cx, float cy, float rx, float ry, int argb) {
        if (glow == null) glow = make("glow", false);
        blit(ctx, glow, Math.round(cx - rx), Math.round(cy - ry), Math.round(rx * 2), Math.round(ry * 2), argb);
    }

    /** Darkens the edges of the area. */
    public static void vignette(GuiGraphics ctx, int x, int y, int w, int h, int argb) {
        if (vignette == null) vignette = make("vignette", true);
        blit(ctx, vignette, x, y, w, h, argb);
    }
}
