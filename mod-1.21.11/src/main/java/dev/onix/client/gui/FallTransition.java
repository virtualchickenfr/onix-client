package dev.onix.client.gui;

import dev.onix.client.mixin.PlayerSkinWidgetAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.model.player.PlayerModel;

/**
 * The world loading transition – "sunken" (the same scene as the launcher's start screen): under water, the surface
 * shimmering far above and light falling in through it, specks drifting, and your own Minecraft character falling
 * backwards through it in slow motion, on its back, arms and legs stretched up towards the light, a trail of air
 * bubbles rising from it, while the world loads.
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

        // the character: drops in, slows down as the water takes it, then sinks slowly, smaller and darker
        float enter = 1 - (float) Math.pow(1 - clamp(t / 1.6f), 3);
        float sink = smooth((t - 1.2f) / 4.5f);
        int fh = Math.round(Math.min(h * 0.72f, 330) * (1f - 0.35f * sink));
        int fw = Math.round(fh * 1.3f);                         // the figure lies on its back: wide
        float cx = w / 2f + (float) Math.sin(t * 0.45f) * 1.5f * u;
        float cy = -fh * 0.4f + (h * 0.46f + fh * 0.4f) * enter + sink * 8 * u + (float) Math.sin(t * 0.9f) * 0.6f * u;
        // light from the surface falls on it: a soft bright halo behind, the water's colour over it as it sinks
        SoftShapes.glow(ctx, cx, cy - fh * 0.15f, fw * 0.55f, fh * 0.5f, a(0x8FE3EA, 0.16f * (1 - sink)));
        drawFigure(Minecraft.getInstance(), ctx, Math.round(cx - fw / 2f), Math.round(cy - fh / 2f), fw, fh, t);
        SoftShapes.glow(ctx, cx, cy, fw * 0.5f, fh * 0.55f, a(0x0A3C48, 0.1f + 0.45f * sink));
        bubbles(ctx, t, cx, cy, fh, u);

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

    /** The water without the figure: deep teal, the shimmering surface far above, light rays and drifting specks. */
    public static void drawVoid(GuiGraphics ctx, int w, int h, float t) {
        // the shader water (caustics, shafts, specks) when the graphics card runs it; this simpler water otherwise
        WaterScene.request();
        if (WaterScene.drawn()) return;
        float u = h / 100f;
        ctx.fillGradient(0, 0, w, Math.round(h * 0.3f), 0xFF16505A, 0xFF0C3039);
        ctx.fillGradient(0, Math.round(h * 0.3f), w, Math.round(h * 0.7f), 0xFF0C3039, 0xFF061A20);
        ctx.fillGradient(0, Math.round(h * 0.7f), w, h, 0xFF061A20, 0xFF02080A);
        // the surface: a bright glow at the top with soft streaks of light moving like waves
        SoftShapes.glow(ctx, w / 2f, -18 * u, w * 0.6f, 75 * u, 0x6896E6EB);
        for (int i = 0; i < 22; i++) {
            float x = (rnd(i) * 1.4f - 0.2f) * w + (float) Math.sin(t * 0.35f + i * 1.7f) * 6 * u;
            float y = (1.5f + rnd(i + 40) * 9) * u + (float) Math.sin(t * 0.8f + i) * 0.6f * u;
            float len = (4 + rnd(i + 9) * 10) * u;
            float alpha = 0.04f + 0.05f * (0.5f + 0.5f * (float) Math.sin(t * 1.6f + i * 2.3f));
            float th = Math.max(1, (0.4f + rnd(i + 3) * 0.5f) * u);
            gg.carbon.client.ui.R2D.rrect(ctx, x - len / 2, y - th / 2, len, th, th / 2, a(0xC8FAFF, alpha));
        }
        // light rays from the surface: soft wedges that sway and breathe
        for (int i = 0; i < 7; i++) {
            float base = w / 2f + (i - 3) * 11 * u + (float) Math.sin(t * 0.22f + i * 1.3f) * 4 * u;
            float lean = (float) Math.sin(t * 0.17f + i) * 6 * u;
            float top = (1.2f + (i % 3) * 0.6f) * u;
            float bottom = (9 + (i % 4) * 4) * u;
            float reach = h * (0.75f + 0.15f * rnd(i + 20));
            float alpha = 0.06f + 0.035f * (float) Math.sin(t * 0.9f + i * 1.9f);
            int c0 = a(0xAAF0F5, alpha), c1 = a(0x96E1EB, 0);
            quad(ctx, base - top, 0, base + top, 0, base + lean + bottom, reach, base + lean - bottom, reach, c0, c0, c1, c1);
        }
        // specks drifting in the water, three depths, brighter in the light
        for (int i = 0; i < 90; i++) {
            float z = 0.35f + (i % 3) * 0.3f;
            float x = ((rnd(i) * w + (float) Math.sin(t * 0.3f + i) * 4 * u * z) % w + w) % w;
            float y = ((rnd(i + 7) * h - t * (1.2f + 2.5f * z) * u) % h + h) % h;
            float light = clamp(1 - Math.abs(x - w / 2f) / (50 * u)) * clamp(1 - y / h * 0.8f);
            float r = z > 0.9f ? 1.2f : 0.8f;
            gg.carbon.client.ui.R2D.rrect(ctx, x - r, y - r, r * 2, r * 2, r, a(0xCDF8FC, (0.12f + 0.55f * light) * z * (0.4f + 0.6f * rnd(i + 3))));
        }
    }

    /** A four-cornered shape with a colour per corner (both windings: the GUI pipeline culls one side). */
    private static void quad(GuiGraphics ctx, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                             int c0, int c1, int c2, int c3) {
        GuiTriangles.draw(ctx, new float[]{x0, y0, x1, y1, x2, y2, x0, y0, x2, y2, x3, y3, x0, y0, x2, y2, x1, y1, x0, y0, x3, y3, x2, y2},
                new int[]{c0, c1, c2, c0, c2, c3, c0, c2, c1, c0, c3, c2});
    }

    /** Air bubbles rising from the face and hands: born now and then, wobbling, growing a little, fading. */
    private static final java.util.List<float[]> BUBBLES = new java.util.ArrayList<>();   // x, y, r, speed, wobble, seed, life, age
    private static float lastT = -1;
    private static float nextBubble;

    private static void bubbles(GuiGraphics ctx, float t, float cx, float cy, float fh, float u) {
        float dt = lastT < 0 || t < lastT ? 0 : Math.min(0.05f, t - lastT);
        if (t < lastT) {
            BUBBLES.clear();
            nextBubble = 0;
        }
        lastT = t;
        if (t > 0.6f && t > nextBubble) {
            int n = 1 + (int) (rnd(t * 13) * 2);
            for (int k = 0; k < n; k++) {
                BUBBLES.add(new float[]{cx + (rnd(t * 7 + k) - 0.3f) * fh * 0.4f, cy - fh * 0.2f + (rnd(t * 3 + k) - 0.5f) * fh * 0.1f,
                        0.35f + rnd(t * 5 + k) * 0.8f, 4 + rnd(t * 11 + k) * 4, 3 + rnd(k + t) * 3, rnd(t + k * 9) * 6,
                        2.2f + rnd(t * 17 + k) * 1.6f, 0});
            }
            nextBubble = t + 0.3f + rnd(t * 19) * 0.6f;
        }
        for (int i = BUBBLES.size() - 1; i >= 0; i--) {
            float[] b = BUBBLES.get(i);
            b[7] += dt;
            float age = b[7];
            b[1] -= (b[3] + age * 0.6f) * u * dt;
            float alpha = clamp(1 - age / b[6]) * clamp(age * 4);
            if (alpha <= 0.01f || b[1] < -10) {
                BUBBLES.remove(i);
                continue;
            }
            float x = b[0] + (float) Math.sin(age * b[4] + b[5]) * 0.8f * u;
            float r = Math.max(1.2f, b[2] * u * (1 + age * 0.12f));
            gg.carbon.client.ui.R2D.rrect(ctx, x - r, b[1] - r, r * 2, r * 2, r, a(0xBEF5FF, 0.1f * alpha));
            gg.carbon.client.ui.R2D.outline(ctx, x - r, b[1] - r, r * 2, r * 2, r, Math.max(0.6f, r * 0.22f), a(0xD2FAFF, 0.55f * alpha));
            float hr = r * 0.28f;
            gg.carbon.client.ui.R2D.rrect(ctx, x - r * 0.35f - hr, b[1] - r * 0.35f - hr, hr * 2, hr * 2, hr, a(0xFFFFFF, 0.75f * alpha));
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
            model.rightLeg.xRot = -0.98f + legDrift;
            model.leftLeg.xRot = -1.15f - legDrift;
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
