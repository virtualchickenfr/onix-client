package net.minecraft.client.render.entity.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;

@Environment(EnvType.CLIENT)
public class ColdCowEntityModel extends CowEntityModel {
	public ColdCowEntityModel(ModelPart modelPart) {
		super(modelPart);
	}

	public static TexturedModelData getTexturedModelData() {
		ModelData modelData = getModelData();
		modelData.getRoot()
			.addChild(
				EntityModelPartNames.BODY,
				ModelPartBuilder.create()
					.uv(20, 32)
					.cuboid(-6.0F, -10.0F, -7.0F, 12.0F, 18.0F, 10.0F, new Dilation(0.5F))
					.uv(18, 4)
					.cuboid(-6.0F, -10.0F, -7.0F, 12.0F, 18.0F, 10.0F)
					.uv(52, 0)
					.cuboid(-2.0F, 2.0F, -8.0F, 4.0F, 6.0F, 1.0F),
				ModelTransform.of(0.0F, 5.0F, 2.0F, (float) (Math.PI / 2), 0.0F, 0.0F)
			);
		ModelPartData modelPartData = modelData.getRoot()
			.addChild(
				EntityModelPartNames.HEAD,
				ModelPartBuilder.create().uv(0, 0).cuboid(-4.0F, -4.0F, -6.0F, 8.0F, 8.0F, 6.0F).uv(9, 33).cuboid(-3.0F, 1.0F, -7.0F, 6.0F, 3.0F, 1.0F),
				ModelTransform.origin(0.0F, 4.0F, -8.0F)
			);
		modelPartData.addChild(
			EntityModelPartNames.RIGHT_HORN,
			ModelPartBuilder.create().uv(0, 40).cuboid(-1.5F, -4.5F, -0.5F, 2.0F, 6.0F, 2.0F),
			ModelTransform.of(-4.5F, -2.5F, -3.5F, 1.5708F, 0.0F, 0.0F)
		);
		modelPartData.addChild(
			EntityModelPartNames.LEFT_HORN,
			ModelPartBuilder.create().uv(0, 32).cuboid(-1.5F, -3.0F, -0.5F, 2.0F, 6.0F, 2.0F),
			ModelTransform.of(5.5F, -2.5F, -5.0F, 1.5708F, 0.0F, 0.0F)
		);
		return TexturedModelData.of(modelData, 64, 64);
	}
}
