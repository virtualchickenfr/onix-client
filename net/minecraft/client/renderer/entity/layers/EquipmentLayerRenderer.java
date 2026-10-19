package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class EquipmentLayerRenderer {
	private static final int NO_LAYER_COLOR = 0;
	private final EquipmentAssetManager equipmentAssets;
	private final Function<EquipmentLayerRenderer.LayerTextureKey, ResourceLocation> layerTextureLookup;
	private final Function<EquipmentLayerRenderer.TrimSpriteKey, TextureAtlasSprite> trimSpriteLookup;

	public EquipmentLayerRenderer(EquipmentAssetManager equipmentAssetManager, TextureAtlas textureAtlas) {
		this.equipmentAssets = equipmentAssetManager;
		this.layerTextureLookup = Util.memoize(layerTextureKey -> layerTextureKey.layer.getTextureLocation(layerTextureKey.layerType));
		this.trimSpriteLookup = Util.memoize(trimSpriteKey -> textureAtlas.getSprite(trimSpriteKey.spriteId()));
	}

	public void renderLayers(
		EquipmentClientInfo.LayerType layerType,
		ResourceKey<EquipmentAsset> resourceKey,
		Model model,
		ItemStack itemStack,
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i
	) {
		this.renderLayers(layerType, resourceKey, model, itemStack, poseStack, multiBufferSource, i, null);
	}

	public void renderLayers(
		EquipmentClientInfo.LayerType layerType,
		ResourceKey<EquipmentAsset> resourceKey,
		Model model,
		ItemStack itemStack,
		PoseStack poseStack,
		MultiBufferSource multiBufferSource,
		int i,
		@Nullable ResourceLocation resourceLocation
	) {
		List<EquipmentClientInfo.Layer> list = this.equipmentAssets.get(resourceKey).getLayers(layerType);
		if (!list.isEmpty()) {
			int j = DyedItemColor.getOrDefault(itemStack, 0);
			boolean bl = itemStack.hasFoil();

			for (EquipmentClientInfo.Layer layer : list) {
				int k = getColorForLayer(layer, j);
				if (k != 0) {
					ResourceLocation resourceLocation2 = layer.usePlayerTexture() && resourceLocation != null
						? resourceLocation
						: this.layerTextureLookup.apply(new EquipmentLayerRenderer.LayerTextureKey(layerType, layer));
					VertexConsumer vertexConsumer = ItemRenderer.getArmorFoilBuffer(multiBufferSource, RenderType.armorCutoutNoCull(resourceLocation2), bl);
					model.renderToBuffer(poseStack, vertexConsumer, i, OverlayTexture.NO_OVERLAY, k);
					bl = false;
				}
			}

			ArmorTrim armorTrim = itemStack.get(DataComponents.TRIM);
			if (armorTrim != null) {
				TextureAtlasSprite textureAtlasSprite = this.trimSpriteLookup.apply(new EquipmentLayerRenderer.TrimSpriteKey(armorTrim, layerType, resourceKey));
				VertexConsumer vertexConsumer2 = textureAtlasSprite.wrap(multiBufferSource.getBuffer(Sheets.armorTrimsSheet(armorTrim.pattern().value().decal())));
				model.renderToBuffer(poseStack, vertexConsumer2, i, OverlayTexture.NO_OVERLAY);
			}
		}
	}

	private static int getColorForLayer(EquipmentClientInfo.Layer layer, int i) {
		Optional<EquipmentClientInfo.Dyeable> optional = layer.dyeable();
		if (optional.isPresent()) {
			int j = optional.get().colorWhenUndyed().map(ARGB::opaque).orElse(0);
			return i != 0 ? i : j;
		} else {
			return -1;
		}
	}

	@Environment(EnvType.CLIENT)
	record LayerTextureKey(EquipmentClientInfo.LayerType layerType, EquipmentClientInfo.Layer layer) {
	}

	@Environment(EnvType.CLIENT)
	record TrimSpriteKey(ArmorTrim trim, EquipmentClientInfo.LayerType layerType, ResourceKey<EquipmentAsset> equipmentAssetId) {
		public ResourceLocation spriteId() {
			return this.trim.layerAssetId(this.layerType.trimAssetPrefix(), this.equipmentAssetId);
		}
	}
}
