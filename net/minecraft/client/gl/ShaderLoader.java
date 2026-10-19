package net.minecraft.client.gl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceFinder;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.path.PathUtil;
import net.minecraft.util.profiler.Profiler;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class ShaderLoader extends SinglePreparationResourceReloader<ShaderLoader.Definitions> implements AutoCloseable {
	static final Logger LOGGER = LogUtils.getLogger();
	public static final int field_53936 = 32768;
	public static final String SHADERS_PATH = "shaders";
	private static final String INCLUDE_PATH = "shaders/include/";
	private static final ResourceFinder POST_EFFECT_FINDER = ResourceFinder.json("post_effect");
	final TextureManager textureManager;
	private final Consumer<Exception> onError;
	private ShaderLoader.Cache cache = new ShaderLoader.Cache(ShaderLoader.Definitions.EMPTY);

	public ShaderLoader(TextureManager textureManager, Consumer<Exception> onError) {
		this.textureManager = textureManager;
		this.onError = onError;
	}

	protected ShaderLoader.Definitions prepare(ResourceManager resourceManager, Profiler profiler) {
		Builder<ShaderLoader.ShaderSourceKey, String> builder = ImmutableMap.builder();
		Map<Identifier, Resource> map = resourceManager.findResources("shaders", ShaderLoader::isShaderSource);

		for (Entry<Identifier, Resource> entry : map.entrySet()) {
			Identifier identifier = (Identifier)entry.getKey();
			ShaderType shaderType = ShaderType.byLocation(identifier);
			if (shaderType != null) {
				loadShaderSource(identifier, (Resource)entry.getValue(), shaderType, map, builder);
			}
		}

		Builder<Identifier, PostEffectPipeline> builder2 = ImmutableMap.builder();

		for (Entry<Identifier, Resource> entry2 : POST_EFFECT_FINDER.findResources(resourceManager).entrySet()) {
			loadPostEffect((Identifier)entry2.getKey(), (Resource)entry2.getValue(), builder2);
		}

		return new ShaderLoader.Definitions(builder.build(), builder2.build());
	}

	private static void loadShaderSource(
		Identifier id, Resource resource, ShaderType type, Map<Identifier, Resource> allResources, Builder<ShaderLoader.ShaderSourceKey, String> builder
	) {
		Identifier identifier = type.idConverter().toResourceId(id);
		GlImportProcessor glImportProcessor = createImportProcessor(allResources, id);

		try {
			Reader reader = resource.getReader();

			try {
				String string = IOUtils.toString(reader);
				builder.put(new ShaderLoader.ShaderSourceKey(identifier, type), String.join("", glImportProcessor.readSource(string)));
			} catch (Throwable var11) {
				if (reader != null) {
					try {
						reader.close();
					} catch (Throwable var10) {
						var11.addSuppressed(var10);
					}
				}

				throw var11;
			}

			if (reader != null) {
				reader.close();
			}
		} catch (IOException var12) {
			LOGGER.error("Failed to load shader source at {}", id, var12);
		}
	}

	private static GlImportProcessor createImportProcessor(Map<Identifier, Resource> allResources, Identifier id) {
		final Identifier identifier = id.withPath(PathUtil::getPosixFullPath);
		return new GlImportProcessor() {
			private final Set<Identifier> processed = new ObjectArraySet<>();

			@Override
			public String loadImport(boolean inline, String name) {
				Identifier identifierx;
				try {
					if (inline) {
						identifierx = identifier.withPath((UnaryOperator<String>)(path -> PathUtil.normalizeToPosix(path + name)));
					} else {
						identifierx = Identifier.of(name).withPrefixedPath("shaders/include/");
					}
				} catch (InvalidIdentifierException var8) {
					ShaderLoader.LOGGER.error("Malformed GLSL import {}: {}", name, var8.getMessage());
					return "#error " + var8.getMessage();
				}

				if (!this.processed.add(identifierx)) {
					return null;
				} else {
					try {
						Reader reader = ((Resource)allResources.get(identifierx)).getReader();

						String var5;
						try {
							var5 = IOUtils.toString(reader);
						} catch (Throwable var9) {
							if (reader != null) {
								try {
									reader.close();
								} catch (Throwable var7) {
									var9.addSuppressed(var7);
								}
							}

							throw var9;
						}

						if (reader != null) {
							reader.close();
						}

						return var5;
					} catch (IOException var10) {
						ShaderLoader.LOGGER.error("Could not open GLSL import {}: {}", identifierx, var10.getMessage());
						return "#error " + var10.getMessage();
					}
				}
			}
		};
	}

	private static void loadPostEffect(Identifier id, Resource resource, Builder<Identifier, PostEffectPipeline> builder) {
		Identifier identifier = POST_EFFECT_FINDER.toResourceId(id);

		try {
			Reader reader = resource.getReader();

			try {
				JsonElement jsonElement = JsonParser.parseReader(reader);
				builder.put(identifier, PostEffectPipeline.CODEC.parse(JsonOps.INSTANCE, jsonElement).getOrThrow(JsonSyntaxException::new));
			} catch (Throwable var8) {
				if (reader != null) {
					try {
						reader.close();
					} catch (Throwable var7) {
						var8.addSuppressed(var7);
					}
				}

				throw var8;
			}

			if (reader != null) {
				reader.close();
			}
		} catch (JsonParseException | IOException var9) {
			LOGGER.error("Failed to parse post chain at {}", id, var9);
		}
	}

	private static boolean isShaderSource(Identifier id) {
		return ShaderType.byLocation(id) != null || id.getPath().endsWith(".glsl");
	}

	protected void apply(ShaderLoader.Definitions definitions, ResourceManager resourceManager, Profiler profiler) {
		ShaderLoader.Cache cache = new ShaderLoader.Cache(definitions);
		Set<RenderPipeline> set = new HashSet(RenderPipelines.getAll());
		List<Identifier> list = new ArrayList();
		GpuDevice gpuDevice = RenderSystem.getDevice();
		gpuDevice.clearPipelineCache();

		for (RenderPipeline renderPipeline : set) {
			CompiledRenderPipeline compiledRenderPipeline = gpuDevice.precompilePipeline(renderPipeline, cache::getSource);
			if (!compiledRenderPipeline.isValid()) {
				list.add(renderPipeline.getLocation());
			}
		}

		if (!list.isEmpty()) {
			gpuDevice.clearPipelineCache();
			throw new RuntimeException(
				"Failed to load required shader programs:\n" + (String)list.stream().map(identifier -> " - " + identifier).collect(Collectors.joining("\n"))
			);
		} else {
			this.cache.close();
			this.cache = cache;
		}
	}

	@Override
	public String getName() {
		return "Shader Loader";
	}

	private void handleError(Exception exception) {
		if (!this.cache.errorHandled) {
			this.onError.accept(exception);
			this.cache.errorHandled = true;
		}
	}

	@Nullable
	public PostEffectProcessor loadPostEffect(Identifier id, Set<Identifier> availableExternalTargets) {
		try {
			return this.cache.getOrLoadProcessor(id, availableExternalTargets);
		} catch (ShaderLoader.LoadException var4) {
			LOGGER.error("Failed to load post chain: {}", id, var4);
			this.cache.postEffectProcessors.put(id, Optional.empty());
			this.handleError(var4);
			return null;
		}
	}

	public void close() {
		this.cache.close();
	}

	public String getSource(Identifier id, ShaderType type) {
		return this.cache.getSource(id, type);
	}

	@Environment(EnvType.CLIENT)
	class Cache implements AutoCloseable {
		private final ShaderLoader.Definitions definitions;
		final Map<Identifier, Optional<PostEffectProcessor>> postEffectProcessors = new HashMap();
		boolean errorHandled;

		Cache(final ShaderLoader.Definitions definitions) {
			this.definitions = definitions;
		}

		@Nullable
		public PostEffectProcessor getOrLoadProcessor(Identifier id, Set<Identifier> availableExternalTargets) throws ShaderLoader.LoadException {
			Optional<PostEffectProcessor> optional = (Optional<PostEffectProcessor>)this.postEffectProcessors.get(id);
			if (optional != null) {
				return (PostEffectProcessor)optional.orElse(null);
			} else {
				PostEffectProcessor postEffectProcessor = this.loadProcessor(id, availableExternalTargets);
				this.postEffectProcessors.put(id, Optional.of(postEffectProcessor));
				return postEffectProcessor;
			}
		}

		private PostEffectProcessor loadProcessor(Identifier id, Set<Identifier> availableExternalTargets) throws ShaderLoader.LoadException {
			PostEffectPipeline postEffectPipeline = (PostEffectPipeline)this.definitions.postChains.get(id);
			if (postEffectPipeline == null) {
				throw new ShaderLoader.LoadException("Could not find post chain with id: " + id);
			} else {
				return PostEffectProcessor.parseEffect(postEffectPipeline, ShaderLoader.this.textureManager, availableExternalTargets, id);
			}
		}

		public void close() {
			this.postEffectProcessors.clear();
		}

		public String getSource(Identifier id, ShaderType type) {
			return (String)this.definitions.shaderSources.get(new ShaderLoader.ShaderSourceKey(id, type));
		}
	}

	@Environment(EnvType.CLIENT)
	public record Definitions(Map<ShaderLoader.ShaderSourceKey, String> shaderSources, Map<Identifier, PostEffectPipeline> postChains) {
		public static final ShaderLoader.Definitions EMPTY = new ShaderLoader.Definitions(Map.of(), Map.of());
	}

	@Environment(EnvType.CLIENT)
	public static class LoadException extends Exception {
		public LoadException(String message) {
			super(message);
		}
	}

	@Environment(EnvType.CLIENT)
	record ShaderSourceKey(Identifier id, ShaderType type) {
		public String toString() {
			return this.id + " (" + this.type + ")";
		}
	}
}
