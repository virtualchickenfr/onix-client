package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

@Environment(EnvType.CLIENT)
public abstract class Model {
	protected final ModelPart root;
	protected final Function<ResourceLocation, RenderType> renderType;
	private final List<ModelPart> allParts;

	public Model(ModelPart modelPart, Function<ResourceLocation, RenderType> function) {
		this.root = modelPart;
		this.renderType = function;
		this.allParts = modelPart.getAllParts();
	}

	public final RenderType renderType(ResourceLocation resourceLocation) {
		return this.renderType.apply(resourceLocation);
	}

	public final void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int j, int k) {
		this.root().render(poseStack, vertexConsumer, i, j, k);
	}

	public final void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int j) {
		this.renderToBuffer(poseStack, vertexConsumer, i, j, -1);
	}

	public final ModelPart root() {
		return this.root;
	}

	public final List<ModelPart> allParts() {
		return this.allParts;
	}

	public final void resetPose() {
		for (ModelPart modelPart : this.allParts) {
			modelPart.resetPose();
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Simple extends Model {
		public Simple(ModelPart modelPart, Function<ResourceLocation, RenderType> function) {
			super(modelPart, function);
		}
	}
}
