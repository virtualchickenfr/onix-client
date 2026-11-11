package dev.onix.client.gui;

import dev.onix.client.mixin.PlayerSkinWidgetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.player.PlayerModel;

/**
 * The world loading transition: your own Minecraft character falls into a deep, quiet blue and slowly sinks while the
 * world loads. Light falls in from above, faint streaks rush past, tiny bubbles rise. Clean: no frames, one thin line
 * for the progress and one line of text.
 */
public final class FallTransition {
    private FallTransition() {}

    private static PlayerSkinWidget figure;
    private static long start;
    private static Object owner;

    private static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private static int a(int rgb, float alpha) {
        return Math.round(Math.max(0f, Math.min(1f, alpha)) * 255) << 24 | (rgb & 0xFFFFFF);
    }

    /** Draws the transition for {@code screen} (a new screen starts the fall again). */
    public static void render(Object screen, GuiGraphics ctx, String title, String detail, float progress) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        // one fall across the loading screens of one join (they follow each other within a few seconds)
        if (owner != screen) {
            if (owner == null || now - start > 20_000) start = now;
            owner = screen;
        }
        float t = (now - start) / 1000f;
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();

        // the depth: dark blue that gets deeper the longer you sink
        float deep = smooth(t / 6f);
        ctx.fillGradient(0, 0, w, h, Theme.lerpColor(0xFF0E1A2B, 0xFF07101C, deep), Theme.lerpColor(0xFF040810, 0xFF010204, deep));
        // light from the surface above, fading as you sink
        SoftShapes.glow(ctx, w / 2f, -h * 0.15f, w * 0.7f, h * 0.55f, a(0x7FB8FF, 0.22f * (1f - deep * 0.7f)));
        for (int i = 0; i < 5; i++) {
            float x = w * (0.18f + i * 0.16f) + (float) Math.sin(t * 0.4f + i) * 12;
            float beam = 0.05f + 0.03f * (float) Math.sin(t * 0.9f + i * 1.7f);
            ctx.fillGradient(Math.round(x), 0, Math.round(x + w * 0.035f), Math.round(h * 0.75f), a(0xBFDFFF, beam * (1f - deep * 0.6f)), 0x00BFDFFF);
        }
        // streaks rushing upwards: the speed of the fall, slowing down as the water takes over
        float speed = 2.2f - 1.6f * smooth(t / 3f);
        for (int i = 0; i < 26; i++) {
            float seed = i * 97.31f;
            float x = (seed * 13.7f) % w;
            float len = 18 + (seed % 40);
            float y = h - ((t * speed * (180 + (seed % 140)) + seed * 7) % (h + len));
            ctx.fill(Math.round(x), Math.round(y), Math.round(x) + 1, Math.round(y + len), a(0xFFFFFF, 0.05f + (i % 4) * 0.02f));
        }
        // tiny bubbles rising around the figure once it is under water
        float under = smooth((t - 1.2f) / 1.5f);
        for (int i = 0; i < 14; i++) {
            float seed = i * 53.9f;
            float bx = w / 2f + (float) Math.sin(t * 1.3f + i) * 10 + ((seed % 120) - 60);
            float by = h - ((t * (30 + seed % 30) + seed * 5) % (h * 0.9f));
            int r = 1 + (i % 3);
            ctx.fill(Math.round(bx), Math.round(by), Math.round(bx) + r, Math.round(by) + r, a(0xDDEEFF, 0.35f * under));
        }

        // the character: drops in from above, then drifts and tumbles slowly in the deep
        int fh = Math.round(Math.min(h * 0.42f, 190));
        int fw = Math.round(fh * 0.55f);
        float drop = smooth(t / 1.3f);
        float cy = -fh + (h * 0.44f + fh) * drop + (float) Math.sin(t * 1.1f) * 5 * under + t * 1.5f * under;
        cy = Math.min(cy, h * 0.52f);
        float cx = w / 2f + (float) Math.sin(t * 0.7f) * 8 * under;
        SoftShapes.glow(ctx, cx, cy, fw * 1.4f, fh * 0.7f, a(0x6FA8FF, 0.16f));
        drawFigure(mc, ctx, Math.round(cx - fw / 2f), Math.round(cy - fh / 2f), fw, fh, t, under);

        // depth vignette and one clean line of text with a thin progress line
        SoftShapes.vignette(ctx, 0, 0, w, h, a(0x000000, 0.55f + 0.25f * deep));
        int ty = h - 34;
        if (title != null) Theme.textCentered(ctx, title, w / 2, ty - 12, a(0xFFFFFF, 0.92f));
        if (detail != null) Theme.small(ctx, Theme.ellipsize(detail, w - 40), w / 2 - Theme.smallWidth(Theme.ellipsize(detail, w - 40)) / 2, ty + 2, a(0xBFD4FF, 0.7f));
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

    private static void drawFigure(Minecraft mc, GuiGraphics ctx, int x, int y, int w, int h, float t, float under) {
        try {
            if (figure == null) {
                figure = new PlayerSkinWidget(w, h, mc.getEntityModels(), mc.getSkinManager().createLookup(mc.getGameProfile(), false));
            }
            figure.setRectangle(w, h, x, y);
            var acc = (PlayerSkinWidgetAccessor) figure;
            // falling: arms up, legs apart; under water the limbs drift slowly
            float drift = (float) Math.sin(t * 1.4f) * 0.18f * (0.4f + under);
            for (PlayerModel model : new PlayerModel[]{acc.onix$wide(), acc.onix$slim()}) {
                model.rightArm.zRot = 2.55f + drift;
                model.leftArm.zRot = -2.55f - drift;
                model.rightArm.xRot = -0.2f;
                model.leftArm.xRot = -0.2f;
                model.rightLeg.zRot = 0.18f + drift * 0.6f;
                model.leftLeg.zRot = -0.18f - drift * 0.6f;
                model.rightLeg.xRot = 0.25f + drift;
                model.leftLeg.xRot = -0.15f - drift;
                model.head.xRot = -0.35f;
            }
            acc.onix$setRotationY(25f + t * 22f);
            acc.onix$setRotationX(-12f + (float) Math.sin(t * 0.8f) * 6f);
            figure.render(ctx, 0, 0, 0f);
        } catch (RuntimeException e) {
            figure = null; // no skin yet: only the scene
        }
    }
}
