package dev.onix.client.cosmetics;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.player.PlayerModelPart;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Draws the Onix cape in 3D: a cloth with thickness that bends, flutters and folds ({@link CapeMesh}),
 * textured with the cape texture, plus a collar at the shoulders. Replaces the flat vanilla cape for
 * Onix capes (the vanilla one is switched off in AbstractClientPlayerEntityMixin).
 */
public class CapeRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    public CapeRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    /** True when this renderer draws the cape, so the vanilla cape must stay off. */
    public static boolean drawsCape() {
        return Cosmetics.shown(Cosmetics.Slot.CAPE) != null && !Cosmetics.capeLeftToCosmetica();
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!Cosmetics.appliesTo(player) || !drawsCape()) return;
        if (!player.isPartVisible(PlayerModelPart.CAPE)) return;
        if (player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) return;
        CosmeticCatalog.Item item = Cosmetics.shown(Cosmetics.Slot.CAPE);
        Identifier texture = CapeTextures.get(item);
        if (texture == null) return;

        // the same swing as the vanilla cape (CapeFeatureRenderer)
        double dx = MathHelper.lerp(tickDelta, player.prevCapeX, player.capeX) - MathHelper.lerp(tickDelta, player.prevX, player.getX());
        double dy = MathHelper.lerp(tickDelta, player.prevCapeY, player.capeY) - MathHelper.lerp(tickDelta, player.prevY, player.getY());
        double dz = MathHelper.lerp(tickDelta, player.prevCapeZ, player.capeZ) - MathHelper.lerp(tickDelta, player.prevZ, player.getZ());
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        double sin = MathHelper.sin(bodyYaw * MathHelper.RADIANS_PER_DEGREE);
        double cos = -MathHelper.cos(bodyYaw * MathHelper.RADIANS_PER_DEGREE);
        float lift = MathHelper.clamp((float) dy * 10f, -6f, 32f);
        float back = MathHelper.clamp((float) (dx * sin + dz * cos) * 100f, 0f, 150f);
        float side = MathHelper.clamp((float) (dx * cos - dz * sin) * 100f, -20f, 20f);
        float stride = MathHelper.lerp(tickDelta, player.prevStrideDistance, player.strideDistance);
        lift += MathHelper.sin(MathHelper.lerp(tickDelta, player.prevHorizontalSpeed, player.horizontalSpeed) * 6f) * 32f * stride;
        if (player.isInSneakingPose()) lift += 25f;

        matrices.push();
        matrices.translate(0f, 0f, 0.125f);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6f + back / 2f + lift));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(side / 2f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-side / 2f));
        float swing = MathHelper.clamp(back / 110f + Math.max(0f, lift) / 60f, 0f, 1f);
        draw(matrices, vertexConsumers, light, item, texture, player.age + tickDelta, swing);
        matrices.pop();
    }

    /** Draws cape and collar in cape space (see CapeMesh). */
    static void draw(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CosmeticCatalog.Item item, Identifier texture,
                     float time, float swing) {
        CapeMesh mesh = CapeMesh.build(time, swing);
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(texture));
        MatrixStack.Entry entry = matrices.peek();
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
        collar(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Cosmetics.WHITE)), light, item);
    }

    /** The collar the cape hangs from; richer for higher tiers. Shared with the launcher (capeCollar in cosrender.js). */
    private static void collar(MatrixStack m, VertexConsumer vc, int light, CosmeticCatalog.Item item) {
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

    private static void quad(VertexConsumer vc, MatrixStack.Entry e, int light, float[] a, float[] b, float[] c, float[] d,
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
        Matrix4f m = e.getPositionMatrix();
        vertex(vc, m, e, light, a, ua, va, nx, ny, nz);
        vertex(vc, m, e, light, b, ub, vb, nx, ny, nz);
        vertex(vc, m, e, light, c, uc, vc2, nx, ny, nz);
        vertex(vc, m, e, light, d, ud, vd, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, MatrixStack.Entry e, int light, float[] p, float u, float v, float nx, float ny, float nz) {
        vc.vertex(m, p[0] / 16f, p[1] / 16f, p[2] / 16f)
                .color(0xFFFFFFFF)
                .texture(u / 64f, v / 32f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(e, nx, ny, nz);
    }

    // ---------------------------------------------------------------- menu icon

    /** Draws a cape in 3D into a rectangle of the menu, seen from behind (extraYaw turns it). */
    public static void drawIcon(DrawContext ctx, CosmeticCatalog.Item item, int x, int y, int w, int h, float t, float extraYaw) {
        Identifier texture = CapeTextures.get(item);
        if (texture == null) return;
        // cape space spans about x -5.5..5.5, y -1..17, z 0..6; fit its height
        float scale = Math.min(w / 14f, h / 20f) * 16f;
        MatrixStack ms = ctx.getMatrices();
        ms.push();
        ms.translate(x + w / 2f, y + h / 2f, 150f);
        ms.scale(scale, scale, -scale);
        ms.translate(0f, -8f / 16f, 0f);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12f));
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(160f + extraYaw));
        ms.translate(0f, 0f, -3f / 16f);
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6f));
        draw(ms, ctx.getVertexConsumers(), CosmeticModels.FULL_BRIGHT, item, texture, t, 0.12f);
        ms.pop();
    }
}
