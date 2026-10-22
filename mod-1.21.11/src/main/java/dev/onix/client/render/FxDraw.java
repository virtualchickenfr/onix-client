package dev.onix.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Flat shapes for world effects (POSITION_COLOR quads: debug quads for normal blending, lightning for glow).
 * Coordinates are relative to the camera.
 */
public final class FxDraw {
    private FxDraw() {}

    /** A horizontal ring (annulus) around (x, y, z); inner and outer colours may differ for a soft edge. */
    public static void ring(PoseStack matrices, VertexConsumer vc, double x, double y, double z,
                            float inner, float outer, int innerColor, int outerColor, int segments) {
        ring(matrices.last().pose(), vc, x, y, z, inner, outer, innerColor, outerColor, segments);
    }

    public static void ring(Matrix4f m, VertexConsumer vc, double x, double y, double z,
                            float inner, float outer, int innerColor, int outerColor, int segments) {
        float fx = (float) x, fy = (float) y, fz = (float) z;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            vc.addVertex(m, fx + c0 * inner, fy, fz + s0 * inner).setColor(innerColor);
            vc.addVertex(m, fx + c0 * outer, fy, fz + s0 * outer).setColor(outerColor);
            vc.addVertex(m, fx + c1 * outer, fy, fz + s1 * outer).setColor(outerColor);
            vc.addVertex(m, fx + c1 * inner, fy, fz + s1 * inner).setColor(innerColor);
        }
    }

    /**
     * A flat ring whose front faces up, for rings on the ground seen from above (the lightning render type culls
     * back faces, so {@link #ring} – which faces down – disappears there).
     */
    public static void ringUp(PoseStack matrices, VertexConsumer vc, double x, double y, double z,
                              float inner, float outer, int innerColor, int outerColor, int segments) {
        Matrix4f m = matrices.last().pose();
        float fx = (float) x, fy = (float) y, fz = (float) z;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            vc.addVertex(m, fx + c0 * inner, fy, fz + s0 * inner).setColor(innerColor);
            vc.addVertex(m, fx + c1 * inner, fy, fz + s1 * inner).setColor(innerColor);
            vc.addVertex(m, fx + c1 * outer, fy, fz + s1 * outer).setColor(outerColor);
            vc.addVertex(m, fx + c0 * outer, fy, fz + s0 * outer).setColor(outerColor);
        }
    }

    /** Both sides of a flat ring (with a culling render type only the side facing the camera is drawn). */
    public static void ringBoth(PoseStack matrices, VertexConsumer vc, double x, double y, double z,
                                float inner, float outer, int innerColor, int outerColor, int segments) {
        ringUp(matrices, vc, x, y, z, inner, outer, innerColor, outerColor, segments);
        ring(matrices, vc, x, y, z, inner, outer, innerColor, outerColor, segments);
    }

    /**
     * An upright band around a vertical axis from y (colour top) to y + height (colour bottom; height may be negative),
     * both sides – like the glowing trail under a ring.
     */
    public static void band(PoseStack matrices, VertexConsumer vc, double x, double y, double z, float radius, float height,
                            int topColor, int bottomColor, int segments) {
        Matrix4f m = matrices.last().pose();
        float fx = (float) x, fy = (float) y, fz = (float) z;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            float x0 = fx + (float) Math.cos(a0) * radius, z0 = fz + (float) Math.sin(a0) * radius;
            float x1 = fx + (float) Math.cos(a1) * radius, z1 = fz + (float) Math.sin(a1) * radius;
            vc.addVertex(m, x0, fy, z0).setColor(topColor);
            vc.addVertex(m, x1, fy, z1).setColor(topColor);
            vc.addVertex(m, x1, fy + height, z1).setColor(bottomColor);
            vc.addVertex(m, x0, fy + height, z0).setColor(bottomColor);
            vc.addVertex(m, x0, fy, z0).setColor(topColor);
            vc.addVertex(m, x0, fy + height, z0).setColor(bottomColor);
            vc.addVertex(m, x1, fy + height, z1).setColor(bottomColor);
            vc.addVertex(m, x1, fy, z1).setColor(topColor);
        }
    }

    /** A camera-facing square centred on (x, y, z) in the current pose, fading from the centre colour to the edge colour. */
    public static void glow(PoseStack matrices, VertexConsumer vc, float x, float y, float z, float size, int center, int edge) {
        glow(matrices.last().pose(), vc, x, y, z, size, center, edge);
    }

    public static void glow(Matrix4f m, VertexConsumer vc, float x, float y, float z, float size, int center, int edge) {
        float h = size / 2f;
        // four triangles meeting in the middle, as quads with a doubled vertex
        float[][] corners = {{-h, -h}, {h, -h}, {h, h}, {-h, h}};
        for (int i = 0; i < 4; i++) {
            float[] a = corners[i];
            float[] b = corners[(i + 1) % 4];
            vc.addVertex(m, x, y, z).setColor(center);
            vc.addVertex(m, x + a[0], y + a[1], z).setColor(edge);
            vc.addVertex(m, x + b[0], y + b[1], z).setColor(edge);
            vc.addVertex(m, x, y, z).setColor(center);
        }
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * ((color >>> 24) & 0xFF));
        return (a << 24) | (color & 0xFFFFFF);
    }

    /** A colour on the rainbow, by turns (0..1). */
    public static int rainbow(float t) {
        return 0xFF000000 | (java.awt.Color.HSBtoRGB(t - (float) Math.floor(t), 0.75f, 1f) & 0xFFFFFF);
    }
}
