package dev.onix.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;

/**
 * Draws solid-colour cuboids in model pixels (1/16 block, Y pointing down). Side faces get a soft
 * gradient (lighter at the top, darker at the bottom), the same as cubePolys() in cosrender.js.
 */
public final class ModelBox {
    /** Brightness of the bottom edge of a side face compared to its top edge. */
    private static final float GRADIENT = 0.78f;

    private ModelBox() {}

    public static void cube(PoseStack matrices, VertexConsumer vc, int light,
                            float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        cube(matrices, vc, light, x1, y1, z1, x2, y2, z2, color, false);
    }

    /** flat = no shading at all (glowing parts look evenly lit). */
    public static void cube(PoseStack matrices, VertexConsumer vc, int light,
                            float x1, float y1, float z1, float x2, float y2, float z2, int color, boolean flat) {
        PoseStack.Pose e = matrices.last();
        Matrix4f m = e.pose();
        float ax = x1 / 16f, ay = y1 / 16f, az = z1 / 16f;
        float bx = x2 / 16f, by = y2 / 16f, bz = z2 / 16f;

        int top = flat ? color : shade(color, 1.00f);
        int bottom = flat ? color : shade(color, 0.55f);
        // y1 is the upper edge (Y points down)
        quad(m, e, vc, light, top, top, ay, 0, -1, 0, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
        quad(m, e, vc, light, bottom, bottom, ay, 0, 1, 0, ax, by, az, ax, by, bz, bx, by, bz, bx, by, az);
        side(m, e, vc, light, color, flat, 0.85f, ay, 0, 0, -1, ax, ay, az, ax, by, az, bx, by, az, bx, ay, az); // front
        side(m, e, vc, light, color, flat, 0.80f, ay, 0, 0, 1, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);  // back
        side(m, e, vc, light, color, flat, 0.70f, ay, -1, 0, 0, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az); // left
        side(m, e, vc, light, color, flat, 0.72f, ay, 1, 0, 0, bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz);  // right
    }

    private static void side(Matrix4f m, PoseStack.Pose e, VertexConsumer vc, int light, int color, boolean flat, float f, float topY,
                             float nx, float ny, float nz,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        int upper = flat ? color : shade(color, f);
        int lower = flat ? color : shade(color, f * GRADIENT);
        quad(m, e, vc, light, upper, lower, topY, nx, ny, nz, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4);
    }

    /** Vertices on the upper edge (y == topY) get the upper colour, the others the lower one. */
    private static void quad(Matrix4f m, PoseStack.Pose e, VertexConsumer vc, int light, int upper, int lower, float topY,
                             float nx, float ny, float nz,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vertex(m, e, vc, light, y1 == topY ? upper : lower, nx, ny, nz, x1, y1, z1);
        vertex(m, e, vc, light, y2 == topY ? upper : lower, nx, ny, nz, x2, y2, z2);
        vertex(m, e, vc, light, y3 == topY ? upper : lower, nx, ny, nz, x3, y3, z3);
        vertex(m, e, vc, light, y4 == topY ? upper : lower, nx, ny, nz, x4, y4, z4);
    }

    private static void vertex(Matrix4f m, PoseStack.Pose e, VertexConsumer vc, int light, int color,
                               float nx, float ny, float nz, float x, float y, float z) {
        vc.addVertex(m, x, y, z)
                .setColor(color)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(e, nx, ny, nz);
    }

    static int shade(int color, float f) {
        int a = (color >>> 24) & 0xFF;
        int r = Math.min(255, Math.round(((color >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((color >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((color & 0xFF) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
