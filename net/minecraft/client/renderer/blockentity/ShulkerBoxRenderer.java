package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class ShulkerBoxRenderer implements BlockEntityRenderer<ShulkerBoxBlockEntity> {
	private final ShulkerBoxRenderer.ShulkerBoxModel model;

	public ShulkerBoxRenderer(BlockEntityRendererProvider.Context context) {
		this(context.getModelSet());
	}

	public ShulkerBoxRenderer(EntityModelSet entityModelSet) {
		this.model = new ShulkerBoxRenderer.ShulkerBoxModel(entityModelSet.bakeLayer(ModelLayers.SHULKER_BOX));
	}

	public void render(ShulkerBoxBlockEntity shulkerBoxBlockEntity, float f, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, Vec3 vec3) {
		Direction direction = shulkerBoxBlockEntity.getBlockState().getValueOrElse(ShulkerBoxBlock.FACING, Direction.UP);
		DyeColor dyeColor = shulkerBoxBlockEntity.getColor();
		Material material;
		if (dyeColor == null) {
			material = Sheets.DEFAULT_SHULKER_TEXTURE_LOCATION;
		} else {
			material = Sheets.getShulkerBoxMaterial(dyeColor);
		}

		float g = shulkerBoxBlockEntity.getProgress(f);
		this.render(poseStack, multiBufferSource, i, j, direction, g, material);
	}

	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, Direction direction, float f, Material material) {
		poseStack.pushPose();
		this.prepareModel(poseStack, direction, f);
		VertexConsumer vertexConsumer = material.buffer(multiBufferSource, this.model::renderType);
		this.model.renderToBuffer(poseStack, vertexConsumer, i, j);
		poseStack.popPose();
	}

	private void prepareModel(PoseStack poseStack, Direction direction, float f) {
		poseStack.translate(0.5F, 0.5F, 0.5F);
		float g = 0.9995F;
		poseStack.scale(0.9995F, 0.9995F, 0.9995F);
		poseStack.mulPose(direction.getRotation());
		poseStack.scale(1.0F, -1.0F, -1.0F);
		poseStack.translate(0.0F, -1.0F, 0.0F);
		this.model.animate(f);
	}

	public void getExtents(Direction direction, float f, Set<Vector3f> set) {
		PoseStack poseStack = new PoseStack();
		this.prepareModel(poseStack, direction, f);
		this.model.root().getExtentsForGui(poseStack, set);
	}

	@Environment(EnvType.CLIENT)
	static class ShulkerBoxModel extends Model {
		private final ModelPart lid;

		public ShulkerBoxModel(ModelPart modelPart) {
			super(modelPart, RenderType::entityCutoutNoCull);
			this.lid = modelPart.getChild("lid");
		}

		public void animate(float f) {
			this.lid.setPos(0.0F, 24.0F - f * 0.5F * 16.0F, 0.0F);
			this.lid.yRot = 270.0F * f * (float) (Math.PI / 180.0);
		}
	}
}
