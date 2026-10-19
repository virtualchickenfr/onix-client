package net.minecraft.client.render.block.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.StructureBoxRendering;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.shape.BitSetVoxelSet;
import net.minecraft.util.shape.VoxelSet;
import net.minecraft.world.BlockView;

@Environment(EnvType.CLIENT)
public class StructureBlockBlockEntityRenderer<T extends BlockEntity & StructureBoxRendering> implements BlockEntityRenderer<T> {
	public StructureBlockBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
	}

	@Override
	public void render(T entity, float tickProgress, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, Vec3d cameraPos) {
		if (MinecraftClient.getInstance().player.isCreativeLevelTwoOp() || MinecraftClient.getInstance().player.isSpectator()) {
			StructureBoxRendering.RenderMode renderMode = entity.getRenderMode();
			if (renderMode != StructureBoxRendering.RenderMode.NONE) {
				StructureBoxRendering.StructureBox structureBox = entity.getStructureBox();
				BlockPos blockPos = structureBox.localPos();
				Vec3i vec3i = structureBox.size();
				if (vec3i.getX() >= 1 && vec3i.getY() >= 1 && vec3i.getZ() >= 1) {
					float f = 1.0F;
					float g = 0.9F;
					float h = 0.5F;
					VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
					BlockPos blockPos2 = blockPos.add(vec3i);
					VertexRendering.drawBox(
						matrices,
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
					if (renderMode == StructureBoxRendering.RenderMode.BOX_AND_INVISIBLE_BLOCKS && entity.getWorld() != null) {
						this.renderInvisibleBlocks(entity, entity.getWorld(), blockPos, vec3i, vertexConsumers, matrices);
					}
				}
			}
		}
	}

	private void renderInvisibleBlocks(T entity, BlockView world, BlockPos pos, Vec3i size, VertexConsumerProvider vertexConsumers, MatrixStack matrices) {
		VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getLines());
		BlockPos blockPos = entity.getPos();
		BlockPos blockPos2 = blockPos.add(pos);

		for (BlockPos blockPos3 : BlockPos.iterate(blockPos2, blockPos2.add(size).add(-1, -1, -1))) {
			BlockState blockState = world.getBlockState(blockPos3);
			boolean bl = blockState.isAir();
			boolean bl2 = blockState.isOf(Blocks.STRUCTURE_VOID);
			boolean bl3 = blockState.isOf(Blocks.BARRIER);
			boolean bl4 = blockState.isOf(Blocks.LIGHT);
			boolean bl5 = bl2 || bl3 || bl4;
			if (bl || bl5) {
				float f = bl ? 0.05F : 0.0F;
				double d = blockPos3.getX() - blockPos.getX() + 0.45F - f;
				double e = blockPos3.getY() - blockPos.getY() + 0.45F - f;
				double g = blockPos3.getZ() - blockPos.getZ() + 0.45F - f;
				double h = blockPos3.getX() - blockPos.getX() + 0.55F + f;
				double i = blockPos3.getY() - blockPos.getY() + 0.55F + f;
				double j = blockPos3.getZ() - blockPos.getZ() + 0.55F + f;
				if (bl) {
					VertexRendering.drawBox(matrices, vertexConsumer, d, e, g, h, i, j, 0.5F, 0.5F, 1.0F, 1.0F, 0.5F, 0.5F, 1.0F);
				} else if (bl2) {
					VertexRendering.drawBox(matrices, vertexConsumer, d, e, g, h, i, j, 1.0F, 0.75F, 0.75F, 1.0F, 1.0F, 0.75F, 0.75F);
				} else if (bl3) {
					VertexRendering.drawBox(matrices, vertexConsumer, d, e, g, h, i, j, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F);
				} else if (bl4) {
					VertexRendering.drawBox(matrices, vertexConsumer, d, e, g, h, i, j, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F);
				}
			}
		}
	}

	private void renderStructureVoids(T entity, BlockPos pos, Vec3i size, VertexConsumer vertexConsumer, MatrixStack matrices) {
		BlockView blockView = entity.getWorld();
		if (blockView != null) {
			BlockPos blockPos = entity.getPos();
			VoxelSet voxelSet = new BitSetVoxelSet(size.getX(), size.getY(), size.getZ());

			for (BlockPos blockPos2 : BlockPos.iterate(pos, pos.add(size).add(-1, -1, -1))) {
				if (blockView.getBlockState(blockPos2).isOf(Blocks.STRUCTURE_VOID)) {
					voxelSet.set(blockPos2.getX() - pos.getX(), blockPos2.getY() - pos.getY(), blockPos2.getZ() - pos.getZ());
				}
			}

			voxelSet.forEachDirection((direction, x, y, z) -> {
				float f = 0.48F;
				float g = x + pos.getX() - blockPos.getX() + 0.5F - 0.48F;
				float h = y + pos.getY() - blockPos.getY() + 0.5F - 0.48F;
				float i = z + pos.getZ() - blockPos.getZ() + 0.5F - 0.48F;
				float j = x + pos.getX() - blockPos.getX() + 0.5F + 0.48F;
				float k = y + pos.getY() - blockPos.getY() + 0.5F + 0.48F;
				float l = z + pos.getZ() - blockPos.getZ() + 0.5F + 0.48F;
				VertexRendering.drawSide(matrices, vertexConsumer, direction, g, h, i, j, k, l, 0.75F, 0.75F, 1.0F, 0.2F);
			});
		}
	}

	@Override
	public boolean rendersOutsideBoundingBox(T blockEntity) {
		return true;
	}

	@Override
	public int getRenderDistance() {
		return 96;
	}
}
