package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.SnowGolemModel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.SnowGolemRenderState;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

@Environment(EnvType.CLIENT)
public class SnowGolemHeadLayer extends RenderLayer<SnowGolemRenderState, SnowGolemModel> {
	private final BlockRenderDispatcher blockRenderer;

	public SnowGolemHeadLayer(RenderLayerParent<SnowGolemRenderState, SnowGolemModel> renderLayerParent, BlockRenderDispatcher blockRenderDispatcher) {
		super(renderLayerParent);
		this.blockRenderer = blockRenderDispatcher;
	}

	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, SnowGolemRenderState snowGolemRenderState, float f, float g) {
		if (snowGolemRenderState.hasPumpkin) {
			if (!snowGolemRenderState.isInvisible || snowGolemRenderState.appearsGlowing) {
				poseStack.pushPose();
				this.getParentModel().getHead().translateAndRotate(poseStack);
				float h = 0.625F;
				poseStack.translate(0.0F, -0.34375F, 0.0F);
				poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
				poseStack.scale(0.625F, -0.625F, -0.625F);
				BlockState blockState = Blocks.CARVED_PUMPKIN.defaultBlockState();
				BlockStateModel blockStateModel = this.blockRenderer.getBlockModel(blockState);
				int j = LivingEntityRenderer.getOverlayCoords(snowGolemRenderState, 0.0F);
				poseStack.translate(-0.5F, -0.5F, -0.5F);
				VertexConsumer vertexConsumer = snowGolemRenderState.appearsGlowing && snowGolemRenderState.isInvisible
					? multiBufferSource.getBuffer(RenderType.outline(TextureAtlas.LOCATION_BLOCKS))
					: multiBufferSource.getBuffer(ItemBlockRenderTypes.getRenderType(blockState));
				ModelBlockRenderer.renderModel(poseStack.last(), vertexConsumer, blockStateModel, 0.0F, 0.0F, 0.0F, i, j);
				poseStack.popPose();
			}
		}
	}
}
