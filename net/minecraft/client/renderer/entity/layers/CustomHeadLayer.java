package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.SkullModelBase;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.SkullBlockRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.SkullBlock;

@Environment(EnvType.CLIENT)
public class CustomHeadLayer<S extends LivingEntityRenderState, M extends EntityModel<S> & HeadedModel> extends RenderLayer<S, M> {
	private static final float ITEM_SCALE = 0.625F;
	private static final float SKULL_SCALE = 1.1875F;
	private final CustomHeadLayer.Transforms transforms;
	private final Function<SkullBlock.Type, SkullModelBase> skullModels;

	public CustomHeadLayer(RenderLayerParent<S, M> renderLayerParent, EntityModelSet entityModelSet) {
		this(renderLayerParent, entityModelSet, CustomHeadLayer.Transforms.DEFAULT);
	}

	public CustomHeadLayer(RenderLayerParent<S, M> renderLayerParent, EntityModelSet entityModelSet, CustomHeadLayer.Transforms transforms) {
		super(renderLayerParent);
		this.transforms = transforms;
		this.skullModels = Util.memoize(type -> SkullBlockRenderer.createModel(entityModelSet, type));
	}

	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, S livingEntityRenderState, float f, float g) {
		if (!livingEntityRenderState.headItem.isEmpty() || livingEntityRenderState.wornHeadType != null) {
			poseStack.pushPose();
			poseStack.scale(this.transforms.horizontalScale(), 1.0F, this.transforms.horizontalScale());
			M entityModel = this.getParentModel();
			entityModel.root().translateAndRotate(poseStack);
			entityModel.getHead().translateAndRotate(poseStack);
			if (livingEntityRenderState.wornHeadType != null) {
				poseStack.translate(0.0F, this.transforms.skullYOffset(), 0.0F);
				poseStack.scale(1.1875F, -1.1875F, -1.1875F);
				poseStack.translate(-0.5, 0.0, -0.5);
				SkullBlock.Type type = livingEntityRenderState.wornHeadType;
				SkullModelBase skullModelBase = this.skullModels.apply(type);
				RenderType renderType = SkullBlockRenderer.getRenderType(type, livingEntityRenderState.wornHeadProfile);
				SkullBlockRenderer.renderSkull(null, 180.0F, livingEntityRenderState.wornHeadAnimationPos, poseStack, multiBufferSource, i, skullModelBase, renderType);
			} else {
				translateToHead(poseStack, this.transforms);
				livingEntityRenderState.headItem.render(poseStack, multiBufferSource, i, OverlayTexture.NO_OVERLAY);
			}

			poseStack.popPose();
		}
	}

	public static void translateToHead(PoseStack poseStack, CustomHeadLayer.Transforms transforms) {
		poseStack.translate(0.0F, -0.25F + transforms.yOffset(), 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
		poseStack.scale(0.625F, -0.625F, -0.625F);
	}

	@Environment(EnvType.CLIENT)
	public record Transforms(float yOffset, float skullYOffset, float horizontalScale) {
		public static final CustomHeadLayer.Transforms DEFAULT = new CustomHeadLayer.Transforms(0.0F, 0.0F, 1.0F);
	}
}
