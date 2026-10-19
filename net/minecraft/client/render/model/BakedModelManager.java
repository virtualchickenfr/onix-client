package net.minecraft.client.render.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.item.ItemAsset;
import net.minecraft.client.item.ItemAssetsLoader;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.block.entity.LoadedBlockEntityModels;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.model.json.GeneratedItemModel;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.texture.atlas.Atlases;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.util.profiler.ScopedProfiler;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class BakedModelManager implements ResourceReloader, AutoCloseable, FabricBakedModelManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceFinder MODELS_FINDER = ResourceFinder.json("models");
	private static final Map<Identifier, Identifier> LAYERS_TO_LOADERS = Map.of(
		TexturedRenderLayers.BANNER_PATTERNS_ATLAS_TEXTURE,
		Atlases.BANNER_PATTERNS,
		TexturedRenderLayers.BEDS_ATLAS_TEXTURE,
		Atlases.BEDS,
		TexturedRenderLayers.CHEST_ATLAS_TEXTURE,
		Atlases.CHESTS,
		TexturedRenderLayers.SHIELD_PATTERNS_ATLAS_TEXTURE,
		Atlases.SHIELD_PATTERNS,
		TexturedRenderLayers.SIGNS_ATLAS_TEXTURE,
		Atlases.SIGNS,
		TexturedRenderLayers.SHULKER_BOXES_ATLAS_TEXTURE,
		Atlases.SHULKER_BOXES,
		TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE,
		Atlases.ARMOR_TRIMS,
		TexturedRenderLayers.DECORATED_POT_ATLAS_TEXTURE,
		Atlases.DECORATED_POT,
		SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
		Atlases.BLOCKS
	);
	private Map<Identifier, ItemModel> bakedItemModels = Map.of();
	private Map<Identifier, ItemAsset.Properties> itemProperties = Map.of();
	private final SpriteAtlasManager atlasManager;
	private final BlockModels blockModelCache;
	private final BlockColors colorMap;
	private LoadedEntityModels entityModels = LoadedEntityModels.EMPTY;
	private LoadedBlockEntityModels blockEntityModels = LoadedBlockEntityModels.EMPTY;
	private int mipmapLevels;
	private ModelBaker.BlockItemModels missingModels;
	private Object2IntMap<BlockState> modelGroups = Object2IntMaps.emptyMap();

	public BakedModelManager(TextureManager textureManager, BlockColors colorMap, int mipmapLevels) {
		this.colorMap = colorMap;
		this.mipmapLevels = mipmapLevels;
		this.blockModelCache = new BlockModels(this);
		this.atlasManager = new SpriteAtlasManager(LAYERS_TO_LOADERS, textureManager);
	}

	public BlockStateModel getMissingModel() {
		return this.missingModels.block();
	}

	public ItemModel getItemModel(Identifier id) {
		return (ItemModel)this.bakedItemModels.getOrDefault(id, this.missingModels.item());
	}

	public ItemAsset.Properties getItemProperties(Identifier id) {
		return (ItemAsset.Properties)this.itemProperties.getOrDefault(id, ItemAsset.Properties.DEFAULT);
	}

	public BlockModels getBlockModels() {
		return this.blockModelCache;
	}

	@Override
	public final CompletableFuture<Void> reload(ResourceReloader.Synchronizer synchronizer, ResourceManager resourceManager, Executor executor, Executor executor2) {
		CompletableFuture<LoadedEntityModels> completableFuture = CompletableFuture.supplyAsync(LoadedEntityModels::copy, executor);
		CompletableFuture<LoadedBlockEntityModels> completableFuture2 = completableFuture.thenApplyAsync(LoadedBlockEntityModels::fromModels, executor);
		CompletableFuture<Map<Identifier, UnbakedModel>> completableFuture3 = reloadModels(resourceManager, executor);
		CompletableFuture<BlockStatesLoader.LoadedModels> completableFuture4 = BlockStatesLoader.load(resourceManager, executor);
		CompletableFuture<ItemAssetsLoader.Result> completableFuture5 = ItemAssetsLoader.load(resourceManager, executor);
		CompletableFuture<BakedModelManager.Models> completableFuture6 = CompletableFuture.allOf(completableFuture3, completableFuture4, completableFuture5)
			.thenApplyAsync(
				async -> collect(
					(Map<Identifier, UnbakedModel>)completableFuture3.join(),
					(BlockStatesLoader.LoadedModels)completableFuture4.join(),
					(ItemAssetsLoader.Result)completableFuture5.join()
				),
				executor
			);
		CompletableFuture<Object2IntMap<BlockState>> completableFuture7 = completableFuture4.thenApplyAsync(definition -> group(this.colorMap, definition), executor);
		Map<Identifier, CompletableFuture<SpriteAtlasManager.AtlasPreparation>> map = this.atlasManager.reload(resourceManager, this.mipmapLevels, executor);
		return CompletableFuture.allOf(
				(CompletableFuture[])Stream.concat(
						map.values().stream(),
						Stream.of(completableFuture6, completableFuture7, completableFuture4, completableFuture5, completableFuture, completableFuture2, completableFuture3)
					)
					.toArray(CompletableFuture[]::new)
			)
			.thenComposeAsync(
				async -> {
					Map<Identifier, SpriteAtlasManager.AtlasPreparation> map2 = Util.transformMapValues(map, CompletableFuture::join);
					BakedModelManager.Models models = (BakedModelManager.Models)completableFuture6.join();
					Object2IntMap<BlockState> object2IntMap = (Object2IntMap<BlockState>)completableFuture7.join();
					Set<Identifier> set = Sets.<Identifier>difference(((Map)completableFuture3.join()).keySet(), models.models.keySet());
					if (!set.isEmpty()) {
						LOGGER.debug("Unreferenced models: \n{}", set.stream().sorted().map(id -> "\t" + id + "\n").collect(Collectors.joining()));
					}

					ModelBaker modelBaker = new ModelBaker(
						(LoadedEntityModels)completableFuture.join(),
						((BlockStatesLoader.LoadedModels)completableFuture4.join()).models(),
						((ItemAssetsLoader.Result)completableFuture5.join()).contents(),
						models.models(),
						models.missing()
					);
					return bake(map2, modelBaker, object2IntMap, (LoadedEntityModels)completableFuture.join(), (LoadedBlockEntityModels)completableFuture2.join(), executor);
				},
				executor
			)
			.thenCompose(result -> result.readyForUpload.thenApply(void_ -> result))
			.thenCompose(synchronizer::whenPrepared)
			.thenAcceptAsync(bakingResult -> this.upload(bakingResult, Profilers.get()), executor2);
	}

	private static CompletableFuture<Map<Identifier, UnbakedModel>> reloadModels(ResourceManager resourceManager, Executor executor) {
		return CompletableFuture.supplyAsync(() -> MODELS_FINDER.findResources(resourceManager), executor)
			.thenCompose(
				models -> {
					List<CompletableFuture<Pair<Identifier, JsonUnbakedModel>>> list = new ArrayList(models.size());

					for (Entry<Identifier, Resource> entry : models.entrySet()) {
						list.add(CompletableFuture.supplyAsync(() -> {
							Identifier identifier = MODELS_FINDER.toResourceId((Identifier)entry.getKey());

							try {
								Reader reader = ((Resource)entry.getValue()).getReader();

								Pair var3;
								try {
									var3 = Pair.of(identifier, JsonUnbakedModel.deserialize(reader));
								} catch (Throwable var6) {
									if (reader != null) {
										try {
											reader.close();
										} catch (Throwable var5) {
											var6.addSuppressed(var5);
										}
									}

									throw var6;
								}

								if (reader != null) {
									reader.close();
								}

								return var3;
							} catch (Exception var7) {
								LOGGER.error("Failed to load model {}", entry.getKey(), var7);
								return null;
							}
						}, executor));
					}

					return Util.combineSafe(list)
						.thenApply(modelsx -> (Map)modelsx.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond)));
				}
			);
	}

	private static BakedModelManager.Models collect(
		Map<Identifier, UnbakedModel> modelMap, BlockStatesLoader.LoadedModels stateDefinition, ItemAssetsLoader.Result result
	) {
		BakedModelManager.Models var5;
		try (ScopedProfiler scopedProfiler = Profilers.get().scoped("dependencies")) {
			ReferencedModelsCollector referencedModelsCollector = new ReferencedModelsCollector(modelMap, MissingModel.create());
			referencedModelsCollector.addSpecialModel(GeneratedItemModel.GENERATED, new GeneratedItemModel());
			stateDefinition.models().values().forEach(referencedModelsCollector::resolve);
			result.contents().values().forEach(asset -> referencedModelsCollector.resolve(asset.model()));
			var5 = new BakedModelManager.Models(referencedModelsCollector.getMissingModel(), referencedModelsCollector.collectModels());
		}

		return var5;
	}

	private static CompletableFuture<BakedModelManager.BakingResult> bake(
		Map<Identifier, SpriteAtlasManager.AtlasPreparation> atlasMap,
		ModelBaker baker,
		Object2IntMap<BlockState> blockStates,
		LoadedEntityModels entityModels,
		LoadedBlockEntityModels blockEntityModels,
		Executor executor
	) {
		CompletableFuture<Void> completableFuture = CompletableFuture.allOf(
			(CompletableFuture[])atlasMap.values().stream().map(SpriteAtlasManager.AtlasPreparation::whenComplete).toArray(CompletableFuture[]::new)
		);
		final Multimap<String, SpriteIdentifier> multimap = Multimaps.synchronizedMultimap(HashMultimap.create());
		final Multimap<String, String> multimap2 = Multimaps.synchronizedMultimap(HashMultimap.create());
		return baker.bake(new ErrorCollectingSpriteGetter() {
				private final Sprite missingSprite = ((SpriteAtlasManager.AtlasPreparation)atlasMap.get(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)).getMissingSprite();

				@Override
				public Sprite get(SpriteIdentifier id, SimpleModel model) {
					SpriteAtlasManager.AtlasPreparation atlasPreparation = (SpriteAtlasManager.AtlasPreparation)atlasMap.get(id.getAtlasId());
					Sprite sprite = atlasPreparation.getSprite(id.getTextureId());
					if (sprite != null) {
						return sprite;
					} else {
						multimap.put(model.name(), id);
						return atlasPreparation.getMissingSprite();
					}
				}

				@Override
				public Sprite getMissing(String name, SimpleModel model) {
					multimap2.put(model.name(), name);
					return this.missingSprite;
				}
			}, executor)
			.thenApply(
				models -> {
					multimap.asMap()
						.forEach(
							(modelName, sprites) -> LOGGER.warn(
								"Missing textures in model {}:\n{}",
								modelName,
								sprites.stream()
									.sorted(SpriteIdentifier.COMPARATOR)
									.map(spriteId -> "    " + spriteId.getAtlasId() + ":" + spriteId.getTextureId())
									.collect(Collectors.joining("\n"))
							)
						);
					multimap2.asMap()
						.forEach(
							(modelName, textureIds) -> LOGGER.warn(
								"Missing texture references in model {}:\n{}", modelName, textureIds.stream().sorted().map(string -> "    " + string).collect(Collectors.joining("\n"))
							)
						);
					Map<BlockState, BlockStateModel> map2 = toStateMap(models.blockStateModels(), models.missingModels().block());
					return new BakedModelManager.BakingResult(models, blockStates, map2, atlasMap, entityModels, blockEntityModels, completableFuture);
				}
			);
	}

	private static Map<BlockState, BlockStateModel> toStateMap(Map<BlockState, BlockStateModel> blockStateModels, BlockStateModel missingModel) {
		Object var8;
		try (ScopedProfiler scopedProfiler = Profilers.get().scoped("block state dispatch")) {
			Map<BlockState, BlockStateModel> map = new IdentityHashMap(blockStateModels);

			for (Block block : Registries.BLOCK) {
				block.getStateManager().getStates().forEach(state -> {
					if (blockStateModels.putIfAbsent(state, missingModel) == null) {
						LOGGER.warn("Missing model for variant: '{}'", state);
					}
				});
			}

			var8 = map;
		}

		return (Map<BlockState, BlockStateModel>)var8;
	}

	private static Object2IntMap<BlockState> group(BlockColors colors, BlockStatesLoader.LoadedModels definition) {
		Object2IntMap var3;
		try (ScopedProfiler scopedProfiler = Profilers.get().scoped("block groups")) {
			var3 = ModelGrouper.group(colors, definition);
		}

		return var3;
	}

	private void upload(BakedModelManager.BakingResult bakingResult, Profiler profiler) {
		profiler.push("upload");
		bakingResult.atlasPreparations.values().forEach(SpriteAtlasManager.AtlasPreparation::upload);
		ModelBaker.BakedModels bakedModels = bakingResult.bakedModels;
		this.bakedItemModels = bakedModels.itemStackModels();
		this.itemProperties = bakedModels.itemProperties();
		this.modelGroups = bakingResult.modelGroups;
		this.missingModels = bakedModels.missingModels();
		profiler.swap("cache");
		this.blockModelCache.setModels(bakingResult.modelCache);
		this.blockEntityModels = bakingResult.specialBlockModelRenderer;
		this.entityModels = bakingResult.entityModelSet;
		profiler.pop();
	}

	public boolean shouldRerender(BlockState from, BlockState to) {
		if (from == to) {
			return false;
		} else {
			int i = this.modelGroups.getInt(from);
			if (i != -1) {
				int j = this.modelGroups.getInt(to);
				if (i == j) {
					FluidState fluidState = from.getFluidState();
					FluidState fluidState2 = to.getFluidState();
					return fluidState != fluidState2;
				}
			}

			return true;
		}
	}

	public SpriteAtlasTexture getAtlas(Identifier id) {
		return this.atlasManager.getAtlas(id);
	}

	public void close() {
		this.atlasManager.close();
	}

	public void setMipmapLevels(int mipmapLevels) {
		this.mipmapLevels = mipmapLevels;
	}

	public Supplier<LoadedBlockEntityModels> getBlockEntityModelsSupplier() {
		return () -> this.blockEntityModels;
	}

	public Supplier<LoadedEntityModels> getEntityModelsSupplier() {
		return () -> this.entityModels;
	}

	@Environment(EnvType.CLIENT)
	record BakingResult(
		ModelBaker.BakedModels bakedModels,
		Object2IntMap<BlockState> modelGroups,
		Map<BlockState, BlockStateModel> modelCache,
		Map<Identifier, SpriteAtlasManager.AtlasPreparation> atlasPreparations,
		LoadedEntityModels entityModelSet,
		LoadedBlockEntityModels specialBlockModelRenderer,
		CompletableFuture<Void> readyForUpload
	) {
	}

	@Environment(EnvType.CLIENT)
	record Models(BakedSimpleModel missing, Map<Identifier, BakedSimpleModel> models) {
	}
}
