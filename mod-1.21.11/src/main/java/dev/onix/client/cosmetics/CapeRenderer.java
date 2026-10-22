package dev.onix.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.onix.client.render.RecordingBuffers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;

/**
 * Draws the Onix cape in 3D: a cloth with thickness that bends, flutters and folds ({@link CapeMesh}),
 * textured with the cape texture, plus a collar at the shoulders. Replaces the flat vanilla cape for
 * Onix capes (the vanilla one is switched off in AbstractClientPlayerEntityMixin).
 */
public class CapeRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public CapeRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
        super(context);
    }

    /** True when this renderer draws the cape, so the vanilla cape must stay off. */
    public static boolean drawsCape() {
        return Cosmetics.shown(Cosmetics.Slot.CAPE) != null;
    }

    @Override
    public void submit(PoseStack matrices, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (!Cosmetics.appliesTo(state) || !drawsCape()) return;
        if (!state.showCape || state.chestEquipment.is(Items.ELYTRA)) return;
        CosmeticCatalog.Item item = Cosmetics.shown(Cosmetics.Slot.CAPE);
        Identifier texture = CapeTextures.get(item);
        if (texture == null) return;

        // the same swing as the vanilla cape, from the values the game already worked out for this frame
        float lift = state.capeFlap;
        float back = state.capeLean;
        float side = state.capeLean2;
        if (state.isCrouching) lift += 25f;

        matrices.pushPose();
        matrices.translate(0f, 0f, 0.125f);
        matrices.mulPose(Axis.XP.rotationDegrees(6f + back / 2f + lift));
        matrices.mulPose(Axis.ZP.rotationDegrees(side / 2f));
        matrices.mulPose(Axis.YP.rotationDegrees(-side / 2f));
        float swing = Mth.clamp(back / 110f + Math.max(0f, lift) / 60f, 0f, 1f);
        RecordingBuffers buffers = new RecordingBuffers();
        draw(matrices, buffers, light, item, texture, state.ageInTicks, swing);
        buffers.submit(collector);
        matrices.popPose();
    }

    /** Draws cape and collar in cape space (see CapeMesh). */
    static void draw(PoseStack matrices, MultiBufferSource vertexConsumers, int light, CosmeticCatalog.Item item, Identifier texture,
                     float time, float swing) {
        CapeMesh mesh = CapeMesh.build(time, swing);
        VertexConsumer vc = vertexConsumers.getBuffer(RenderTypes.entityCutoutNoCull(texture));
        PoseStack.Pose entry = matrices.last();
        int nu = CapeMesh.NU;
        int nv = CapeMesh.NV;
        for (int j = 0; j < nv; j++) {
            float v1 = 1f + 16f * j / nv;
            float v2 = 1f + 16f * (j + 1) / nv;
            for (int i = 0; i < nu; i++) {
                float u1 = 1f + 10f * i / nu;
                float u2 = 1f + 10f * (i + 1) / nu;
                // outside (seen from behind) and inside
                quad(vc, entry, light, mesh.outer[j][i], mesh.outer[j + 1][i], mesh.outer[j + 1][i + 1], mesh.outer[j][i + 1], u1, v1, u1, v2, u2, v2, u2, v1);
                float iu1 = 12f + 10f * i / nu;
                float iu2 = 12f + 10f * (i + 1) / nu;
                quad(vc, entry, light, mesh.inner[j][i + 1], mesh.inner[j + 1][i + 1], mesh.inner[j + 1][i], mesh.inner[j][i], iu2, v1, iu2, v2, iu1, v2, iu1, v1);
            }
            // side edges
            quad(vc, entry, light, mesh.inner[j][0], mesh.inner[j + 1][0], mesh.outer[j + 1][0], mesh.outer[j][0], 0f, v1, 0f, v2, 1f, v2, 1f, v1);
            quad(vc, entry, light, mesh.outer[j][nu], mesh.outer[j + 1][nu], mesh.inner[j + 1][nu], mesh.inner[j][nu], 11f, v1, 11f, v2, 12f, v2, 12f, v1);
        }
        for (int i = 0; i < nu; i++) {   // bottom hem
            float u1 = 11f + 10f * i / nu;
            float u2 = 11f + 10f * (i + 1) / nu;
            quad(vc, entry, light, mesh.outer[nv][i], mesh.inner[nv][i], mesh.inner[nv][i + 1], mesh.outer[nv][i + 1], u1, 0f, u1, 1f, u2, 1f, u2, 0f);
        }
        collar(matrices, vertexConsumers.getBuffer(RenderTypes.entityCutoutNoCull(Cosmetics.WHITE)), light, item);
    }

    /** The collar the cape hangs from; richer for higher tiers. Shared with the launcher (capeCollar in cosrender.js). */
    private static void collar(PoseStack m, VertexConsumer vc, int light, CosmeticCatalog.Item item) {
        int[] p = CosmeticCatalog.palette(item.palette());
        int tier = item.tier().ordinal();
        int band = tier >= 2 ? 0xFFF2C14E : p[Math.min(5, p.length - 1)];
        int trim = tier >= 2 ? 0xFFC8902A : p[Math.min(9, p.length - 1)];
        ModelBox.cube(m, vc, light, -5.4f, -0.9f, -0.2f, 5.4f, 0.5f, 1.35f, band);
        ModelBox.cube(m, vc, light, -5.5f, 0.3f, -0.25f, 5.5f, 0.75f, 1.45f, trim);
        if (tier >= 1) {
            int rivet = p[Math.min(8, p.length - 1)];
            ModelBox.cube(m, vc, light, 3.4f, -0.5f, 1.3f, 4.2f, 0.2f, 1.55f, rivet);
            ModelBox.cube(m, vc, light, -4.2f, -0.5f, 1.3f, -3.4f, 0.2f, 1.55f, rivet);
        }
        if (tier >= 2) {
            boolean glow = tier >= 3;
            ModelBox.cube(m, vc, glow ? CosmeticModels.FULL_BRIGHT : light, -0.75f, -0.75f, 1.3f, 0.75f, 0.6f, 1.75f, p[Math.min(2, p.length - 1)], glow);
            ModelBox.cube(m, vc, glow ? CosmeticModels.FULL_BRIGHT : light, -0.35f, -0.45f, 1.7f, 0.35f, 0.25f, 1.85f, 0xFFFFFFFF, glow);
        }
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose e, int light, float[] a, float[] b, float[] c, float[] d,
                             float ua, float va, float ub, float vb, float uc, float vc2, float ud, float vd) {
        // normal from the two diagonals
        float ex = c[0] - a[0], ey = c[1] - a[1], ez = c[2] - a[2];
        float fx = d[0] - b[0], fy = d[1] - b[1], fz = d[2] - b[2];
        float nx = ey * fz - ez * fy;
        float ny = ez * fx - ex * fz;
        float nz = ex * fy - ey * fx;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-6f) return;
        nx /= len;
        ny /= len;
        nz /= len;
        Matrix4f m = e.pose();
        vertex(vc, m, e, light, a, ua, va, nx, ny, nz);
        vertex(vc, m, e, light, b, ub, vb, nx, ny, nz);
        vertex(vc, m, e, light, c, uc, vc2, nx, ny, nz);
        vertex(vc, m, e, light, d, ud, vd, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, PoseStack.Pose e, int light, float[] p, float u, float v, float nx, float ny, float nz) {
        vc.addVertex(m, p[0] / 16f, p[1] / 16f, p[2] / 16f)
                .setColor(0xFFFFFFFF)
                .setUv(u / 64f, v / 32f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(e, nx, ny, nz);
    }

    // ---------------------------------------------------------------- menu icon

    /** Draws a cape in 3D into a rectangle of the menu, seen from behind (extraYaw turns it). */
    public static void drawIcon(GuiGraphics ctx, CosmeticCatalog.Item item, int x, int y, int w, int h, float t, float extraYaw) {
        if (CapeTextures.get(item) == null) return;
        CosmeticIcons.submit(ctx, item, true, x, y, w, h, t, extraYaw);
    }

    /** The 3D part of {@link #drawIcon}, drawn into the icon texture; the origin is the centre of the rectangle. */
    static void renderIcon(PoseStack ms, MultiBufferSource buffers, CosmeticCatalog.Item item, int w, int h, float t, float extraYaw) {
        Identifier texture = CapeTextures.get(item);
        if (texture == null) return;
        // cape space spans about x -5.5..5.5, y -1..17, z 0..6; fit its height
        float scale = Math.min(w / 14f, h / 20f) * 16f;
        ms.pushPose();
        ms.scale(scale, scale, scale);
        ms.translate(0f, -8f / 16f, 0f);
        ms.mulPose(Axis.XP.rotationDegrees(-12f));
        ms.mulPose(Axis.YP.rotationDegrees(160f + extraYaw));
        ms.translate(0f, 0f, -3f / 16f);
        ms.mulPose(Axis.XP.rotationDegrees(6f));
        draw(ms, buffers, CosmeticModels.FULL_BRIGHT, item, texture, t, 0.12f);
        ms.popPose();
    }
}
