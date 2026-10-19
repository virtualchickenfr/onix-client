package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.DolphinModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.DolphinRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public class DolphinCarryingItemLayer extends RenderLayer<DolphinRenderState, DolphinModel> {
	public DolphinCarryingItemLayer(RenderLayerParent<DolphinRenderState, DolphinModel> renderLayerParent) {
		super(renderLayerParent);
	}

	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, DolphinRenderState dolphinRenderState, float f, float g) {
		ItemStackRenderState itemStackRenderState = dolphinRenderState.heldItem;
		if (!itemStackRenderState.isEmpty()) {
			poseStack.pushPose();
			float h = 1.0F;
			float j = -1.0F;
			float k = Mth.abs(dolphinRenderState.xRot) / 60.0F;
			if (dolphinRenderState.xRot < 0.0F) {
				poseStack.translate(0.0F, 1.0F - k * 0.5F, -1.0F + k * 0.5F);
			} else {
				poseStack.translate(0.0F, 1.0F + k * 0.8F, -1.0F + k * 0.2F);
			}

			itemStackRenderState.render(poseStack, multiBufferSource, i, OverlayTexture.NO_OVERLAY);
			poseStack.popPose();
		}
	}
}
