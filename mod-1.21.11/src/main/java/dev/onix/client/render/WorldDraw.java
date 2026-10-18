package dev.onix.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Matrix4f;

/** Line and box drawing in the world for 1.21.11 (line width is a vertex attribute there). */
public final class WorldDraw {
    public static final float LINE_WIDTH = 2f;

    private WorldDraw() {}

    public static Vec3 camera(WorldRenderContext context) {
        var state = context.worldState();
        if (state != null && state.cameraRenderState != null && state.cameraRenderState.pos != null) return state.cameraRenderState.pos;
        return Minecraft.getInstance().gameRenderer.getMainCamera().position();
    }

    public static float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    private static int argb(float r, float g, float b, float a) {
        return (Math.round(a * 255) & 0xFF) << 24 | (Math.round(r * 255) & 0xFF) << 16 | (Math.round(g * 255) & 0xFF) << 8 | (Math.round(b * 255) & 0xFF);
    }

    /** Box outline; the box is already relative to the camera. */
    public static void lineBox(PoseStack matrices, VertexConsumer lines, AABB box, float r, float g, float b, float a) {
        ShapeRenderer.renderShape(matrices, lines, Shapes.create(box), 0, 0, 0, argb(r, g, b, a), LINE_WIDTH);
    }

    /** Filled box (debug quads); the box is already relative to the camera. */
    public static void filledBox(PoseStack matrices, VertexConsumer quads, double x1, double y1, double z1, double x2, double y2, double z2,
                                 float r, float g, float b, float a) {
        Matrix4f m = matrices.last().pose();
        float ax = (float) x1, ay = (float) y1, az = (float) z1, bx = (float) x2, by = (float) y2, bz = (float) z2;
        int c = argb(r, g, b, a);
        // six faces, four vertices each
        quad(quads, m, c, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz);
        quad(quads, m, c, ax, by, az, ax, by, bz, bx, by, bz, bx, by, az);
        quad(quads, m, c, ax, ay, az, ax, by, az, bx, by, az, bx, ay, az);
        quad(quads, m, c, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz);
        quad(quads, m, c, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az);
        quad(quads, m, c, bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, int c, float... p) {
        for (int i = 0; i < 12; i += 3) vc.addVertex(m, p[i], p[i + 1], p[i + 2]).setColor(c);
    }
}
