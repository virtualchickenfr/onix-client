package net.minecraft.client.render.model;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.ItemAsset;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.item.model.MissingItemModel;
import net.minecraft.client.render.model.json.ModelTransformation;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.util.Identifier;
import net.minecraft.util.thread.AsyncHelper;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ModelBaker {
	public static final SpriteIdentifier FIRE_0 = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla("block/fire_0"));
	public static final SpriteIdentifier FIRE_1 = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla("block/fire_1"));
	public static final SpriteIdentifier LAVA_FLOW = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla("block/lava_flow"));
	public static final SpriteIdentifier WATER_FLOW = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla("block/water_flow"));
	public static final SpriteIdentifier WATER_OVERLAY = new SpriteIdentifier(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE, Identifier.ofVanilla("block/water_overlay"));
	public static final SpriteIdentifier BANNER_BASE = new SpriteIdentifier(
		TexturedRenderLayers.BANNER_PATTERNS_ATLAS_TEXTURE, Identifier.ofVanilla("entity/banner_base")
	);
	public static final SpriteIdentifier SHIELD_BASE = new SpriteIdentifier(
		TexturedRenderLayers.SHIELD_PATTERNS_ATLAS_TEXTURE, Identifier.ofVanilla("entity/shield_base")
	);
	public static final SpriteIdentifier SHIELD_BASE_NO_PATTERN = new SpriteIdentifier(
		TexturedRenderLayers.SHIELD_PATTERNS_ATLAS_TEXTURE, Identifier.ofVanilla("entity/shield_base_nopattern")
	);
	public static final int MAX_BLOCK_DESTRUCTION_STAGE = 10;
	public static final List<Identifier> BLOCK_DESTRUCTION_STAGES = (List<Identifier>)IntStream.range(0, 10)
		.mapToObj(stage -> Identifier.ofVanilla("block/destroy_stage_" + stage))
		.collect(Collectors.toList());
	public static final List<Identifier> BLOCK_DESTRUCTION_STAGE_TEXTURES = (List<Identifier>)BLOCK_DESTRUCTION_STAGES.stream()
		.map(id -> id.withPath((UnaryOperator<String>)(path -> "textures/" + path + ".png")))
		.collect(Collectors.toList());
	public static final List<RenderLayer> BLOCK_DESTRUCTION_RENDER_LAYERS = (List<RenderLayer>)BLOCK_DESTRUCTION_STAGE_TEXTURES.stream()
		.map(RenderLayer::getBlockBreaking)
		.collect(Collectors.toList());
	static final Logger LOGGER = LogUtils.getLogger();
	private final LoadedEntityModels entityModels;
	private final Map<BlockState, BlockStateModel.UnbakedGrouped> blockModels;
	private final Map<Identifier, ItemAsset> itemAssets;
	final Map<Identifier, BakedSimpleModel> simpleModels;
	final BakedSimpleModel missingModel;

	public ModelBaker(
		LoadedEntityModels entityModels,
		Map<BlockState, BlockStateModel.UnbakedGrouped> blockModels,
		Map<Identifier, ItemAsset> itemModels,
		Map<Identifier, BakedSimpleModel> simpleModels,
		BakedSimpleModel missingModel
	) {
		this.entityModels = entityModels;
		this.blockModels = blockModels;
		this.itemAssets = itemModels;
		this.simpleModels = simpleModels;
		this.missingModel = missingModel;
	}

	public CompletableFuture<ModelBaker.BakedModels> bake(ErrorCollectingSpriteGetter spriteGetter, Executor executor) {
		ModelBaker.BlockItemModels blockItemModels = ModelBaker.BlockItemModels.bake(this.missingModel, spriteGetter);
		ModelBaker.BakerImpl bakerImpl = new ModelBaker.BakerImpl(spriteGetter);
		CompletableFuture<Map<BlockState, BlockStateModel>> completableFuture = AsyncHelper.mapValues(this.blockModels, (state, unbaked) -> {
			try {
				return unbaked.bake(state, bakerImpl);
			} catch (Exception var4x) {
				LOGGER.warn("Unable to bake model: '{}': {}", state, var4x);
				return null;
			}
		}, executor);
		CompletableFuture<Map<Identifier, ItemModel>> completableFuture2 = AsyncHelper.mapValues(this.itemAssets, (state, asset) -> {
			try {
				return asset.model().bake(new ItemModel.BakeContext(bakerImpl, this.entityModels, blockItemModels.item, asset.registrySwapper()));
			} catch (Exception var6x) {
				LOGGER.warn("Unable to bake item model: '{}'", state, var6x);
				return null;
			}
		}, executor);
		Map<Identifier, ItemAsset.Properties> map = new HashMap(this.itemAssets.size());
		this.itemAssets.forEach((id, asset) -> {
			ItemAsset.Properties properties = asset.properties();
			if (!properties.equals(ItemAsset.Properties.DEFAULT)) {
				map.put(id, properties);
			}
		});
		return completableFuture.thenCombine(
			completableFuture2, (blockStateModels, itemModels) -> new ModelBaker.BakedModels(blockItemModels, blockStateModels, itemModels, map)
		);
	}

	@Environment(EnvType.CLIENT)
	public record BakedModels(
		ModelBaker.BlockItemModels missingModels,
		Map<BlockState, BlockStateModel> blockStateModels,
		Map<Identifier, ItemModel> itemStackModels,
		Map<Identifier, ItemAsset.Properties> itemProperties
	) {
	}

	@Environment(EnvType.CLIENT)
	class BakerImpl implements Baker {
		private final ErrorCollectingSpriteGetter spriteGetter;
		private final Map<Baker.ResolvableCacheKey<Object>, Object> cache = new ConcurrentHashMap();
		private final Function<Baker.ResolvableCacheKey<Object>, Object> cacheValueFunction = key -> key.compute(this);

		BakerImpl(final ErrorCollectingSpriteGetter spriteGetter) {
			this.spriteGetter = spriteGetter;
		}

		@Override
		public ErrorCollectingSpriteGetter getSpriteGetter() {
			return this.spriteGetter;
		}

		@Override
		public BakedSimpleModel getModel(Identifier id) {
			BakedSimpleModel bakedSimpleModel = (BakedSimpleModel)ModelBaker.this.simpleModels.get(id);
			if (bakedSimpleModel == null) {
				ModelBaker.LOGGER.warn("Requested a model that was not discovered previously: {}", id);
				return ModelBaker.this.missingModel;
			} else {
				return bakedSimpleModel;
			}
		}

		@Override
		public <T> T compute(Baker.ResolvableCacheKey<T> key) {
			return (T)this.cache.computeIfAbsent(key, this.cacheValueFunction);
		}
	}

	@Environment(EnvType.CLIENT)
	public record BlockItemModels(BlockStateModel block, ItemModel item) {

		public static ModelBaker.BlockItemModels bake(BakedSimpleModel model, ErrorCollectingSpriteGetter errorCollectingSpriteGetter) {
			Baker baker = new Baker() {
				@Override
				public BakedSimpleModel getModel(Identifier id) {
					throw new IllegalStateException("Missing model can't have dependencies, but asked for " + id);
				}

				@Override
				public <T> T compute(Baker.ResolvableCacheKey<T> key) {
					return key.compute(this);
				}

				@Override
				public ErrorCollectingSpriteGetter getSpriteGetter() {
					return errorCollectingSpriteGetter;
				}
			};
			ModelTextures modelTextures = model.getTextures();
			boolean bl = model.getAmbientOcclusion();
			boolean bl2 = model.getGuiLight().isSide();
			ModelTransformation modelTransformation = model.getTransformations();
			BakedGeometry bakedGeometry = model.bakeGeometry(modelTextures, baker, ModelRotation.X0_Y0);
			Sprite sprite = model.getParticleTexture(modelTextures, baker);
			BlockStateModel blockStateModel = new SimpleBlockStateModel(new GeometryBakedModel(bakedGeometry, bl, sprite));
			ItemModel itemModel = new MissingItemModel(bakedGeometry.getAllQuads(), new ModelSettings(bl2, sprite, modelTransformation));
			return new ModelBaker.BlockItemModels(blockStateModel, itemModel);
		}
	}
}
