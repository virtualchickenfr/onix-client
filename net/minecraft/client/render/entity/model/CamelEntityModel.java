package net.minecraft.client.render.entity.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.animation.CamelAnimations;
import net.minecraft.client.render.entity.state.CamelEntityRenderState;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class CamelEntityModel extends EntityModel<CamelEntityRenderState> {
	private static final float LIMB_ANGLE_SCALE = 2.0F;
	private static final float LIMB_DISTANCE_SCALE = 2.5F;
	public static final ModelTransformer BABY_TRANSFORMER = ModelTransformer.scaling(0.45F);
	protected final ModelPart head;

	public CamelEntityModel(ModelPart modelPart) {
		super(modelPart);
		ModelPart modelPart2 = modelPart.getChild(EntityModelPartNames.BODY);
		this.head = modelPart2.getChild(EntityModelPartNames.HEAD);
	}

	public static TexturedModelData getTexturedModelData() {
		return TexturedModelData.of(getModelData(), 128, 128);
	}

	protected static ModelData getModelData() {
		ModelData modelData = new ModelData();
		ModelPartData modelPartData = modelData.getRoot();
		ModelPartData modelPartData2 = modelPartData.addChild(
			EntityModelPartNames.BODY, ModelPartBuilder.create().uv(0, 25).cuboid(-7.5F, -12.0F, -23.5F, 15.0F, 12.0F, 27.0F), ModelTransform.origin(0.0F, 4.0F, 9.5F)
		);
		modelPartData2.addChild(
			"hump", ModelPartBuilder.create().uv(74, 0).cuboid(-4.5F, -5.0F, -5.5F, 9.0F, 5.0F, 11.0F), ModelTransform.origin(0.0F, -12.0F, -10.0F)
		);
		modelPartData2.addChild(
			EntityModelPartNames.TAIL, ModelPartBuilder.create().uv(122, 0).cuboid(-1.5F, 0.0F, 0.0F, 3.0F, 14.0F, 0.0F), ModelTransform.origin(0.0F, -9.0F, 3.5F)
		);
		ModelPartData modelPartData3 = modelPartData2.addChild(
			EntityModelPartNames.HEAD,
			ModelPartBuilder.create()
				.uv(60, 24)
				.cuboid(-3.5F, -7.0F, -15.0F, 7.0F, 8.0F, 19.0F)
				.uv(21, 0)
				.cuboid(-3.5F, -21.0F, -15.0F, 7.0F, 14.0F, 7.0F)
				.uv(50, 0)
				.cuboid(-2.5F, -21.0F, -21.0F, 5.0F, 5.0F, 6.0F),
			ModelTransform.origin(0.0F, -3.0F, -19.5F)
		);
		modelPartData3.addChild(
			EntityModelPartNames.LEFT_EAR, ModelPartBuilder.create().uv(45, 0).cuboid(-0.5F, 0.5F, -1.0F, 3.0F, 1.0F, 2.0F), ModelTransform.origin(2.5F, -21.0F, -9.5F)
		);
		modelPartData3.addChild(
			EntityModelPartNames.RIGHT_EAR,
			ModelPartBuilder.create().uv(67, 0).cuboid(-2.5F, 0.5F, -1.0F, 3.0F, 1.0F, 2.0F),
			ModelTransform.origin(-2.5F, -21.0F, -9.5F)
		);
		modelPartData.addChild(
			EntityModelPartNames.LEFT_HIND_LEG,
			ModelPartBuilder.create().uv(58, 16).cuboid(-2.5F, 2.0F, -2.5F, 5.0F, 21.0F, 5.0F),
			ModelTransform.origin(4.9F, 1.0F, 9.5F)
		);
		modelPartData.addChild(
			EntityModelPartNames.RIGHT_HIND_LEG,
			ModelPartBuilder.create().uv(94, 16).cuboid(-2.5F, 2.0F, -2.5F, 5.0F, 21.0F, 5.0F),
			ModelTransform.origin(-4.9F, 1.0F, 9.5F)
		);
		modelPartData.addChild(
			EntityModelPartNames.LEFT_FRONT_LEG,
			ModelPartBuilder.create().uv(0, 0).cuboid(-2.5F, 2.0F, -2.5F, 5.0F, 21.0F, 5.0F),
			ModelTransform.origin(4.9F, 1.0F, -10.5F)
		);
		modelPartData.addChild(
			EntityModelPartNames.RIGHT_FRONT_LEG,
			ModelPartBuilder.create().uv(0, 26).cuboid(-2.5F, 2.0F, -2.5F, 5.0F, 21.0F, 5.0F),
			ModelTransform.origin(-4.9F, 1.0F, -10.5F)
		);
		return modelData;
	}

	public void setAngles(CamelEntityRenderState camelEntityRenderState) {
		super.setAngles(camelEntityRenderState);
		this.setHeadAngles(camelEntityRenderState, camelEntityRenderState.relativeHeadYaw, camelEntityRenderState.pitch);
		this.animateWalking(CamelAnimations.WALKING, camelEntityRenderState.limbSwingAnimationProgress, camelEntityRenderState.limbSwingAmplitude, 2.0F, 2.5F);
		this.animate(camelEntityRenderState.sittingTransitionAnimationState, CamelAnimations.SITTING_TRANSITION, camelEntityRenderState.age, 1.0F);
		this.animate(camelEntityRenderState.sittingAnimationState, CamelAnimations.SITTING, camelEntityRenderState.age, 1.0F);
		this.animate(camelEntityRenderState.standingTransitionAnimationState, CamelAnimations.STANDING_TRANSITION, camelEntityRenderState.age, 1.0F);
		this.animate(camelEntityRenderState.idlingAnimationState, CamelAnimations.IDLING, camelEntityRenderState.age, 1.0F);
		this.animate(camelEntityRenderState.dashingAnimationState, CamelAnimations.DASHING, camelEntityRenderState.age, 1.0F);
	}

	private void setHeadAngles(CamelEntityRenderState state, float headYaw, float headPitch) {
		headYaw = MathHelper.clamp(headYaw, -30.0F, 30.0F);
		headPitch = MathHelper.clamp(headPitch, -25.0F, 45.0F);
		if (state.jumpCooldown > 0.0F) {
			float f = 45.0F * state.jumpCooldown / 55.0F;
			headPitch = MathHelper.clamp(headPitch + f, -25.0F, 70.0F);
		}

		this.head.yaw = headYaw * (float) (Math.PI / 180.0);
		this.head.pitch = headPitch * (float) (Math.PI / 180.0);
	}
}
