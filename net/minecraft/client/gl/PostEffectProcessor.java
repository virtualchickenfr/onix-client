package net.minecraft.client.gl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.Handle;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class PostEffectProcessor {
	public static final Identifier MAIN = Identifier.ofVanilla("main");
	private final List<PostEffectPass> passes;
	private final Map<Identifier, PostEffectPipeline.Targets> internalTargets;
	private final Set<Identifier> externalTargets;

	private PostEffectProcessor(List<PostEffectPass> passes, Map<Identifier, PostEffectPipeline.Targets> internalTargets, Set<Identifier> externalTargets) {
		this.passes = passes;
		this.internalTargets = internalTargets;
		this.externalTargets = externalTargets;
	}

	public static PostEffectProcessor parseEffect(
		PostEffectPipeline pipeline, TextureManager textureManager, Set<Identifier> availableExternalTargets, Identifier id
	) throws ShaderLoader.LoadException {
		Stream<Identifier> stream = pipeline.passes().stream().flatMap(PostEffectPipeline.Pass::streamTargets);
		Set<Identifier> set = (Set<Identifier>)stream.filter(target -> !pipeline.internalTargets().containsKey(target)).collect(Collectors.toSet());
		Set<Identifier> set2 = Sets.<Identifier>difference(set, availableExternalTargets);
		if (!set2.isEmpty()) {
			throw new ShaderLoader.LoadException("Referenced external targets are not available in this context: " + set2);
		} else {
			Builder<PostEffectPass> builder = ImmutableList.builder();

			for (int i = 0; i < pipeline.passes().size(); i++) {
				PostEffectPipeline.Pass pass = (PostEffectPipeline.Pass)pipeline.passes().get(i);
				builder.add(parsePass(textureManager, pass, id.withSuffixedPath("/" + i)));
			}

			return new PostEffectProcessor(builder.build(), pipeline.internalTargets(), set);
		}
	}

	private static PostEffectPass parsePass(TextureManager textureManager, PostEffectPipeline.Pass pass, Identifier id) throws ShaderLoader.LoadException {
		RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.POST_EFFECT_PROCESSOR_SNIPPET)
			.withFragmentShader(pass.fragmentShaderId())
			.withVertexShader(pass.vertexShaderId())
			.withLocation(id);

		for (PostEffectPipeline.Input input : pass.inputs()) {
			builder.withSampler(input.samplerName() + "Sampler");
			builder.withUniform(input.samplerName() + "Size", UniformType.VEC2);
		}

		for (PostEffectPipeline.Uniform uniform : pass.uniforms()) {
			builder.withUniform(uniform.name(), (UniformType)Objects.requireNonNull((UniformType)UniformType.CODEC.byId(uniform.type())));
		}

		RenderPipeline renderPipeline = builder.build();
		CompiledRenderPipeline compiledRenderPipeline = RenderSystem.getDevice().precompilePipeline(renderPipeline);

		for (PostEffectPipeline.Uniform uniform2 : pass.uniforms()) {
			String string = uniform2.name();
			if (!compiledRenderPipeline.containsUniform(string)) {
				throw new ShaderLoader.LoadException("Uniform '" + string + "' does not exist for " + id);
			}
		}

		PostEffectPass postEffectPass = new PostEffectPass(renderPipeline, pass.outputTarget(), pass.uniforms());

		for (PostEffectPipeline.Input input2 : pass.inputs()) {
			switch (input2) {
				case PostEffectPipeline.TextureSampler(String var39, Identifier var40, int var41, int var42, boolean var43):
					AbstractTexture abstractTexture = textureManager.getTexture(var40.withPath((UnaryOperator<String>)(name -> "textures/effect/" + name + ".png")));
					abstractTexture.setFilter(var43, false);
					postEffectPass.addSampler(new PostEffectPass.TextureSampler(var39, abstractTexture, var41, var42));
					break;
				case PostEffectPipeline.TargetSampler(String var22, Identifier var45, boolean var46, boolean var47):
					postEffectPass.addSampler(new PostEffectPass.TargetSampler(var22, var45, var46, var47));
					break;
				default:
					throw new MatchException(null, null);
			}
		}

		return postEffectPass;
	}

	public void render(
		FrameGraphBuilder builder,
		int textureWidth,
		int textureHeight,
		PostEffectProcessor.FramebufferSet framebufferSet,
		@Nullable Consumer<RenderPass> additionalUniformsSetter
	) {
		Matrix4f matrix4f = new Matrix4f().setOrtho(0.0F, textureWidth, 0.0F, textureHeight, 0.1F, 1000.0F);
		Map<Identifier, Handle<Framebuffer>> map = new HashMap(this.internalTargets.size() + this.externalTargets.size());

		for (Identifier identifier : this.externalTargets) {
			map.put(identifier, framebufferSet.getOrThrow(identifier));
		}

		for (Entry<Identifier, PostEffectPipeline.Targets> entry : this.internalTargets.entrySet()) {
			Identifier identifier2 = (Identifier)entry.getKey();

			SimpleFramebufferFactory simpleFramebufferFactory = switch ((PostEffectPipeline.Targets)entry.getValue()) {
				case PostEffectPipeline.CustomSized(int var26, int var27) -> new SimpleFramebufferFactory(var26, var27, true, 0);
				case PostEffectPipeline.ScreenSized var17 -> new SimpleFramebufferFactory(textureWidth, textureHeight, true, 0);
				default -> throw new MatchException(null, null);
			};
			map.put(identifier2, builder.createResourceHandle(identifier2.toString(), simpleFramebufferFactory));
		}

		for (PostEffectPass postEffectPass : this.passes) {
			postEffectPass.render(builder, map, matrix4f, additionalUniformsSetter);
		}

		for (Identifier identifier : this.externalTargets) {
			framebufferSet.set(identifier, (Handle<Framebuffer>)map.get(identifier));
		}
	}

	@Deprecated
	public void render(Framebuffer framebuffer, ObjectAllocator objectAllocator, @Nullable Consumer<RenderPass> additionalUniformsSetter) {
		FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
		PostEffectProcessor.FramebufferSet framebufferSet = PostEffectProcessor.FramebufferSet.singleton(
			MAIN, frameGraphBuilder.createObjectNode("main", framebuffer)
		);
		this.render(frameGraphBuilder, framebuffer.textureWidth, framebuffer.textureHeight, framebufferSet, additionalUniformsSetter);
		frameGraphBuilder.run(objectAllocator);
	}

	@Environment(EnvType.CLIENT)
	public interface FramebufferSet {
		static PostEffectProcessor.FramebufferSet singleton(Identifier id, Handle<Framebuffer> framebuffer) {
			return new PostEffectProcessor.FramebufferSet() {
				private Handle<Framebuffer> framebuffer = framebuffer;

				@Override
				public void set(Identifier id, Handle<Framebuffer> framebuffer) {
					if (id.equals(id)) {
						this.framebuffer = framebuffer;
					} else {
						throw new IllegalArgumentException("No target with id " + id);
					}
				}

				@Nullable
				@Override
				public Handle<Framebuffer> get(Identifier id) {
					return id.equals(id) ? this.framebuffer : null;
				}
			};
		}

		void set(Identifier id, Handle<Framebuffer> framebuffer);

		@Nullable
		Handle<Framebuffer> get(Identifier id);

		default Handle<Framebuffer> getOrThrow(Identifier id) {
			Handle<Framebuffer> handle = this.get(id);
			if (handle == null) {
				throw new IllegalArgumentException("Missing target with id " + id);
			} else {
				return handle;
			}
		}
	}
}
