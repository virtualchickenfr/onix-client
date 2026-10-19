package net.minecraft.client.renderer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.FileUtil;
import net.minecraft.ResourceLocationException;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.StrictJsonParser;
import net.minecraft.util.profiling.ProfilerFiller;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ShaderManager extends SimplePreparableReloadListener<ShaderManager.Configs> implements AutoCloseable {
	static final Logger LOGGER = LogUtils.getLogger();
	public static final int MAX_LOG_LENGTH = 32768;
	public static final String SHADER_PATH = "shaders";
	private static final String SHADER_INCLUDE_PATH = "shaders/include/";
	private static final FileToIdConverter POST_CHAIN_ID_CONVERTER = FileToIdConverter.json("post_effect");
	final TextureManager textureManager;
	private final Consumer<Exception> recoveryHandler;
	private ShaderManager.CompilationCache compilationCache = new ShaderManager.CompilationCache(ShaderManager.Configs.EMPTY);
	final CachedOrthoProjectionMatrixBuffer postChainProjectionMatrixBuffer = new CachedOrthoProjectionMatrixBuffer("post", 0.1F, 1000.0F, false);

	public ShaderManager(TextureManager textureManager, Consumer<Exception> consumer) {
		this.textureManager = textureManager;
		this.recoveryHandler = consumer;
	}

	protected ShaderManager.Configs prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		Builder<ShaderManager.ShaderSourceKey, String> builder = ImmutableMap.builder();
		Map<ResourceLocation, Resource> map = resourceManager.listResources("shaders", ShaderManager::isShader);

		for (Entry<ResourceLocation, Resource> entry : map.entrySet()) {
			ResourceLocation resourceLocation = entry.getKey();
			ShaderType shaderType = ShaderType.byLocation(resourceLocation);
			if (shaderType != null) {
				loadShader(resourceLocation, entry.getValue(), shaderType, map, builder);
			}
		}

		Builder<ResourceLocation, PostChainConfig> builder2 = ImmutableMap.builder();

		for (Entry<ResourceLocation, Resource> entry2 : POST_CHAIN_ID_CONVERTER.listMatchingResources(resourceManager).entrySet()) {
			loadPostChain(entry2.getKey(), entry2.getValue(), builder2);
		}

		return new ShaderManager.Configs(builder.build(), builder2.build());
	}

	private static void loadShader(
		ResourceLocation resourceLocation,
		Resource resource,
		ShaderType shaderType,
		Map<ResourceLocation, Resource> map,
		Builder<ShaderManager.ShaderSourceKey, String> builder
	) {
		ResourceLocation resourceLocation2 = shaderType.idConverter().fileToId(resourceLocation);
		GlslPreprocessor glslPreprocessor = createPreprocessor(map, resourceLocation);

		try (Reader reader = resource.openAsReader()) {
			String string = IOUtils.toString(reader);
			builder.put(new ShaderManager.ShaderSourceKey(resourceLocation2, shaderType), String.join("", glslPreprocessor.process(string)));
		} catch (IOException iOException) {
			LOGGER.error("Failed to load shader source at {}", resourceLocation, iOException);
		}
	}

	private static GlslPreprocessor createPreprocessor(Map<ResourceLocation, Resource> map, ResourceLocation resourceLocation) {
		final ResourceLocation resourceLocation2 = resourceLocation.withPath(FileUtil::getFullResourcePath);
		return new GlslPreprocessor() {
			private final Set<ResourceLocation> importedLocations = new ObjectArraySet<>();

			@Override
			public String applyImport(boolean bl, String string) {
				ResourceLocation resourceLocationx;
				try {
					if (bl) {
						resourceLocationx = resourceLocation2.withPath(string2 -> FileUtil.normalizeResourcePath(string2 + string));
					} else {
						resourceLocationx = ResourceLocation.parse(string).withPrefix("shaders/include/");
					}
				} catch (ResourceLocationException resourceLocationException) {
					ShaderManager.LOGGER.error("Malformed GLSL import {}: {}", string, resourceLocationException.getMessage());
					return "#error " + resourceLocationException.getMessage();
				}

				if (!this.importedLocations.add(resourceLocationx)) {
					return null;
				}

				try (Reader reader = map.get(resourceLocationx).openAsReader()) {
					return IOUtils.toString(reader);
				} catch (IOException iOException) {
					ShaderManager.LOGGER.error("Could not open GLSL import {}: {}", resourceLocationx, iOException.getMessage());
					return "#error " + iOException.getMessage();
				}
			}
		};
	}

	private static void loadPostChain(ResourceLocation resourceLocation, Resource resource, Builder<ResourceLocation, PostChainConfig> builder) {
		ResourceLocation resourceLocation2 = POST_CHAIN_ID_CONVERTER.fileToId(resourceLocation);

		try (Reader reader = resource.openAsReader()) {
			JsonElement jsonElement = StrictJsonParser.parse(reader);
			builder.put(resourceLocation2, PostChainConfig.CODEC.parse(JsonOps.INSTANCE, jsonElement).getOrThrow(JsonSyntaxException::new));
		} catch (IOException | JsonParseException exception) {
			LOGGER.error("Failed to parse post chain at {}", resourceLocation, exception);
		}
	}

	private static boolean isShader(ResourceLocation resourceLocation) {
		return ShaderType.byLocation(resourceLocation) != null || resourceLocation.getPath().endsWith(".glsl");
	}

	protected void apply(ShaderManager.Configs configs, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
		ShaderManager.CompilationCache compilationCache = new ShaderManager.CompilationCache(configs);
		Set<RenderPipeline> set = new HashSet<>(RenderPipelines.getStaticPipelines());
		List<ResourceLocation> list = new ArrayList<>();
		GpuDevice gpuDevice = RenderSystem.getDevice();
		gpuDevice.clearPipelineCache();

		for (RenderPipeline renderPipeline : set) {
			CompiledRenderPipeline compiledRenderPipeline = gpuDevice.precompilePipeline(renderPipeline, compilationCache::getShaderSource);
			if (!compiledRenderPipeline.isValid()) {
				list.add(renderPipeline.getLocation());
			}
		}

		if (!list.isEmpty()) {
			gpuDevice.clearPipelineCache();
			throw new RuntimeException(
				"Failed to load required shader programs:\n" + list.stream().map(resourceLocation -> " - " + resourceLocation).collect(Collectors.joining("\n"))
			);
		}

		this.compilationCache.close();
		this.compilationCache = compilationCache;
	}

	@Override
	public String getName() {
		return "Shader Loader";
	}

	private void tryTriggerRecovery(Exception exception) {
		if (!this.compilationCache.triggeredRecovery) {
			this.recoveryHandler.accept(exception);
			this.compilationCache.triggeredRecovery = true;
		}
	}

	@Nullable
	public PostChain getPostChain(ResourceLocation resourceLocation, Set<ResourceLocation> set) {
		try {
			return this.compilationCache.getOrLoadPostChain(resourceLocation, set);
		} catch (ShaderManager.CompilationException compilationException) {
			LOGGER.error("Failed to load post chain: {}", resourceLocation, compilationException);
			this.compilationCache.postChains.put(resourceLocation, Optional.empty());
			this.tryTriggerRecovery(compilationException);
			return null;
		}
	}

	@Override
	public void close() {
		this.compilationCache.close();
		this.postChainProjectionMatrixBuffer.close();
	}

	public String getShader(ResourceLocation resourceLocation, ShaderType shaderType) {
		return this.compilationCache.getShaderSource(resourceLocation, shaderType);
	}

	@Environment(EnvType.CLIENT)
	class CompilationCache implements AutoCloseable {
		private final ShaderManager.Configs configs;
		final Map<ResourceLocation, Optional<PostChain>> postChains = new HashMap<>();
		boolean triggeredRecovery;

		CompilationCache(final ShaderManager.Configs configs) {
			this.configs = configs;
		}

		@Nullable
		public PostChain getOrLoadPostChain(ResourceLocation resourceLocation, Set<ResourceLocation> set) throws ShaderManager.CompilationException {
			Optional<PostChain> optional = this.postChains.get(resourceLocation);
			if (optional != null) {
				return optional.orElse(null);
			}

			PostChain postChain = this.loadPostChain(resourceLocation, set);
			this.postChains.put(resourceLocation, Optional.of(postChain));
			return postChain;
		}

		private PostChain loadPostChain(ResourceLocation resourceLocation, Set<ResourceLocation> set) throws ShaderManager.CompilationException {
			PostChainConfig postChainConfig = this.configs.postChains.get(resourceLocation);
			if (postChainConfig == null) {
				throw new ShaderManager.CompilationException("Could not find post chain with id: " + resourceLocation);
			} else {
				return PostChain.load(postChainConfig, ShaderManager.this.textureManager, set, resourceLocation, ShaderManager.this.postChainProjectionMatrixBuffer);
			}
		}

		@Override
		public void close() {
			this.postChains.values().forEach(optional -> optional.ifPresent(PostChain::close));
			this.postChains.clear();
		}

		public String getShaderSource(ResourceLocation resourceLocation, ShaderType shaderType) {
			return this.configs.shaderSources.get(new ShaderManager.ShaderSourceKey(resourceLocation, shaderType));
		}
	}

	@Environment(EnvType.CLIENT)
	public static class CompilationException extends Exception {
		public CompilationException(String string) {
			super(string);
		}
	}

	@Environment(EnvType.CLIENT)
	public record Configs(Map<ShaderManager.ShaderSourceKey, String> shaderSources, Map<ResourceLocation, PostChainConfig> postChains) {
		public static final ShaderManager.Configs EMPTY = new ShaderManager.Configs(Map.of(), Map.of());
	}

	@Environment(EnvType.CLIENT)
	record ShaderSourceKey(ResourceLocation id, ShaderType type) {
		@Override
		public String toString() {
			return this.id + " (" + this.type + ")";
		}
	}
}
