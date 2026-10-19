package net.minecraft.client.render.entity.feature;

import com.google.common.collect.Maps;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.HorseEntityModel;
import net.minecraft.client.render.entity.state.HorseEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.passive.HorseMarking;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class HorseMarkingFeatureRenderer extends FeatureRenderer<HorseEntityRenderState, HorseEntityModel> {
	private static final Identifier INVISIBLE_ID = Identifier.ofVanilla("invisible");
	private static final Map<HorseMarking, Identifier> TEXTURES = Maps.newEnumMap(
		Map.of(
			HorseMarking.NONE,
			INVISIBLE_ID,
			HorseMarking.WHITE,
			Identifier.ofVanilla("textures/entity/horse/horse_markings_white.png"),
			HorseMarking.WHITE_FIELD,
			Identifier.ofVanilla("textures/entity/horse/horse_markings_whitefield.png"),
			HorseMarking.WHITE_DOTS,
			Identifier.ofVanilla("textures/entity/horse/horse_markings_whitedots.png"),
			HorseMarking.BLACK_DOTS,
			Identifier.ofVanilla("textures/entity/horse/horse_markings_blackdots.png")
		)
	);

	public HorseMarkingFeatureRenderer(FeatureRendererContext<HorseEntityRenderState, HorseEntityModel> featureRendererContext) {
		super(featureRendererContext);
	}

	public void render(
		MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, HorseEntityRenderState horseEntityRenderState, float f, float g
	) {
		Identifier identifier = (Identifier)TEXTURES.get(horseEntityRenderState.marking);
		if (identifier != INVISIBLE_ID && !horseEntityRenderState.invisible) {
			VertexConsumer vertexConsumer = vertexConsumerProvider.getBuffer(RenderLayer.getEntityTranslucent(identifier));
			this.getContextModel().render(matrixStack, vertexConsumer, i, LivingEntityRenderer.getOverlay(horseEntityRenderState, 0.0F));
		}
	}
}
