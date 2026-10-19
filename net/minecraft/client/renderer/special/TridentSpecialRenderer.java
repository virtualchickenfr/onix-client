package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.TridentModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class TridentSpecialRenderer implements NoDataSpecialModelRenderer {
	private final TridentModel model;

	public TridentSpecialRenderer(TridentModel tridentModel) {
		this.model = tridentModel;
	}

	@Override
	public void render(ItemDisplayContext itemDisplayContext, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, boolean bl) {
		poseStack.pushPose();
		poseStack.scale(1.0F, -1.0F, -1.0F);
		VertexConsumer vertexConsumer = ItemRenderer.getFoilBuffer(multiBufferSource, this.model.renderType(TridentModel.TEXTURE), false, bl);
		this.model.renderToBuffer(poseStack, vertexConsumer, i, j);
		poseStack.popPose();
	}

	@Override
	public void getExtents(Set<Vector3f> set) {
		PoseStack poseStack = new PoseStack();
		poseStack.scale(1.0F, -1.0F, -1.0F);
		this.model.root().getExtentsForGui(poseStack, set);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked() implements SpecialModelRenderer.Unbaked {
		public static final MapCodec<TridentSpecialRenderer.Unbaked> MAP_CODEC = MapCodec.unit(new TridentSpecialRenderer.Unbaked());

		@Override
		public MapCodec<TridentSpecialRenderer.Unbaked> type() {
			return MAP_CODEC;
		}

		@Override
		public SpecialModelRenderer<?> bake(EntityModelSet entityModelSet) {
			return new TridentSpecialRenderer(new TridentModel(entityModelSet.bakeLayer(ModelLayers.TRIDENT)));
		}
	}
}
