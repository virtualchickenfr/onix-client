package dev.onix.client.gui;

import dev.onix.client.mixin.PlayerSkinWidgetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.player.PlayerModel;

/**
 * The world loading transition – "sunken" (the same scene as the launcher's Play transition): a dark teal void, soft
 * light from above with dust drifting in it, and your own Minecraft character falling backwards through it in slow
 * motion, on its back, arms and legs stretched up towards the light, while the world loads.
 */
public final class FallTransition {
    private FallTransition() {}

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

    private static float rnd(float seed) {
        double x = Math.sin(seed * 127.1 + 311.7) * 43758.5453;
        return (float) (x - Math.floor(x));
    }

    private static int a(int rgb, float alpha) {
        return Math.round(clamp(alpha) * 255) << 24 | (rgb & 0xFFFFFF);
    }

    public static void render(Object screen, GuiGraphics ctx, String title, String detail, float progress) {
        long now = System.currentTimeMillis();
        if (owner != screen) {
            if (owner == null || now - start > 20_000) start = now;
            owner = screen;
        }
        float t = (now - start) / 1000f;
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        float u = h / 100f;

        drawVoid(ctx, w, h, t);

        // the character: drops into view, then sinks slowly away (smaller, darker), turning as it goes
        float enter = smooth(t / 1.2f);
        float sink = clamp((t - 0.8f) / 4f);
        int fh = Math.round(Math.min(h * 0.7f, 320) * (1f - 0.42f * sink));
        int fw = Math.round(fh * 1.3f);                         // the figure lies on its back: wide
        float cx = w / 2f + (float) Math.sin(t * 0.7f) * 2.5f * u;
        float cy = -fh * 0.15f + (h * 0.5f + fh * 0.15f) * enter + sink * 8 * u;
        SoftShapes.glow(ctx, cx, cy, fw * 0.6f, fh * 0.5f, a(0x6FD2DC, 0.12f * (1 - sink)));
        drawFigure(Minecraft.getInstance(), ctx, Math.round(cx - fw / 2f), Math.round(cy - fh / 2f), fw, fh, t);
        // the deep swallows it: a teal veil over the figure that thickens as it sinks
        SoftShapes.glow(ctx, cx, cy, fw * 0.55f, fh * 0.55f, a(0x08343E, 0.15f + 0.6f * sink));

        // vignette and one clean line of text with a thin progress line
        SoftShapes.vignette(ctx, 0, 0, w, h, a(0x000406, 0.75f));
        int ty = h - 34;
        if (title != null) Theme.textCentered(ctx, title, w / 2, ty - 12, a(0xFFFFFF, 0.9f));
        if (detail != null) {
            String d = Theme.ellipsize(detail, w - 40);
            Theme.small(ctx, d, w / 2 - Theme.smallWidth(d) / 2, ty + 2, a(0xAEE6EC, 0.7f));
        }
        int bw = Math.min(160, w / 3);
        ctx.fill(w / 2 - bw / 2, ty + 14, w / 2 + bw / 2, ty + 15, 0x22FFFFFF);
        if (progress >= 0) {
            ctx.fill(w / 2 - bw / 2, ty + 14, w / 2 - bw / 2 + Math.round(bw * Math.min(1f, progress)), ty + 15, 0xCCAEE6EC);
        } else {
            float pp = (t * 0.6f) % 1f;
            int x0 = w / 2 - bw / 2 + Math.round((bw - bw / 4f) * pp);
            ctx.fill(x0, ty + 14, x0 + bw / 4, ty + 15, 0xAAAEE6EC);
        }
    }

    /** The scene without the figure: the dark teal void, light falling in from above, dust drifting in it. */
    public static void drawVoid(GuiGraphics ctx, int w, int h, float t) {
        float u = h / 100f;
        // the void: dark teal, lighter where the light comes from
        ctx.fillGradient(0, 0, w, Math.round(h * 0.45f), 0xFF0F3A44, 0xFF08232B);
        ctx.fillGradient(0, Math.round(h * 0.45f), w, h, 0xFF08232B, 0xFF020709);
        // light from above: a haze at the top and swaying beams
        SoftShapes.glow(ctx, w / 2f, -10 * u, w * 0.55f, 70 * u, 0x5578D2DC);
        float sway = (float) Math.sin(t * 0.5f) * 2 * u;
        for (int i = 0; i < 5; i++) {
            float x = w / 2f + (i - 2) * 9 * u + sway * (i % 2 == 1 ? 1 : -1);
            float bw = (4 + i) * u;
            ctx.fillGradient(Math.round(x - bw / 2), 0, Math.round(x + bw / 2), Math.round(h * 0.88f),
                    a(0x96E1EB, 0.07f + 0.03f * (float) Math.sin(t * 1.1f + i)), 0x0096E1EB);
        }
        // dust drifting slowly in the light, three depths
        for (int i = 0; i < 80; i++) {
            float z = 0.4f + (i % 3) * 0.3f;
            float x = ((rnd(i) * w + (float) Math.sin(t * 0.4f + i) * 3 * u * z) % w + w) % w;
            float y = ((rnd(i + 7) * h - t * (2 + 3 * z) * u) % h + h) % h;
            float near = clamp(1 - Math.abs(x - w / 2f) / (45 * u));
            int s = z > 0.9f ? 2 : 1;
            ctx.fill(Math.round(x), Math.round(y), Math.round(x) + s, Math.round(y) + s, a(0xC8F5FA, (0.25f + 0.5f * near) * z * (0.4f + 0.6f * rnd(i + 3))));
        }

    }

    /**
     * Puts a player figure into the sunken pose for time t: falling backwards on its back, arms and legs stretched up
     * towards the light, looking up at it, turning slowly.
     */
    public static void pose(PlayerSkinWidget widget, float t) {
        var acc = (PlayerSkinWidgetAccessor) widget;
        float drift = (float) Math.sin(t * 1.3f) * 0.1f;
        float legDrift = (float) Math.sin(t * 1.1f) * 0.1f;
        for (PlayerModel model : new PlayerModel[]{acc.onix$wide(), acc.onix$slim()}) {
            // arms and legs forward – with the body lying back they point up to the light
            model.rightArm.xRot = -1.66f + drift;
            model.leftArm.xRot = -1.43f - drift;
            model.rightArm.yRot = 0f;
            model.leftArm.yRot = 0f;
            model.rightArm.zRot = 0.17f;
            model.leftArm.zRot = -0.17f;
            model.rightLeg.xRot = -1.43f + legDrift;
            model.leftLeg.xRot = -1.68f - legDrift;
            model.rightLeg.zRot = 0.07f;
            model.leftLeg.zRot = -0.07f;
            model.head.xRot = -0.52f;      // looking up at the light
            model.head.yRot = 0f;
            // the whole body falls backwards (on its back), a little smaller so the lying figure stays in the frame
            var root = model.root();
            root.xRot = -1.4f + (float) Math.sin(t * 0.7f) * 0.1f;
            root.zRot = 0f;
            root.xScale = root.yScale = root.zScale = 0.8f;
            root.y = 6f;
        }
        acc.onix$setRotationX(-6f + (float) Math.sin(t * 0.6f) * 3f);
        acc.onix$setRotationY(70f + (float) Math.sin(t * 0.35f) * 12f);
    }

    private static void drawFigure(Minecraft mc, GuiGraphics ctx, int x, int y, int w, int h, float t) {
        try {
            if (figure == null) {
                figure = new PlayerSkinWidget(w, h, mc.getEntityModels(), mc.getSkinManager().createLookup(mc.getGameProfile(), false));
            }
            figure.setRectangle(w, h, x, y);
            pose(figure, t);
            figure.render(ctx, 0, 0, 0f);
        } catch (RuntimeException e) {
            figure = null;
        }
    }
}
