package dev.onix.client.module.impl.pack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.onix.client.gui.SoftShapes;
import dev.onix.client.gui.Theme;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Shared pieces of the viral modules: camera punch (FOV), camera shake, easing curves and the big outlined
 * "TikTok caption" text. Camera effects are applied by GameRendererMixin and only move your own view.
 */
public final class ViralFx {
    private ViralFx() {}

    private static final Random RANDOM = new Random();

    // ------------------------------------------------------------------ camera punch
    private static long punchStart = -1;
    private static float punchAmount;
    private static long punchLength = 600;

    /** Zooms the camera in by {@code amount} (0.15 = 15 %) and eases back out. */
    public static void punch(float amount, long millis) {
        punchStart = System.currentTimeMillis();
        punchAmount = amount;
        punchLength = millis;
    }

    /** Multiplier for the field of view, 1 when nothing is going on. */
    public static float fovFactor() {
        if (punchStart < 0) return 1f;
        float t = (System.currentTimeMillis() - punchStart) / (float) punchLength;
        if (t >= 1f) {
            punchStart = -1;
            return 1f;
        }
        float in = 0.12f;
        float k = t < in ? easeOutCubic(t / in) : 1 - easeInOutCubic((t - in) / (1 - in));
        return 1f - punchAmount * k;
    }

    // ------------------------------------------------------------------ camera shake
    private static long shakeStart = -1;
    private static float shakeAmount;
    private static long shakeLength = 350;
    private static float seed;

    public static void shake(float amount, long millis) {
        if (shakeStart >= 0 && amount < shakeAmount * remaining()) return; // keep the stronger one running
        shakeStart = System.currentTimeMillis();
        shakeAmount = amount;
        shakeLength = millis;
        seed = RANDOM.nextFloat() * 100f;
    }

    private static float remaining() {
        if (shakeStart < 0) return 0;
        return Math.max(0, 1 - (System.currentTimeMillis() - shakeStart) / (float) shakeLength);
    }

    /** Called at the start of GameRenderer.bobHurt. */
    public static void applyShake(PoseStack pose) {
        if (shakeStart < 0) return;
        float left = remaining();
        if (left <= 0) {
            shakeStart = -1;
            return;
        }
        float time = (System.currentTimeMillis() - shakeStart) / 1000f;
        float a = shakeAmount * left * left;
        float rx = (float) (Math.sin(time * 47 + seed) + Math.sin(time * 71 + seed * 2) * 0.5) * a;
        float ry = (float) (Math.sin(time * 53 + seed * 3) + Math.sin(time * 89 + seed) * 0.5) * a;
        float rz = (float) Math.sin(time * 61 + seed * 4) * a * 0.6f;
        pose.mulPose(Axis.XP.rotationDegrees(rx));
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.mulPose(Axis.ZP.rotationDegrees(rz));
    }

    // ------------------------------------------------------------------ easing
    public static float clamp01(float t) {
        return t < 0 ? 0 : t > 1 ? 1 : t;
    }

    public static float easeOutCubic(float t) {
        t = clamp01(t);
        float u = 1 - t;
        return 1 - u * u * u;
    }

    public static float easeInOutCubic(float t) {
        t = clamp01(t);
        return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    public static float easeOutBack(float t) {
        t = clamp01(t);
        float c1 = 1.70158f;
        float c3 = c1 + 1;
        float u = t - 1;
        return 1 + c3 * u * u * u + c1 * u * u;
    }

    /** Fade in over the first {@code in} and out over the last {@code out} of t (all 0..1). */
    public static float fade(float t, float in, float out) {
        float a = in > 0 ? clamp01(t / in) : 1;
        float b = out > 0 ? clamp01((1 - t) / out) : 1;
        return Math.min(a, b);
    }

    // ------------------------------------------------------------------ text
    /** Big outlined caption text centered on (x, y) with an optional glow behind it. */
    public static void caption(GuiGraphics ctx, String text, float x, float y, float scale, int color, float alpha, boolean glow) {
        if (alpha <= 0.01f) return;
        Font f = Minecraft.getInstance().font;
        Component c = Theme.display(text);
        int w = f.width(c);
        if (glow) {
            SoftShapes.glow(ctx, x, y, (w * scale) * 0.75f + 14, 10 * scale + 8, Theme.withAlpha(color, Math.round(110 * alpha)));
        }
        int a = Math.round(255 * alpha);
        int outline = Theme.withAlpha(0xFF000000, Math.round(220 * alpha));
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        int tx = -w / 2;
        int ty = -4;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                ctx.drawString(f, c, tx + dx, ty + dy, outline, false);
            }
        }
        ctx.drawString(f, c, tx, ty, Theme.withAlpha(color, a), false);
        ctx.pose().popMatrix();
    }

    /** Small outlined text. */
    public static void small(GuiGraphics ctx, String text, float x, float y, float scale, int color, float alpha) {
        if (alpha <= 0.01f) return;
        Font f = Minecraft.getInstance().font;
        int w = f.width(text);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(scale, scale);
        ctx.drawString(f, text, -w / 2, -4, Theme.withAlpha(color, Math.round(255 * alpha)), true);
        ctx.pose().popMatrix();
    }

    public static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000 | (Math.round(ar + (br - ar) * t) << 16) | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }
}
