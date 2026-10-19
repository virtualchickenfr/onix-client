package net.minecraft.client.render.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
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
import net.minecraft.client.model.ModelNameSupplier;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.block.BlockModels;
import net.minecraft.client.render.block.entity.LoadedBlockEntityModels;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.item.model.ItemModel;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.ModelIdentifier;
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
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class BakedModelManager implements ResourceReloader, AutoCloseable, FabricBakedModelManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final ResourceFinder MODELS_FINDER = ResourceFinder.json("models");
	private static final Map<Identifier, Identifier> LAYERS_TO_LOADERS = Map.of(
		TexturedRenderLayers.BANNER_PATTERNS_ATLAS_TEXTURE,
		Identifier.ofVanilla("banner_patterns"),
		TexturedRenderLayers.BEDS_ATLAS_TEXTURE,
		Identifier.ofVanilla("beds"),
		TexturedRenderLayers.CHEST_ATLAS_TEXTURE,
		Identifier.ofVanilla("chests"),
		TexturedRenderLayers.SHIELD_PATTERNS_ATLAS_TEXTURE,
		Identifier.ofVanilla("shield_patterns"),
		TexturedRenderLayers.SIGNS_ATLAS_TEXTURE,
		Identifier.ofVanilla("signs"),
		TexturedRenderLayers.SHULKER_BOXES_ATLAS_TEXTURE,
		Identifier.ofVanilla("shulker_boxes"),
		TexturedRenderLayers.ARMOR_TRIMS_ATLAS_TEXTURE,
		Identifier.ofVanilla("armor_trims"),
		TexturedRenderLayers.DECORATED_POT_ATLAS_TEXTURE,
		Identifier.ofVanilla("decorated_pot"),
		SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE,
		Identifier.ofVanilla("blocks")
	);
	private Map<ModelIdentifier, BakedModel> bakedBlockModels = Map.of();
	private Map<Identifier, ItemModel> bakedItemModels = Map.of();
	private Map<Identifier, ItemAsset.Properties> itemProperties = Map.of();
	private final SpriteAtlasManager atlasManager;
	private final BlockModels blockModelCache;
	private final BlockColors colorMap;
	private LoadedEntityModels entityModels = LoadedEntityModels.EMPTY;
	private LoadedBlockEntityModels blockEntityModels = LoadedBlockEntityModels.EMPTY;
	private int mipmapLevels;
	private BakedModel missingBlockModel;
	private ItemModel missingItemModel;
	private Object2IntMap<BlockState> modelGroups = Object2IntMaps.emptyMap();

	public BakedModelManager(TextureManager textureManager, BlockColors colorMap, int mipmap) {
		this.colorMap = colorMap;
		this.mipmapLevels = mipmap;
		this.blockModelCache = new BlockModels(this);
		this.atlasManager = new SpriteAtlasManager(LAYERS_TO_LOADERS, textureManager);
	}

	public BakedModel getModel(ModelIdentifier id) {
		return (BakedModel)this.bakedBlockModels.getOrDefault(id, this.missingBlockModel);
	}

	public BakedModel getMissingBlockModel() {
		return this.missingBlockModel;
	}

	public ItemModel getItemModel(Identifier id) {
		return (ItemModel)this.bakedItemModels.getOrDefault(id, this.missingItemModel);
	}

	public ItemAsset.Properties getItemProperties(Identifier id) {
		return (ItemAsset.Properties)this.itemProperties.getOrDefault(id, ItemAsset.Properties.DEFAULT);
	}

	public BlockModels getBlockModels() {
		return this.blockModelCache;
	}

	@Override
	public final CompletableFuture<Void> reload(
		ResourceReloader.Synchronizer synchronizer, ResourceManager manager, Executor prepareExecutor, Executor applyExecutor
	) {
		UnbakedModel unbakedModel = MissingModel.create();
		CompletableFuture<LoadedEntityModels> completableFuture = CompletableFuture.supplyAsync(LoadedEntityModels::copy, prepareExecutor);
		CompletableFuture<LoadedBlockEntityModels> completableFuture2 = completableFuture.thenApplyAsync(LoadedBlockEntityModels::fromModels, prepareExecutor);
		CompletableFuture<Map<Identifier, UnbakedModel>> completableFuture3 = reloadModels(manager, prepareExecutor);
		CompletableFuture<BlockStatesLoader.BlockStateDefinition> completableFuture4 = BlockStatesLoader.load(unbakedModel, manager, prepareExecutor);
		CompletableFuture<ItemAssetsLoader.Result> completableFuture5 = ItemAssetsLoader.load(manager, prepareExecutor);
		CompletableFuture<ReferencedModelsCollector> completableFuture6 = CompletableFuture.allOf(completableFuture3, completableFuture4, completableFuture5)
			.thenApplyAsync(
				v -> collect(
					unbakedModel,
					(Map<Identifier, UnbakedModel>)completableFuture3.join(),
					(BlockStatesLoader.BlockStateDefinition)completableFuture4.join(),
					(ItemAssetsLoader.Result)completableFuture5.join()
				),
				prepareExecutor
			);
		CompletableFuture<Object2IntMap<BlockState>> completableFuture7 = completableFuture4.thenApplyAsync(
			definition -> group(this.colorMap, definition), prepareExecutor
		);
		Map<Identifier, CompletableFuture<SpriteAtlasManager.AtlasPreparation>> map = this.atlasManager.reload(manager, this.mipmapLevels, prepareExecutor);
		return CompletableFuture.allOf(
				(CompletableFuture[])Stream.concat(
						map.values().stream(), Stream.of(completableFuture6, completableFuture7, completableFuture4, completableFuture5, completableFuture, completableFuture2)
					)
					.toArray(CompletableFuture[]::new)
			)
			.thenApplyAsync(
				v -> {
					Map<Identifier, SpriteAtlasManager.AtlasPreparation> map2 = (Map<Identifier, SpriteAtlasManager.AtlasPreparation>)map.entrySet()
						.stream()
						.collect(Collectors.toMap(Entry::getKey, entry -> (SpriteAtlasManager.AtlasPreparation)((CompletableFuture)entry.getValue()).join()));
					ReferencedModelsCollector referencedModelsCollector = (ReferencedModelsCollector)completableFuture6.join();
					Object2IntMap<BlockState> object2IntMap = (Object2IntMap<BlockState>)completableFuture7.join();
					Set<Identifier> set = referencedModelsCollector.getUnresolved();
					if (!set.isEmpty()) {
						LOGGER.debug("Unreferenced models: \n{}", set.stream().sorted().map(id -> "\t" + id + "\n").collect(Collectors.joining()));
					}

					ModelBaker modelBaker = new ModelBaker(
						(LoadedEntityModels)completableFuture.join(),
						((BlockStatesLoader.BlockStateDefinition)completableFuture4.join()).getModels(),
						((ItemAssetsLoader.Result)completableFuture5.join()).contents(),
						referencedModelsCollector.getResolvedModels(),
						unbakedModel
					);
					return bake(
						Profilers.get(), map2, modelBaker, object2IntMap, (LoadedEntityModels)completableFuture.join(), (LoadedBlockEntityModels)completableFuture2.join()
					);
				},
				prepareExecutor
			)
			.thenCompose(result -> result.readyForUpload.thenApply(void_ -> result))
			.thenCompose(synchronizer::whenPrepared)
			.thenAcceptAsync(bakingResult -> this.upload(bakingResult, Profilers.get()), applyExecutor);
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

	private static ReferencedModelsCollector collect(
		UnbakedModel missingModel, Map<Identifier, UnbakedModel> models, BlockStatesLoader.BlockStateDefinition blockStates, ItemAssetsLoader.Result itemAssets
	) {
		ReferencedModelsCollector referencedModelsCollector = new ReferencedModelsCollector(models, missingModel);
		blockStates.streamModels().forEach(referencedModelsCollector::add);
		itemAssets.contents().values().forEach(asset -> referencedModelsCollector.add(asset.model()));
		referencedModelsCollector.addGenerated();
		referencedModelsCollector.resolveAll();
		return referencedModelsCollector;
	}

	private static BakedModelManager.BakingResult bake(
		Profiler profiler,
		Map<Identifier, SpriteAtlasManager.AtlasPreparation> atlases,
		ModelBaker baker,
		Object2IntMap<BlockState> groups,
		LoadedEntityModels entityModels,
		LoadedBlockEntityModels blockEntityModels
	) {
		profiler.push("baking");
		final Multimap<String, SpriteIdentifier> multimap = HashMultimap.create();
		final Multimap<String, String> multimap2 = HashMultimap.create();
		final Sprite sprite = ((SpriteAtlasManager.AtlasPreparation)atlases.get(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)).getMissingSprite();
		ModelBaker.BakedModels bakedModels = baker.bake(new ModelBaker.ErrorCollectingSpriteGetter() {
			@Override
			public Sprite get(ModelNameSupplier modelNameSupplier, SpriteIdentifier spriteId) {
				SpriteAtlasManager.AtlasPreparation atlasPreparation = (SpriteAtlasManager.AtlasPreparation)atlases.get(spriteId.getAtlasId());
				Sprite spritex = atlasPreparation.getSprite(spriteId.getTextureId());
				if (spritex != null) {
					return spritex;
				} else {
					multimap.put((String)modelNameSupplier.get(), spriteId);
					return atlasPreparation.getMissingSprite();
				}
			}

			@Override
			public Sprite getMissing(ModelNameSupplier modelNameSupplier, String textureId) {
				multimap2.put((String)modelNameSupplier.get(), textureId);
				return sprite;
			}
		});
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
		profiler.swap("dispatch");
		Map<BlockState, BakedModel> map = toStateMap(bakedModels.blockStateModels(), bakedModels.missingModel());
		CompletableFuture<Void> completableFuture = CompletableFuture.allOf(
			(CompletableFuture[])atlases.values().stream().map(SpriteAtlasManager.AtlasPreparation::whenComplete).toArray(CompletableFuture[]::new)
		);
		profiler.pop();
		return new BakedModelManager.BakingResult(bakedModels, groups, map, atlases, entityModels, blockEntityModels, completableFuture);
	}

	private static Map<BlockState, BakedModel> toStateMap(Map<ModelIdentifier, BakedModel> blockStateModels, BakedModel missingModel) {
		Map<BlockState, BakedModel> map = new IdentityHashMap();

		for (Block block : Registries.BLOCK) {
			block.getStateManager().getStates().forEach(state -> {
				Identifier identifier = state.getBlock().getRegistryEntry().registryKey().getValue();
				ModelIdentifier modelIdentifier = BlockModels.getModelId(identifier, state);
				BakedModel bakedModel2 = (BakedModel)blockStateModels.get(modelIdentifier);
				if (bakedModel2 == null) {
					LOGGER.warn("Missing model for variant: '{}'", modelIdentifier);
					map.putIfAbsent(state, missingModel);
				} else {
					map.put(state, bakedModel2);
				}
			});
		}

		return map;
	}

	private static Object2IntMap<BlockState> group(BlockColors colors, BlockStatesLoader.BlockStateDefinition definition) {
		return ModelGrouper.group(colors, definition);
	}

	private void upload(BakedModelManager.BakingResult bakingResult, Profiler profiler) {
		profiler.push("upload");
		bakingResult.atlasPreparations.values().forEach(SpriteAtlasManager.AtlasPreparation::upload);
		ModelBaker.BakedModels bakedModels = bakingResult.bakedModels;
		this.bakedBlockModels = bakedModels.blockStateModels();
		this.bakedItemModels = bakedModels.itemStackModels();
		this.itemProperties = bakedModels.itemProperties();
		this.modelGroups = bakingResult.modelGroups;
		this.missingBlockModel = bakedModels.missingModel();
		this.missingItemModel = bakedModels.missingItemModel();
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
		Map<BlockState, BakedModel> modelCache,
		Map<Identifier, SpriteAtlasManager.AtlasPreparation> atlasPreparations,
		LoadedEntityModels entityModelSet,
		LoadedBlockEntityModels specialBlockModelRenderer,
		CompletableFuture<Void> readyForUpload
	) {
	}
}
