package dev.onix.client.cosmetics;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;

/** Draws every non-cape cosmetic (head, face, neck, back, aura, pet) on the local player. */
public class CosmeticsFeatureRenderer extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    public CosmeticsFeatureRenderer(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
                       float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        if (!Cosmetics.appliesTo(player)) return;
        VertexConsumer vc = null;
        float time = player.age + tickDelta;
        boolean helmet = !player.getEquippedStack(EquipmentSlot.HEAD).isEmpty();
        boolean elytra = player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);

        for (Cosmetics.Slot slot : Cosmetics.Slot.values()) {
            if (slot == Cosmetics.Slot.CAPE) continue;                // the cape mixin handles capes
            if (slot == Cosmetics.Slot.BACK && elytra) continue;      // wings and backpacks would clip through the elytra
            CosmeticCatalog.Item item = Cosmetics.shown(slot);
            if (item == null) continue;
            CosmeticCatalog.Model model = CosmeticCatalog.model(item.model());
            if (model == null) continue;
            if (vc == null) vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(Cosmetics.WHITE));

            matrices.push();
            if (model.onHead()) {
                getContextModel().head.rotate(matrices);
                if (helmet && slot == Cosmetics.Slot.HEAD) matrices.translate(0, -1.2f / 16f, 0);
            } else {
                getContextModel().body.rotate(matrices);
            }
            float speed = slot == Cosmetics.Slot.BACK && player.isFallFlying() ? 4f : 1f;
            CosmeticModels.render(matrices, vc, light, model, CosmeticCatalog.palette(item.palette()), time, speed);
            matrices.pop();
        }
    }
}
