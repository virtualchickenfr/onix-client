package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.AdultAndBabyModelPair;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.CowRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.CowVariant;

@Environment(EnvType.CLIENT)
public class CowRenderer extends MobRenderer<Cow, CowRenderState, CowModel> {
	private final Map<CowVariant.ModelType, AdultAndBabyModelPair<CowModel>> models;

	public CowRenderer(EntityRendererProvider.Context context) {
		super(context, new CowModel(context.bakeLayer(ModelLayers.COW)), 0.7F);
		this.models = bakeModels(context);
	}

	private static Map<CowVariant.ModelType, AdultAndBabyModelPair<CowModel>> bakeModels(EntityRendererProvider.Context context) {
		return Maps.newEnumMap(
			Map.of(
				CowVariant.ModelType.NORMAL,
				new AdultAndBabyModelPair<>(new CowModel(context.bakeLayer(ModelLayers.COW)), new CowModel(context.bakeLayer(ModelLayers.COW_BABY))),
				CowVariant.ModelType.WARM,
				new AdultAndBabyModelPair<>(new CowModel(context.bakeLayer(ModelLayers.WARM_COW)), new CowModel(context.bakeLayer(ModelLayers.WARM_COW_BABY))),
				CowVariant.ModelType.COLD,
				new AdultAndBabyModelPair<>(new CowModel(context.bakeLayer(ModelLayers.COLD_COW)), new CowModel(context.bakeLayer(ModelLayers.COLD_COW_BABY)))
			)
		);
	}

	public ResourceLocation getTextureLocation(CowRenderState cowRenderState) {
		return cowRenderState.variant == null ? MissingTextureAtlasSprite.getLocation() : cowRenderState.variant.modelAndTexture().asset().texturePath();
	}

	public CowRenderState createRenderState() {
		return new CowRenderState();
	}

	public void extractRenderState(Cow cow, CowRenderState cowRenderState, float f) {
		super.extractRenderState(cow, cowRenderState, f);
		cowRenderState.variant = cow.getVariant().value();
	}

	public void render(CowRenderState cowRenderState, PoseStack poseStack, MultiBufferSource multiBufferSource, int i) {
		if (cowRenderState.variant != null) {
			this.model = this.models.get(cowRenderState.variant.modelAndTexture().model()).getModel(cowRenderState.isBaby);
			super.render(cowRenderState, poseStack, multiBufferSource, i);
		}
	}
}
