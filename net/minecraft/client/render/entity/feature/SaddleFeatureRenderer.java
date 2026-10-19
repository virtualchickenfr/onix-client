package net.minecraft.client.render.entity.feature;

import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.equipment.EquipmentModel;
import net.minecraft.client.render.entity.equipment.EquipmentRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.registry.RegistryKey;

@Environment(EnvType.CLIENT)
public class SaddleFeatureRenderer<S extends LivingEntityRenderState, RM extends EntityModel<? super S>, EM extends EntityModel<? super S>>
	extends FeatureRenderer<S, RM> {
	private final EquipmentRenderer equipmentRenderer;
	private final EquipmentModel.LayerType layerType;
	private final Function<S, ItemStack> saddleStackGetter;
	private final EM adultModel;
	private final EM babyModel;

	public SaddleFeatureRenderer(
		FeatureRendererContext<S, RM> context,
		EquipmentRenderer equipmentRenderer,
		EquipmentModel.LayerType layerType,
		Function<S, ItemStack> saddleStackGetter,
		EM adultModel,
		EM babyModel
	) {
		super(context);
		this.equipmentRenderer = equipmentRenderer;
		this.layerType = layerType;
		this.saddleStackGetter = saddleStackGetter;
		this.adultModel = adultModel;
		this.babyModel = babyModel;
	}

	public SaddleFeatureRenderer(
		FeatureRendererContext<S, RM> context,
		EquipmentRenderer equipmentRenderer,
		EM model,
		EquipmentModel.LayerType layerType,
		Function<S, ItemStack> saddleStackGetter
	) {
		this(context, equipmentRenderer, layerType, saddleStackGetter, model, model);
	}

	public void render(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, S livingEntityRenderState, float f, float g) {
		ItemStack itemStack = (ItemStack)this.saddleStackGetter.apply(livingEntityRenderState);
		EquippableComponent equippableComponent = itemStack.get(DataComponentTypes.EQUIPPABLE);
		if (equippableComponent != null && !equippableComponent.assetId().isEmpty()) {
			EM entityModel = livingEntityRenderState.baby ? this.babyModel : this.adultModel;
			entityModel.setAngles(livingEntityRenderState);
			this.equipmentRenderer
				.render(this.layerType, (RegistryKey<EquipmentAsset>)equippableComponent.assetId().get(), entityModel, itemStack, matrixStack, vertexConsumerProvider, i);
		}
	}
}
