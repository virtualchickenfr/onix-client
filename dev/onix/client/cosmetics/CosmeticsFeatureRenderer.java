package dev.onix.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

/** Draws every non-cape cosmetic (head, face, neck, back, aura, pet) on the local player. */
public class CosmeticsFeatureRenderer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public CosmeticsFeatureRenderer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> context) {
        super(context);
    }

    @Override
    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, int light, AbstractClientPlayer player,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!Cosmetics.appliesTo(player)) return;
        VertexConsumer vc = null;
        float time = player.tickCount + tickDelta;
        boolean helmet = !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        boolean elytra = player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);

        for (Cosmetics.Slot slot : Cosmetics.Slot.values()) {
            if (slot == Cosmetics.Slot.CAPE) continue;                // the cape mixin handles capes
            if (slot == Cosmetics.Slot.BACK && elytra) continue;      // wings and backpacks would clip through the elytra
            CosmeticCatalog.Item item = Cosmetics.shown(slot);
            if (item == null) continue;
            CosmeticCatalog.Model model = CosmeticCatalog.model(item.model());
            if (model == null) continue;
            if (vc == null) vc = vertexConsumers.getBuffer(RenderType.entityCutoutNoCull(Cosmetics.WHITE));

            matrices.pushPose();
            if (model.onHead()) {
                getParentModel().head.translateAndRotate(matrices);
                if (helmet && slot == Cosmetics.Slot.HEAD) matrices.translate(0, -1.2f / 16f, 0);
            } else {
                getParentModel().body.translateAndRotate(matrices);
            }
            float speed = slot == Cosmetics.Slot.BACK && player.isFallFlying() ? 4f : 1f;
            CosmeticModels.render(matrices, vc, light, model, CosmeticCatalog.palette(item.palette()), time, speed);
            matrices.popPose();
        }
    }
}
