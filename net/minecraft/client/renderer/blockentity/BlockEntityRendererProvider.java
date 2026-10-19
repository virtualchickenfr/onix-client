package net.minecraft.client.renderer.blockentity;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.level.block.entity.BlockEntity;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface BlockEntityRendererProvider<T extends BlockEntity> {
	BlockEntityRenderer<T> create(BlockEntityRendererProvider.Context context);

	@Environment(EnvType.CLIENT)
	class Context {
		private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
		private final BlockRenderDispatcher blockRenderDispatcher;
		private final ItemModelResolver itemModelResolver;
		private final ItemRenderer itemRenderer;
		private final EntityRenderDispatcher entityRenderer;
		private final EntityModelSet modelSet;
		private final Font font;

		public Context(
			BlockEntityRenderDispatcher blockEntityRenderDispatcher,
			BlockRenderDispatcher blockRenderDispatcher,
			ItemModelResolver itemModelResolver,
			ItemRenderer itemRenderer,
			EntityRenderDispatcher entityRenderDispatcher,
			EntityModelSet entityModelSet,
			Font font
		) {
			this.blockEntityRenderDispatcher = blockEntityRenderDispatcher;
			this.blockRenderDispatcher = blockRenderDispatcher;
			this.itemModelResolver = itemModelResolver;
			this.itemRenderer = itemRenderer;
			this.entityRenderer = entityRenderDispatcher;
			this.modelSet = entityModelSet;
			this.font = font;
		}

		public BlockEntityRenderDispatcher getBlockEntityRenderDispatcher() {
			return this.blockEntityRenderDispatcher;
		}

		public BlockRenderDispatcher getBlockRenderDispatcher() {
			return this.blockRenderDispatcher;
		}

		public EntityRenderDispatcher getEntityRenderer() {
			return this.entityRenderer;
		}

		public ItemModelResolver getItemModelResolver() {
			return this.itemModelResolver;
		}

		public ItemRenderer getItemRenderer() {
			return this.itemRenderer;
		}

		public EntityModelSet getModelSet() {
			return this.modelSet;
		}

		public ModelPart bakeLayer(ModelLayerLocation modelLayerLocation) {
			return this.modelSet.bakeLayer(modelLayerLocation);
		}

		public Font getFont() {
			return this.font;
		}
	}
}
