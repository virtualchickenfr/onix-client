package net.minecraft.client.renderer.entity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MapRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.resources.model.EquipmentAssetManager;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface EntityRendererProvider<T extends Entity> {
	EntityRenderer<T, ?> create(EntityRendererProvider.Context context);

	@Environment(EnvType.CLIENT)
	class Context {
		private final EntityRenderDispatcher entityRenderDispatcher;
		private final ItemModelResolver itemModelResolver;
		private final MapRenderer mapRenderer;
		private final BlockRenderDispatcher blockRenderDispatcher;
		private final ResourceManager resourceManager;
		private final EntityModelSet modelSet;
		private final EquipmentAssetManager equipmentAssets;
		private final Font font;
		private final EquipmentLayerRenderer equipmentRenderer;

		public Context(
			EntityRenderDispatcher entityRenderDispatcher,
			ItemModelResolver itemModelResolver,
			MapRenderer mapRenderer,
			BlockRenderDispatcher blockRenderDispatcher,
			ResourceManager resourceManager,
			EntityModelSet entityModelSet,
			EquipmentAssetManager equipmentAssetManager,
			Font font
		) {
			this.entityRenderDispatcher = entityRenderDispatcher;
			this.itemModelResolver = itemModelResolver;
			this.mapRenderer = mapRenderer;
			this.blockRenderDispatcher = blockRenderDispatcher;
			this.resourceManager = resourceManager;
			this.modelSet = entityModelSet;
			this.equipmentAssets = equipmentAssetManager;
			this.font = font;
			this.equipmentRenderer = new EquipmentLayerRenderer(equipmentAssetManager, this.getModelManager().getAtlas(Sheets.ARMOR_TRIMS_SHEET));
		}

		public EntityRenderDispatcher getEntityRenderDispatcher() {
			return this.entityRenderDispatcher;
		}

		public ItemModelResolver getItemModelResolver() {
			return this.itemModelResolver;
		}

		public MapRenderer getMapRenderer() {
			return this.mapRenderer;
		}

		public BlockRenderDispatcher getBlockRenderDispatcher() {
			return this.blockRenderDispatcher;
		}

		public ResourceManager getResourceManager() {
			return this.resourceManager;
		}

		public EntityModelSet getModelSet() {
			return this.modelSet;
		}

		public EquipmentAssetManager getEquipmentAssets() {
			return this.equipmentAssets;
		}

		public EquipmentLayerRenderer getEquipmentRenderer() {
			return this.equipmentRenderer;
		}

		public ModelManager getModelManager() {
			return this.blockRenderDispatcher.getBlockModelShaper().getModelManager();
		}

		public ModelPart bakeLayer(ModelLayerLocation modelLayerLocation) {
			return this.modelSet.bakeLayer(modelLayerLocation);
		}

		public Font getFont() {
			return this.font;
		}
	}
}
