package dev.onix.client.gui;

import dev.onix.client.mixin.PlayerSkinWidgetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.player.PlayerModel;

/**
 * The world loading transition: your own Minecraft character falls through an evening sky, splashes into water (a
 * flash, droplets flying up, rings) and sinks: the surface moves up out of view, light dances under water, bubbles
 * rise while the figure drifts down and turns slowly. Clean: one line of text and a thin progress line.
 */
public final class FallTransition {
    private FallTransition() {}

    private static final float IMPACT = 1.15f;   // seconds until the figure hits the water
    private static final float DIVE = 1.4f;      // seconds for the view to follow it under

    private static PlayerSkinWidget figure;
    private static long start;
    private static Object owner;

    private static float clamp(float x) {
        return Math.max(0f, Math.min(1f, x));
    }

    private static float smooth(float x) {
        x = clamp(x);
        return x * x * (3f - 2f * x);
    }

    private static int a(int rgb, float alpha) {
        return Math.round(clamp(alpha) * 255) << 24 | (rgb & 0xFFFFFF);
    }

    /** Draws the transition for {@code screen} (the loading screens of one join share one fall). */
    public static void render(Object screen, GuiGraphics ctx, String title, String detail, float progress) {
        long now = System.currentTimeMillis();
        if (owner != screen) {
            if (owner == null || now - start > 20_000) start = now;
            owner = screen;
        }
        float t = (now - start) / 1000f;
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();

        float dive = smooth((t - IMPACT) / DIVE);         // 0 above the water, 1 fully under it
        float rest = h * 0.62f;                           // where the surface is while falling
        int surface = Math.round(rest - dive * (rest + h * 0.12f));

        // above the water: an evening sky over a calm sea
        if (surface > 0) {
            ctx.fillGradient(0, 0, w, surface, 0xFF1B2440, 0xFF6B5A8E);
            SoftShapes.glow(ctx, w * 0.5f, surface, w * 0.45f, h * 0.18f, 0x55FFC9A0);
        }
        // the water: lighter near the surface, deep below; deeper the longer you sink
        float deep = smooth((t - IMPACT - DIVE) / 5f);
        int top = Theme.lerpColor(0xFF1E5B8C, 0xFF0F3150, deep);
        int bottom = Theme.lerpColor(0xFF0A2340, 0xFF030B16, deep);
        ctx.fillGradient(0, Math.max(0, surface), w, h, top, bottom);
        if (surface > 0 && surface < h) {
            ctx.fill(0, surface, w, surface + 1, 0x99DDF0FF);
            ctx.fill(0, surface + 1, w, surface + 3, 0x33DDF0FF);
        }

        // falling: wind streaks rushing up
        float wind = 1f - clamp((t - IMPACT + 0.1f) / 0.3f);
        for (int i = 0; i < 24 && wind > 0; i++) {
            float seed = i * 97.31f;
            float x = (seed * 13.7f) % w;
            float len = 22 + (seed % 46);
            float y = h - ((t * (420 + (seed % 260)) + seed * 7) % (h + len));
            if (y > surface) continue;
            ctx.fill(Math.round(x), Math.round(y), Math.round(x) + 1, Math.round(y + len), a(0xFFFFFF, (0.07f + (i % 3) * 0.03f) * wind));
        }

        // under water: wavy light bands from the surface and rising bubbles
        if (dive > 0) {
            for (int i = 0; i < 6; i++) {
                float x = w * (0.1f + i * 0.17f) + (float) Math.sin(t * 0.7f + i * 1.3f) * 18;
                float bw = w * 0.04f + (float) Math.sin(t * 1.1f + i) * w * 0.012f;
                ctx.fillGradient(Math.round(x), Math.max(0, surface), Math.round(x + bw), Math.round(h * 0.85f),
                        a(0xBFE6FF, 0.09f * dive * (1f - deep * 0.6f)), 0x00BFE6FF);
            }
        }

        // the character
        int fh = Math.round(Math.min(h * 0.40f, 180));
        int fw = Math.round(fh * 0.55f);
        float fallT = clamp(t / IMPACT);
        float airY = -fh * 0.6f + (rest - fh * 0.15f + fh * 0.6f) * fallT * fallT;   // accelerating
        float sinkY = rest - fh * 0.15f + (h * 0.48f - (rest - fh * 0.15f)) * dive + (t - IMPACT) * 2.5f * dive;
        float cy = t < IMPACT ? airY : Math.min(sinkY, h * 0.58f);
        float cx = w / 2f + (float) Math.sin(t * 0.7f) * 9 * dive;
        if (dive > 0) SoftShapes.glow(ctx, cx, cy, fw * 1.4f, fh * 0.7f, a(0x7FC4FF, 0.14f * dive));
        drawFigure(Minecraft.getInstance(), ctx, Math.round(cx - fw / 2f), Math.round(cy - fh / 2f), fw, fh, t, dive);

        // bubbles from the figure once under water
        for (int i = 0; i < 18 && dive > 0; i++) {
            float seed = i * 53.9f;
            float life = ((t * (0.45f + (seed % 30) / 60f)) + seed / 37f) % 1f;
            float bx = cx + (float) Math.sin(t * 2f + i) * 6 + ((seed % 50) - 25);
            float by = cy - fh * 0.3f - life * h * 0.7f;
            if (by < surface + 2) continue;
            int r = 1 + (i % 3);
            ctx.fill(Math.round(bx), Math.round(by), Math.round(bx) + r, Math.round(by) + r, a(0xE6F4FF, 0.55f * dive * (1f - life)));
        }

        // the splash: a white flash, droplets flying up and rings on the water
        float since = t - IMPACT;
        if (since > 0 && since < 1.6f) {
            int sy = Math.round(rest - dive * (rest + h * 0.12f));
            float flash = 1f - clamp(since / 0.35f);
            if (flash > 0) ctx.fill(0, 0, w, h, a(0xFFFFFF, 0.28f * flash));
            for (int i = 0; i < 3; i++) {
                float k = clamp((since - i * 0.12f) / 1.1f);
                if (k <= 0 || k >= 1) continue;
                ellipse(ctx, w / 2f, sy, 20 + k * w * 0.3f, 3 + k * 16, a(0xE6F4FF, 0.55f * (1f - k)));
            }
            for (int i = 0; i < 28; i++) {
                float seed = i * 41.7f;
                float ang = (float) Math.toRadians(200 + (seed % 140));
                float sp = 90 + (seed % 120);
                float dx = (float) Math.cos(ang) * sp * since;
                float dy = (float) Math.sin(ang) * sp * since + 260 * since * since;
                float y = sy + dy;
                if (y > sy + 2) continue;
                int r = 1 + (i % 2);
                ctx.fill(Math.round(w / 2f + dx), Math.round(y), Math.round(w / 2f + dx) + r, Math.round(y) + r + 1, a(0xF2FAFF, 0.85f * (1f - since / 1.6f)));
            }
        }

        // a soft vignette and one clean line of text with a thin progress line
        SoftShapes.vignette(ctx, 0, 0, w, h, a(0x000000, 0.4f + 0.3f * deep));
        int ty = h - 34;
        if (title != null) Theme.textCentered(ctx, title, w / 2, ty - 12, a(0xFFFFFF, 0.92f));
        if (detail != null) {
            String d = Theme.ellipsize(detail, w - 40);
            Theme.small(ctx, d, w / 2 - Theme.smallWidth(d) / 2, ty + 2, a(0xBFD4FF, 0.75f));
        }
        int bw = Math.min(160, w / 3);
        ctx.fill(w / 2 - bw / 2, ty + 14, w / 2 + bw / 2, ty + 15, 0x22FFFFFF);
        if (progress >= 0) {
            ctx.fill(w / 2 - bw / 2, ty + 14, w / 2 - bw / 2 + Math.round(bw * Math.min(1f, progress)), ty + 15, 0xCCBFD4FF);
        } else {
            float p = (t * 0.6f) % 1f;
            int x0 = w / 2 - bw / 2 + Math.round((bw - bw / 4f) * p);
            ctx.fill(x0, ty + 14, x0 + bw / 4, ty + 15, 0xAABFD4FF);
        }
    }

    private static void ellipse(GuiGraphics ctx, float cx, float cy, float rx, float ry, int color) {
        int steps = Math.max(24, Math.round(rx / 2));
        for (int i = 0; i < steps; i++) {
            double ang = Math.PI * 2 * i / steps;
            int x = Math.round(cx + (float) Math.cos(ang) * rx);
            int y = Math.round(cy + (float) Math.sin(ang) * ry);
            ctx.fill(x, y, x + 2, y + 1, color);
        }
    }

    private static void drawFigure(Minecraft mc, GuiGraphics ctx, int x, int y, int w, int h, float t, float dive) {
        try {
            if (figure == null) {
                figure = new PlayerSkinWidget(w, h, mc.getEntityModels(), mc.getSkinManager().createLookup(mc.getGameProfile(), false));
            }
            figure.setRectangle(w, h, x, y);
            var acc = (PlayerSkinWidgetAccessor) figure;
            // falling: arms up and flailing; under water the limbs drift slowly
            float flail = (float) Math.sin(t * (dive > 0.5f ? 1.4f : 7f)) * (dive > 0.5f ? 0.18f : 0.28f);
            for (PlayerModel model : new PlayerModel[]{acc.onix$wide(), acc.onix$slim()}) {
                model.rightArm.zRot = 2.55f + flail;
                model.leftArm.zRot = -2.55f - flail;
                model.rightArm.xRot = -0.2f;
                model.leftArm.xRot = -0.2f;
                model.rightLeg.zRot = 0.18f + flail * 0.6f;
                model.leftLeg.zRot = -0.18f - flail * 0.6f;
                model.rightLeg.xRot = 0.25f + flail;
                model.leftLeg.xRot = -0.15f - flail;
                model.head.xRot = dive > 0.5f ? -0.15f : -0.4f;
            }
            acc.onix$setRotationY(25f + t * (dive > 0.5f ? 18f : 60f));
            acc.onix$setRotationX(-12f + (float) Math.sin(t * 0.8f) * 6f);
            figure.render(ctx, 0, 0, 0f);
        } catch (RuntimeException e) {
            figure = null; // no skin yet: only the scene
        }
    }
}
