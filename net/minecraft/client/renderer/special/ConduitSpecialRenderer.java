package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.ConduitRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class ConduitSpecialRenderer implements NoDataSpecialModelRenderer {
	private final ModelPart model;

	public ConduitSpecialRenderer(ModelPart modelPart) {
		this.model = modelPart;
	}

	@Override
	public void render(ItemDisplayContext itemDisplayContext, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, boolean bl) {
		VertexConsumer vertexConsumer = ConduitRenderer.SHELL_TEXTURE.buffer(multiBufferSource, RenderType::entitySolid);
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		this.model.render(poseStack, vertexConsumer, i, j);
		poseStack.popPose();
	}

	@Override
	public void getExtents(Set<Vector3f> set) {
		PoseStack poseStack = new PoseStack();
		poseStack.translate(0.5F, 0.5F, 0.5F);
		this.model.getExtentsForGui(poseStack, set);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked() implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<ConduitSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new ConduitSpecialRenderer.Unbaked());

		@Override
		public MapCodec<ConduitSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(EntityModelSet entityModelSet) {
			return new ConduitSpecialRenderer(entityModelSet.bakeLayer(ModelLayers.CONDUIT_SHELL));
		}
	}
}
