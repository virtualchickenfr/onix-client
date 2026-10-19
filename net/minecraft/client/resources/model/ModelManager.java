package net.minecraft.client.resources.model;

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
import net.minecraft.Util;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SpecialBlockModelRenderer;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.Zone;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.slf4j.Logger;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager} injected by mod fabric-model-loading-api-v1</p>
 */
@Environment(EnvType.CLIENT)
public class ModelManager implements PreparableReloadListener, AutoCloseable, FabricBakedModelManager {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final FileToIdConverter MODEL_LISTER = FileToIdConverter.json("models");
	private static final Map<ResourceLocation, ResourceLocation> VANILLA_ATLASES = Map.of(
		Sheets.BANNER_SHEET,
		AtlasIds.BANNER_PATTERNS,
		Sheets.BED_SHEET,
		AtlasIds.BEDS,
		Sheets.CHEST_SHEET,
		AtlasIds.CHESTS,
		Sheets.SHIELD_SHEET,
		AtlasIds.SHIELD_PATTERNS,
		Sheets.SIGN_SHEET,
		AtlasIds.SIGNS,
		Sheets.SHULKER_SHEET,
		AtlasIds.SHULKER_BOXES,
		Sheets.ARMOR_TRIMS_SHEET,
		AtlasIds.ARMOR_TRIMS,
		Sheets.DECORATED_POT_SHEET,
		AtlasIds.DECORATED_POT,
		TextureAtlas.LOCATION_BLOCKS,
		AtlasIds.BLOCKS
	);
	private Map<ResourceLocation, ItemModel> bakedItemStackModels = Map.of();
	private Map<ResourceLocation, ClientItem.Properties> itemProperties = Map.of();
	private final AtlasSet atlases;
	private final BlockModelShaper blockModelShaper;
	private final BlockColors blockColors;
	private EntityModelSet entityModelSet = EntityModelSet.EMPTY;
	private SpecialBlockModelRenderer specialBlockModelRenderer = SpecialBlockModelRenderer.EMPTY;
	private int maxMipmapLevels;
	private ModelBakery.MissingModels missingModels;
	private Object2IntMap<BlockState> modelGroups = Object2IntMaps.emptyMap();

	public ModelManager(TextureManager textureManager, BlockColors blockColors, int i) {
		this.blockColors = blockColors;
		this.maxMipmapLevels = i;
		this.blockModelShaper = new BlockModelShaper(this);
		this.atlases = new AtlasSet(VANILLA_ATLASES, textureManager);
	}

	public BlockStateModel getMissingBlockStateModel() {
		return this.missingModels.block();
	}

	public ItemModel getItemModel(ResourceLocation resourceLocation) {
		return this.bakedItemStackModels.getOrDefault(resourceLocation, this.missingModels.item());
	}

	public ClientItem.Properties getItemProperties(ResourceLocation resourceLocation) {
		return this.itemProperties.getOrDefault(resourceLocation, ClientItem.Properties.DEFAULT);
	}

	public BlockModelShaper getBlockModelShaper() {
		return this.blockModelShaper;
	}

	@Override
	public final CompletableFuture<Void> reload(
		PreparableReloadListener.PreparationBarrier preparationBarrier, ResourceManager resourceManager, Executor executor, Executor executor2
	) {
		CompletableFuture<EntityModelSet> completableFuture = CompletableFuture.supplyAsync(EntityModelSet::vanilla, executor);
		CompletableFuture<SpecialBlockModelRenderer> completableFuture2 = completableFuture.thenApplyAsync(SpecialBlockModelRenderer::vanilla, executor);
		CompletableFuture<Map<ResourceLocation, UnbakedModel>> completableFuture3 = loadBlockModels(resourceManager, executor);
		CompletableFuture<BlockStateModelLoader.LoadedModels> completableFuture4 = BlockStateModelLoader.loadBlockStates(resourceManager, executor);
		CompletableFuture<ClientItemInfoLoader.LoadedClientInfos> completableFuture5 = ClientItemInfoLoader.scheduleLoad(resourceManager, executor);
		CompletableFuture<ModelManager.ResolvedModels> completableFuture6 = CompletableFuture.allOf(completableFuture3, completableFuture4, completableFuture5)
			.thenApplyAsync(void_ -> discoverModelDependencies(completableFuture3.join(), completableFuture4.join(), completableFuture5.join()), executor);
		CompletableFuture<Object2IntMap<BlockState>> completableFuture7 = completableFuture4.thenApplyAsync(
			loadedModels -> buildModelGroups(this.blockColors, loadedModels), executor
		);
		Map<ResourceLocation, CompletableFuture<AtlasSet.StitchResult>> map = this.atlases.scheduleLoad(resourceManager, this.maxMipmapLevels, executor);
		return CompletableFuture.allOf(
				Stream.concat(
						map.values().stream(),
						Stream.of(completableFuture6, completableFuture7, completableFuture4, completableFuture5, completableFuture, completableFuture2, completableFuture3)
					)
					.toArray(CompletableFuture[]::new)
			)
			.thenComposeAsync(
				void_ -> {
					Map<ResourceLocation, AtlasSet.StitchResult> map2 = Util.mapValues(map, CompletableFuture::join);
					ModelManager.ResolvedModels resolvedModels = completableFuture6.join();
					Object2IntMap<BlockState> object2IntMap = completableFuture7.join();
					Set<ResourceLocation> set = Sets.difference(completableFuture3.join().keySet(), resolvedModels.models.keySet());
					if (!set.isEmpty()) {
						LOGGER.debug("Unreferenced models: \n{}", set.stream().sorted().map(resourceLocation -> "\t" + resourceLocation + "\n").collect(Collectors.joining()));
					}

					ModelBakery modelBakery = new ModelBakery(
						completableFuture.join(), completableFuture4.join().models(), completableFuture5.join().contents(), resolvedModels.models(), resolvedModels.missing()
					);
					return loadModels(map2, modelBakery, object2IntMap, completableFuture.join(), completableFuture2.join(), executor);
				},
				executor
			)
			.thenCompose(reloadState -> reloadState.readyForUpload.thenApply(void_ -> (ModelManager.ReloadState)reloadState))
			.thenCompose(preparationBarrier::wait)
			.thenAcceptAsync(reloadState -> this.apply(reloadState, Profiler.get()), executor2);
	}

	private static CompletableFuture<Map<ResourceLocation, UnbakedModel>> loadBlockModels(ResourceManager resourceManager, Executor executor) {
		return CompletableFuture.<Map<ResourceLocation, Resource>>supplyAsync(() -> MODEL_LISTER.listMatchingResources(resourceManager), executor)
			.thenCompose(
				map -> {
					List<CompletableFuture<Pair<ResourceLocation, BlockModel>>> list = new ArrayList<>(map.size());

					for (Entry<ResourceLocation, Resource> entry : map.entrySet()) {
						list.add(CompletableFuture.supplyAsync(() -> {
							ResourceLocation resourceLocation = MODEL_LISTER.fileToId(entry.getKey());

							try (Reader reader = entry.getValue().openAsReader()) {
								return Pair.of(resourceLocation, BlockModel.fromStream(reader));
							} catch (Exception exception) {
								LOGGER.error("Failed to load model {}", entry.getKey(), exception);
								return null;
							}
						}, executor));
					}

					return Util.sequence(list)
						.thenApply(listx -> listx.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableMap(Pair::getFirst, Pair::getSecond)));
				}
			);
	}

	private static ModelManager.ResolvedModels discoverModelDependencies(
		Map<ResourceLocation, UnbakedModel> map, BlockStateModelLoader.LoadedModels loadedModels, ClientItemInfoLoader.LoadedClientInfos loadedClientInfos
	) {
		try (Zone zone = Profiler.get().zone("dependencies")) {
			ModelDiscovery modelDiscovery = new ModelDiscovery(map, MissingBlockModel.missingModel());
			modelDiscovery.addSpecialModel(ItemModelGenerator.GENERATED_ITEM_MODEL_ID, new ItemModelGenerator());
			loadedModels.models().values().forEach(modelDiscovery::addRoot);
			loadedClientInfos.contents().values().forEach(clientItem -> modelDiscovery.addRoot(clientItem.model()));
			return new ModelManager.ResolvedModels(modelDiscovery.missingModel(), modelDiscovery.resolve());
		}
	}

	private static CompletableFuture<ModelManager.ReloadState> loadModels(
		Map<ResourceLocation, AtlasSet.StitchResult> map,
		ModelBakery modelBakery,
		Object2IntMap<BlockState> object2IntMap,
		EntityModelSet entityModelSet,
		SpecialBlockModelRenderer specialBlockModelRenderer,
		Executor executor
	) {
		CompletableFuture<Void> completableFuture = CompletableFuture.allOf(
			map.values().stream().map(AtlasSet.StitchResult::readyForUpload).toArray(CompletableFuture[]::new)
		);
		final Multimap<String, Material> multimap = Multimaps.synchronizedMultimap(HashMultimap.create());
		final Multimap<String, String> multimap2 = Multimaps.synchronizedMultimap(HashMultimap.create());
		return modelBakery.bakeModels(new SpriteGetter() {
				private final TextureAtlasSprite missingSprite = map.get(TextureAtlas.LOCATION_BLOCKS).missing();

				@Override
				public TextureAtlasSprite get(Material material, ModelDebugName modelDebugName) {
					AtlasSet.StitchResult stitchResult = map.get(material.atlasLocation());
					TextureAtlasSprite textureAtlasSprite = stitchResult.getSprite(material.texture());
					if (textureAtlasSprite != null) {
						return textureAtlasSprite;
					}

					multimap.put(modelDebugName.debugName(), material);
					return stitchResult.missing();
				}

				@Override
				public TextureAtlasSprite reportMissingReference(String string, ModelDebugName modelDebugName) {
					multimap2.put(modelDebugName.debugName(), string);
					return this.missingSprite;
				}
			}, executor)
			.thenApply(
				bakingResult -> {
					multimap.asMap()
						.forEach(
							(string, collection) -> LOGGER.warn(
								"Missing textures in model {}:\n{}",
								string,
								collection.stream()
									.sorted(Material.COMPARATOR)
									.map(material -> "    " + material.atlasLocation() + ":" + material.texture())
									.collect(Collectors.joining("\n"))
							)
						);
					multimap2.asMap()
						.forEach(
							(string, collection) -> LOGGER.warn(
								"Missing texture references in model {}:\n{}", string, collection.stream().sorted().map(stringx -> "    " + stringx).collect(Collectors.joining("\n"))
							)
						);
					Map<BlockState, BlockStateModel> map2 = createBlockStateToModelDispatch(bakingResult.blockStateModels(), bakingResult.missingModels().block());
					return new ModelManager.ReloadState(bakingResult, object2IntMap, map2, map, entityModelSet, specialBlockModelRenderer, completableFuture);
				}
			);
	}

	private static Map<BlockState, BlockStateModel> createBlockStateToModelDispatch(Map<BlockState, BlockStateModel> map, BlockStateModel blockStateModel) {
		try (Zone zone = Profiler.get().zone("block state dispatch")) {
			Map<BlockState, BlockStateModel> map2 = new IdentityHashMap<>(map);

			for (Block block : BuiltInRegistries.BLOCK) {
				block.getStateDefinition().getPossibleStates().forEach(blockState -> {
					if (map.putIfAbsent(blockState, blockStateModel) == null) {
						LOGGER.warn("Missing model for variant: '{}'", blockState);
					}
				});
			}

			return map2;
		}
	}

	private static Object2IntMap<BlockState> buildModelGroups(BlockColors blockColors, BlockStateModelLoader.LoadedModels loadedModels) {
		try (Zone zone = Profiler.get().zone("block groups")) {
			return ModelGroupCollector.build(blockColors, loadedModels);
		}
	}

	private void apply(ModelManager.ReloadState reloadState, ProfilerFiller profilerFiller) {
		profilerFiller.push("upload");
		reloadState.atlasPreparations.values().forEach(AtlasSet.StitchResult::upload);
		ModelBakery.BakingResult bakingResult = reloadState.bakedModels;
		this.bakedItemStackModels = bakingResult.itemStackModels();
		this.itemProperties = bakingResult.itemProperties();
		this.modelGroups = reloadState.modelGroups;
		this.missingModels = bakingResult.missingModels();
		profilerFiller.popPush("cache");
		this.blockModelShaper.replaceCache(reloadState.modelCache);
		this.specialBlockModelRenderer = reloadState.specialBlockModelRenderer;
		this.entityModelSet = reloadState.entityModelSet;
		profilerFiller.pop();
	}

	public boolean requiresRender(BlockState blockState, BlockState blockState2) {
		if (blockState == blockState2) {
			return false;
		}

		int i = this.modelGroups.getInt(blockState);
		if (i != -1) {
			int j = this.modelGroups.getInt(blockState2);
			if (i == j) {
				FluidState fluidState = blockState.getFluidState();
				FluidState fluidState2 = blockState2.getFluidState();
				return fluidState != fluidState2;
			}
		}

		return true;
	}

	public TextureAtlas getAtlas(ResourceLocation resourceLocation) {
		return this.atlases.getAtlas(resourceLocation);
	}

	@Override
	public void close() {
		this.atlases.close();
	}

	public void updateMaxMipLevel(int i) {
		this.maxMipmapLevels = i;
	}

	public Supplier<SpecialBlockModelRenderer> specialBlockModelRenderer() {
		return () -> this.specialBlockModelRenderer;
	}

	public Supplier<EntityModelSet> entityModels() {
		return () -> this.entityModelSet;
	}

	@Environment(EnvType.CLIENT)
	record ReloadState(
		ModelBakery.BakingResult bakedModels,
		Object2IntMap<BlockState> modelGroups,
		Map<BlockState, BlockStateModel> modelCache,
		Map<ResourceLocation, AtlasSet.StitchResult> atlasPreparations,
		EntityModelSet entityModelSet,
		SpecialBlockModelRenderer specialBlockModelRenderer,
		CompletableFuture<Void> readyForUpload
	) {
	}

	@Environment(EnvType.CLIENT)
	record ResolvedModels(ResolvedModel missing, Map<ResourceLocation, ResolvedModel> models) {
	}
}
