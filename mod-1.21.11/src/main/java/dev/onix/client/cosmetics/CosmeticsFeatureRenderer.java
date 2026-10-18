package dev.onix.client.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.onix.client.render.RecordingBuffers;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.item.Items;

/** Draws every non-cape cosmetic (head, face, neck, back, aura, pet) on the local player. */
public class CosmeticsFeatureRenderer extends RenderLayer<AvatarRenderState, PlayerModel> {
    public CosmeticsFeatureRenderer(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
        super(context);
    }

    @Override
    public void submit(PoseStack matrices, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (!Cosmetics.appliesTo(state)) return;
        RecordingBuffers buffers = null;
        VertexConsumer vc = null;
        float time = state.ageInTicks;
        boolean helmet = !state.headEquipment.isEmpty();
        boolean elytra = state.chestEquipment.is(Items.ELYTRA);

        for (Cosmetics.Slot slot : Cosmetics.Slot.values()) {
            if (slot == Cosmetics.Slot.CAPE) continue;                // the cape layer handles capes
            if (slot == Cosmetics.Slot.BACK && elytra) continue;      // wings and backpacks would clip through the elytra
            CosmeticCatalog.Item item = Cosmetics.shown(slot);
            if (item == null) continue;
            CosmeticCatalog.Model model = CosmeticCatalog.model(item.model());
            if (model == null) continue;
            if (vc == null) {
                buffers = new RecordingBuffers();
                vc = buffers.getBuffer(RenderTypes.entityCutoutNoCull(Cosmetics.WHITE));
            }

            matrices.pushPose();
            if (model.onHead()) {
                getParentModel().head.translateAndRotate(matrices);
                if (helmet && slot == Cosmetics.Slot.HEAD) matrices.translate(0, -1.2f / 16f, 0);
            } else {
                getParentModel().body.translateAndRotate(matrices);
            }
            float speed = slot == Cosmetics.Slot.BACK && state.isFallFlying ? 4f : 1f;
            CosmeticModels.render(matrices, vc, light, model, CosmeticCatalog.palette(item.palette()), time, speed);
            matrices.popPose();
        }
        if (buffers != null) buffers.submit(collector);
    }
}
