package dev.onix.client.cosmetics;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;

/**
 * Draws catalog models. The animations mirror placed() in launcher/src/js/cosrender.js, so the
 * launcher previews move the same way as the game.
 */
public final class CosmeticModels {
    public static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    private static final int MANNEQUIN = 0xFF4A505A;
    private static final Map<String, float[]> BOUNDS = new HashMap<>();

    private CosmeticModels() {}

    /**
     * Draws a model (and its extra part) at time t in ticks. speed > 1 makes it move faster (wings while gliding).
     * The matrices must already be in the anchor's space (head or body, model pixels).
     */
    public static void render(PoseStack m, VertexConsumer vc, int light, CosmeticCatalog.Model model, int[] palette, float t, float speed) {
        draw(m, vc, light, model, palette, t, speed);
        if (model.extra() != null) {
            CosmeticCatalog.Model part = CosmeticCatalog.model(model.extra());
            if (part != null) draw(m, vc, light, part, palette, t, speed);
        }
    }

    private static void draw(PoseStack m, VertexConsumer vc, int light, CosmeticCatalog.Model model, int[] palette, float t, float speed) {
        place(m, model.anim(), t, speed, (ms, copy) -> emit(ms, vc, light, model.cubes(), model.parts(), palette, t + copy * 7.3f));
    }

    /** A node's cubes, then its parts with their own animation inside it (parts can hold parts). */
    private static void emit(PoseStack m, VertexConsumer vc, int light, java.util.List<CosmeticCatalog.Cube> cubes,
                             java.util.List<CosmeticCatalog.Part> parts, int[] palette, float t) {
        for (CosmeticCatalog.Cube c : cubes) {
            ModelBox.cube(m, vc, c.glow() ? FULL_BRIGHT : light, c.x1(), c.y1(), c.z1(), c.x2(), c.y2(), c.z2(), c.color(palette), c.glow());
        }
        for (CosmeticCatalog.Part part : parts) {
            m.pushPose();
            if (part(m, part.anim(), t)) emit(m, vc, light, part.cubes(), part.parts(), palette, t);
            m.popPose();
        }
    }

    /**
     * Applies a part's own animation around its pivot; false while the part is hidden (a twinkle between
     * flashes). Mirrors partMatrix() in cosrender.js.
     */
    static boolean part(PoseStack m, CosmeticCatalog.Anim a, float t) {
        float[] p = a.pivot();
        double ph = a.phase();
        switch (a.type()) {
            case "rot" -> {
                around(m, p, () -> m.mulPose(axis(a).rotationDegrees((float) (a.base() + a.amp() * Math.sin(t * a.speed() + ph)))));
            }
            case "spin" -> around(m, p, () -> m.mulPose(axis(a).rotationDegrees(t * a.speed() + a.base())));
            case "pulse" -> {
                float k = (float) (1 + a.amp() * Math.sin(t * a.speed() + ph));
                around(m, p, () -> m.scale(k, k, k));
            }
            case "blink" -> {
                if (a.period() > 0 && mod(t + ph, a.period()) < a.dur()) around(m, p, () -> m.scale(1f, 0.15f, 1f));
            }
            case "twinkle" -> {
                double v = Math.sin(t * a.speed() + ph);
                float k = v > 0 ? (float) (v * v * v) : 0f;
                if (k < 0.03f) return false;
                around(m, p, () -> m.scale(k, k, k));
            }
            case "flicker" -> {
                float k = (float) (0.8 + 0.12 * Math.sin(t * a.speed() * 1.7 + ph) + 0.08 * Math.sin(t * a.speed() * 3.1));
                float w = 1 - (k - 0.8f) * 0.5f;
                around(m, p, () -> m.scale(w, k, w));
            }
            case "bob" -> m.translate(0, px((float) (a.amp() * Math.sin(t * a.speed() + ph))), 0);
            case "slide" -> {
                float d = px((float) (a.amp() * Math.sin(t * a.speed() + ph)));
                switch (a.axis()) {
                    case "x" -> m.translate(d, 0, 0);
                    case "z" -> m.translate(0, 0, d);
                    default -> m.translate(0, d, 0);
                }
            }
            default -> {
                // static
            }
        }
        return true;
    }

    private static void around(PoseStack m, float[] p, Runnable transform) {
        m.translate(px(p[0]), px(p[1]), px(p[2]));
        transform.run();
        m.translate(px(-p[0]), px(-p[1]), px(-p[2]));
    }

    private static Axis axis(CosmeticCatalog.Anim a) {
        return switch (a.axis()) {
            case "x" -> Axis.XP;
            case "z" -> Axis.ZP;
            default -> Axis.YP;
        };
    }

    /** Receives each copy of a model (orbit, rise … make several) with its index. */
    @FunctionalInterface
    interface Copy {
        void draw(PoseStack m, int copy);
    }

    /** Calls draw once per copy of the model, with the matrices moved for the animation. */
    static void place(PoseStack m, CosmeticCatalog.Anim a, float t, float speed, Copy draw) {
        switch (a.type()) {
            case "bob" -> {
                m.pushPose();
                m.translate(0, px((float) Math.sin(t * a.speed() * speed) * a.amp()), 0);
                draw.draw(m, 0);
                m.popPose();
            }
            case "spin" -> {
                m.pushPose();
                m.mulPose(Axis.YP.rotationDegrees(t * a.speed() * speed));
                draw.draw(m, 0);
                m.popPose();
            }
            case "flap" -> {
                float range = a.range() * Math.min(2.5f, speed);
                float angle = a.base() + (float) Math.sin(t * a.speed() * speed) * range;
                float[] p = a.pivot();
                for (int side = -1; side <= 1; side += 2) {
                    m.pushPose();
                    m.translate(px(side * p[0]), px(p[1]), px(p[2]));
                    m.mulPose(Axis.YP.rotationDegrees(-side * angle));
                    m.mulPose(Axis.ZP.rotationDegrees(side * -8f));
                    if (side < 0) m.scale(-1f, 1f, 1f);
                    draw.draw(m, 0);
                    m.popPose();
                }
            }
            case "orbit" -> {
                for (int i = 0; i < a.count(); i++) {
                    double ang = Math.toRadians(t * a.speed() * speed) + (i * 2 * Math.PI) / a.count();
                    m.pushPose();
                    m.translate(px((float) (Math.cos(ang) * a.radius())), px((float) (a.y() + Math.sin(ang * 2 + i) * a.wobble())),
                            px((float) (Math.sin(ang) * a.radius())));
                    draw.draw(m, i);
                    m.popPose();
                }
            }
            case "spiral" -> {
                for (int i = 0; i < a.count(); i++) {
                    double f = i / (double) a.count();
                    double ang = Math.toRadians(t * a.speed() * speed * 4) + f * Math.PI * 4;
                    m.pushPose();
                    m.translate(px((float) (Math.cos(ang) * a.radius())), px((float) (a.y() - f * a.height())), px((float) (Math.sin(ang) * a.radius())));
                    draw.draw(m, i);
                    m.popPose();
                }
            }
            case "rise", "fall" -> {
                boolean rise = a.type().equals("rise");
                for (int i = 0; i < a.count(); i++) {
                    double f = mod(i / (double) a.count() + t * a.speed() * speed * 0.02, 1);
                    double ang = i * 2.39996;
                    double r = a.radius() * (0.55 + 0.45 * mod(i * 0.618, 1));
                    double y = rise ? a.y() - f * a.height() : a.y() + f * a.height();
                    float s = (float) Math.max(0.05, Math.sin(f * Math.PI));
                    m.pushPose();
                    m.translate(px((float) (Math.cos(ang) * r)), px((float) y), px((float) (Math.sin(ang) * r)));
                    m.scale(s, s, s);
                    draw.draw(m, i);
                    m.popPose();
                }
            }
            default -> draw.draw(m, 0); // static, flicker
        }
    }

    private static float px(float pixels) {
        return pixels / 16f;
    }

    private static double mod(double v, double p) {
        return ((v % p) + p) % p;
    }

    // ---------------------------------------------------------------- menu icons

    /** Camera angles per slot (yaw, pitch), the same as VIEWS in cosrender.js. */
    private static float[] view(String slot) {
        return switch (slot) {
            case "face" -> new float[]{-22, -8};
            case "neck" -> new float[]{-24, -10};
            case "back" -> new float[]{155, -12};
            case "aura" -> new float[]{-20, -16};
            case "pet" -> new float[]{-40, -12};
            default -> new float[]{-28, -18};
        };
    }

    /** Grey stand-in head or torso so hats, masks and scarves are shown in place. */
    private static float[][] mannequin(String slot) {
        return switch (slot) {
            case "head", "face" -> new float[][]{{-4, -8, -4, 4, 0, 4}};
            case "neck" -> new float[][]{{-4, -8, -4, 4, 0, 4}, {-4, 0, -2, 4, 5, 2}};
            default -> new float[0][];
        };
    }

    /**
     * Draws a model item into a rectangle of the menu. t = animation time in ticks,
     * extraYaw turns it (for the hovered tile).
     */
    public static void drawIcon(GuiGraphics ctx, CosmeticCatalog.Item item, int x, int y, int w, int h, float t, float extraYaw) {
        CosmeticCatalog.Model model = CosmeticCatalog.model(item.model());
        if (model == null) return;
        int[] palette = CosmeticCatalog.palette(item.palette());
        float[] v = view(model.slot());
        // a turning model is fitted for every angle, so it does not zoom in and out while it turns
        boolean turning = extraYaw != 0f;
        float[] b = BOUNDS.computeIfAbsent(model.id() + (turning ? "@turn" : ""), k -> turning ? turningBounds(model, v) : bounds(model, v[0], v[1]));
        float bw = Math.max(0.1f, b[2] - b[0]);
        float bh = Math.max(0.1f, b[3] - b[1]);
        float scale = Math.min(w / (bw * 1.2f), h / (bh * 1.2f));

        PoseStack ms = ctx.pose();
        ms.pushPose();
        ms.translate(x + w / 2f, y + h / 2f, 150f);
        ms.scale(scale, scale, -scale);
        ms.translate(-(b[0] + b[2]) / 2f, -(b[1] + b[3]) / 2f, 0f);
        ms.mulPose(Axis.XP.rotationDegrees(v[1]));
        ms.mulPose(Axis.YP.rotationDegrees(v[0] + extraYaw));
        VertexConsumer vc = ctx.bufferSource().getBuffer(RenderType.entityCutoutNoCull(Cosmetics.WHITE));
        for (float[] c : mannequin(model.slot())) ModelBox.cube(ms, vc, FULL_BRIGHT, c[0], c[1], c[2], c[3], c[4], c[5], MANNEQUIN);
        render(ms, vc, FULL_BRIGHT, model, palette, t, 1f);
        ms.popPose();
    }

    private static float[] turningBounds(CosmeticCatalog.Model model, float[] view) {
        float[] out = bounds(model, view[0], view[1]);
        for (int yaw = 45; yaw < 360; yaw += 45) {
            float[] b = bounds(model, view[0] + yaw, view[1]);
            out[0] = Math.min(out[0], b[0]);
            out[1] = Math.min(out[1], b[1]);
            out[2] = Math.max(out[2], b[2]);
            out[3] = Math.max(out[3], b[3]);
        }
        // keep the centre where the still model has it, so the hovered tile does not jump
        float[] still = bounds(model, view[0], view[1]);
        float cx = (still[0] + still[2]) / 2f;
        float cy = (still[1] + still[3]) / 2f;
        float hw = Math.max(cx - out[0], out[2] - cx);
        float hh = Math.max(cy - out[1], out[3] - cy);
        return new float[]{cx - hw, cy - hh, cx + hw, cy + hh};
    }

    /** Screen-space bounds (min x, min y, max x, max y, in blocks) of a model seen from the given angles, at t = 0. */
    private static float[] bounds(CosmeticCatalog.Model model, float yaw, float pitch) {
        float[] out = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        PoseStack ms = new PoseStack();
        ms.mulPose(Axis.XP.rotationDegrees(pitch));
        ms.mulPose(Axis.YP.rotationDegrees(yaw));
        Consumer<float[]> corners = c -> {
            Matrix4f mat = ms.last().pose();
            for (int i = 0; i < 8; i++) {
                Vector3f p = mat.transformPosition(new Vector3f(
                        px((i & 1) == 0 ? c[0] : c[3]), px((i & 2) == 0 ? c[1] : c[4]), px((i & 4) == 0 ? c[2] : c[5])));
                out[0] = Math.min(out[0], p.x);
                out[1] = Math.min(out[1], p.y);
                out[2] = Math.max(out[2], p.x);
                out[3] = Math.max(out[3], p.y);
            }
        };
        for (float[] c : mannequin(model.slot())) corners.accept(c);
        addBounds(ms, model, corners);
        if (model.extra() != null) {
            CosmeticCatalog.Model part = CosmeticCatalog.model(model.extra());
            if (part != null) addBounds(ms, part, corners);
        }
        if (out[0] > out[2]) return new float[]{-0.5f, -0.5f, 0.5f, 0.5f};
        return out;
    }

    private static void addBounds(PoseStack ms, CosmeticCatalog.Model model, Consumer<float[]> corners) {
        place(ms, model.anim(), 0f, 1f, (m, copy) -> boundsOf(model.cubes(), model.parts(), corners));
    }

    /** Parts count at rest (a twinkle that is hidden at t = 0 still needs room). */
    private static void boundsOf(java.util.List<CosmeticCatalog.Cube> cubes, java.util.List<CosmeticCatalog.Part> parts, Consumer<float[]> corners) {
        for (CosmeticCatalog.Cube c : cubes) corners.accept(new float[]{c.x1(), c.y1(), c.z1(), c.x2(), c.y2(), c.z2()});
        for (CosmeticCatalog.Part p : parts) boundsOf(p.cubes(), p.parts(), corners);
    }
}
