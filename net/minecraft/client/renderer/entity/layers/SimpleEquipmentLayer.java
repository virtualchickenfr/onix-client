package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

@Environment(EnvType.CLIENT)
public class SimpleEquipmentLayer<S extends LivingEntityRenderState, RM extends EntityModel<? super S>, EM extends EntityModel<? super S>>
	extends RenderLayer<S, RM> {
	private final EquipmentLayerRenderer equipmentRenderer;
	private final EquipmentClientInfo.LayerType layer;
	private final Function<S, ItemStack> itemGetter;
	private final EM adultModel;
	private final EM babyModel;

	public SimpleEquipmentLayer(
		RenderLayerParent<S, RM> renderLayerParent,
		EquipmentLayerRenderer equipmentLayerRenderer,
		EquipmentClientInfo.LayerType layerType,
		Function<S, ItemStack> function,
		EM entityModel,
		EM entityModel2
	) {
		super(renderLayerParent);
		this.equipmentRenderer = equipmentLayerRenderer;
		this.layer = layerType;
		this.itemGetter = function;
		this.adultModel = entityModel;
		this.babyModel = entityModel2;
	}

	public SimpleEquipmentLayer(
		RenderLayerParent<S, RM> renderLayerParent,
		EquipmentLayerRenderer equipmentLayerRenderer,
		EM entityModel,
		EquipmentClientInfo.LayerType layerType,
		Function<S, ItemStack> function
	) {
		this(renderLayerParent, equipmentLayerRenderer, layerType, function, entityModel, entityModel);
	}

	public void render(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, S livingEntityRenderState, float f, float g) {
		ItemStack itemStack = this.itemGetter.apply(livingEntityRenderState);
		Equippable equippable = itemStack.get(DataComponents.EQUIPPABLE);
		if (equippable != null && !equippable.assetId().isEmpty()) {
			EM entityModel = livingEntityRenderState.isBaby ? this.babyModel : this.adultModel;
			entityModel.setupAnim(livingEntityRenderState);
			this.equipmentRenderer.renderLayers(this.layer, equippable.assetId().get(), entityModel, itemStack, poseStack, multiBufferSource, i);
		}
	}
}
