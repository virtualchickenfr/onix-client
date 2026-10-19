package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BoundingBoxRenderable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;

@Environment(EnvType.CLIENT)
public class BlockEntityWithBoundingBoxRenderer<T extends BlockEntity & BoundingBoxRenderable> implements BlockEntityRenderer<T> {
	public BlockEntityWithBoundingBoxRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(T blockEntity, float f, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, Vec3 vec3) {
		if (Minecraft.getInstance().player.canUseGameMasterBlocks() || Minecraft.getInstance().player.isSpectator()) {
			BoundingBoxRenderable.Mode mode = blockEntity.renderMode();
			if (mode != BoundingBoxRenderable.Mode.NONE) {
				BoundingBoxRenderable.RenderableBox renderableBox = blockEntity.getRenderableBox();
				BlockPos blockPos = renderableBox.localPos();
				Vec3i vec3i = renderableBox.size();
				if (vec3i.getX() >= 1 && vec3i.getY() >= 1 && vec3i.getZ() >= 1) {
					float g = 1.0F;
					float h = 0.9F;
					float k = 0.5F;
					VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.lines());
					BlockPos blockPos2 = blockPos.offset(vec3i);
					ShapeRenderer.renderLineBox(
						poseStack,
						vertexConsumer,
						blockPos.getX(),
						blockPos.getY(),
						blockPos.getZ(),
						blockPos2.getX(),
						blockPos2.getY(),
						blockPos2.getZ(),
						0.9F,
						0.9F,
						0.9F,
						1.0F,
						0.5F,
						0.5F,
						0.5F
					);
					if (mode == BoundingBoxRenderable.Mode.BOX_AND_INVISIBLE_BLOCKS && blockEntity.getLevel() != null) {
						this.renderInvisibleBlocks(blockEntity, blockEntity.getLevel(), blockPos, vec3i, multiBufferSource, poseStack);
					}
				}
			}
		}
	}

	private void renderInvisibleBlocks(
		T blockEntity, BlockGetter blockGetter, BlockPos blockPos, Vec3i vec3i, MultiBufferSource multiBufferSource, PoseStack poseStack
	) {
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.lines());
		BlockPos blockPos2 = blockEntity.getBlockPos();
		BlockPos blockPos3 = blockPos2.offset(blockPos);

		for (BlockPos blockPos4 : BlockPos.betweenClosed(blockPos3, blockPos3.offset(vec3i).offset(-1, -1, -1))) {
			BlockState blockState = blockGetter.getBlockState(blockPos4);
			boolean bl = blockState.isAir();
			boolean bl2 = blockState.is(Blocks.STRUCTURE_VOID);
			boolean bl3 = blockState.is(Blocks.BARRIER);
			boolean bl4 = blockState.is(Blocks.LIGHT);
			boolean bl5 = bl2 || bl3 || bl4;
			if (bl || bl5) {
				float f = bl ? 0.05F : 0.0F;
				double d = blockPos4.getX() - blockPos2.getX() + 0.45F - f;
				double e = blockPos4.getY() - blockPos2.getY() + 0.45F - f;
				double g = blockPos4.getZ() - blockPos2.getZ() + 0.45F - f;
				double h = blockPos4.getX() - blockPos2.getX() + 0.55F + f;
				double i = blockPos4.getY() - blockPos2.getY() + 0.55F + f;
				double j = blockPos4.getZ() - blockPos2.getZ() + 0.55F + f;
				if (bl) {
					ShapeRenderer.renderLineBox(poseStack, vertexConsumer, d, e, g, h, i, j, 0.5F, 0.5F, 1.0F, 1.0F, 0.5F, 0.5F, 1.0F);
				} else if (bl2) {
					ShapeRenderer.renderLineBox(poseStack, vertexConsumer, d, e, g, h, i, j, 1.0F, 0.75F, 0.75F, 1.0F, 1.0F, 0.75F, 0.75F);
				} else if (bl3) {
					ShapeRenderer.renderLineBox(poseStack, vertexConsumer, d, e, g, h, i, j, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F);
				} else if (bl4) {
					ShapeRenderer.renderLineBox(poseStack, vertexConsumer, d, e, g, h, i, j, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F);
				}
			}
		}
	}

	private void renderStructureVoids(T blockEntity, BlockPos blockPos, Vec3i vec3i, VertexConsumer vertexConsumer, PoseStack poseStack) {
		BlockGetter blockGetter = blockEntity.getLevel();
		if (blockGetter != null) {
			BlockPos blockPos2 = blockEntity.getBlockPos();
			DiscreteVoxelShape discreteVoxelShape = new BitSetDiscreteVoxelShape(vec3i.getX(), vec3i.getY(), vec3i.getZ());

			for (BlockPos blockPos3 : BlockPos.betweenClosed(blockPos, blockPos.offset(vec3i).offset(-1, -1, -1))) {
				if (blockGetter.getBlockState(blockPos3).is(Blocks.STRUCTURE_VOID)) {
					discreteVoxelShape.fill(blockPos3.getX() - blockPos.getX(), blockPos3.getY() - blockPos.getY(), blockPos3.getZ() - blockPos.getZ());
				}
			}

			discreteVoxelShape.forAllFaces((direction, i, j, k) -> {
				float f = 0.48F;
				float g = i + blockPos.getX() - blockPos2.getX() + 0.5F - 0.48F;
				float h = j + blockPos.getY() - blockPos2.getY() + 0.5F - 0.48F;
				float l = k + blockPos.getZ() - blockPos2.getZ() + 0.5F - 0.48F;
				float m = i + blockPos.getX() - blockPos2.getX() + 0.5F + 0.48F;
				float n = j + blockPos.getY() - blockPos2.getY() + 0.5F + 0.48F;
				float o = k + blockPos.getZ() - blockPos2.getZ() + 0.5F + 0.48F;
				ShapeRenderer.renderFace(poseStack, vertexConsumer, direction, g, h, l, m, n, o, 0.75F, 0.75F, 1.0F, 0.2F);
			});
		}
	}

	@Override
	public boolean shouldRenderOffScreen() {
		return true;
	}

	@Override
	public int getViewDistance() {
		return 96;
	}
}
