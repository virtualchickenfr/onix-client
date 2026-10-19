package net.minecraft.client.gl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
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
		PostEffectPipeline pipeline, TextureManager textureManager, ShaderLoader shaderLoader, Set<Identifier> availableExternalTargets
	) throws ShaderLoader.LoadException {
		Stream<Identifier> stream = pipeline.passes().stream().flatMap(passx -> passx.inputs().stream()).flatMap(input -> input.getTargetId().stream());
		Set<Identifier> set = (Set<Identifier>)stream.filter(target -> !pipeline.internalTargets().containsKey(target)).collect(Collectors.toSet());
		Set<Identifier> set2 = Sets.<Identifier>difference(set, availableExternalTargets);
		if (!set2.isEmpty()) {
			throw new ShaderLoader.LoadException("Referenced external targets are not available in this context: " + set2);
		} else {
			Builder<PostEffectPass> builder = ImmutableList.builder();

			for (PostEffectPipeline.Pass pass : pipeline.passes()) {
				builder.add(parsePass(textureManager, shaderLoader, pass));
			}

			return new PostEffectProcessor(builder.build(), pipeline.internalTargets(), set);
		}
	}

	private static PostEffectPass parsePass(TextureManager textureManager, ShaderLoader shaderLoader, PostEffectPipeline.Pass pass) throws ShaderLoader.LoadException {
		ShaderProgram shaderProgram = shaderLoader.getProgramToLoad(pass.getShaderProgramKey());

		for (PostEffectPipeline.Uniform uniform : pass.uniforms()) {
			String string = uniform.name();
			if (shaderProgram.getUniform(string) == null) {
				throw new ShaderLoader.LoadException("Uniform '" + string + "' does not exist for " + pass.programId());
			}
		}

		String string2 = pass.programId().toString();
		PostEffectPass postEffectPass = new PostEffectPass(string2, shaderProgram, pass.outputTarget(), pass.uniforms());

		for (PostEffectPipeline.Input input : pass.inputs()) {
			switch (input) {
				case PostEffectPipeline.TextureSampler(String var34, Identifier var35, int var36, int var37, boolean var38):
					AbstractTexture abstractTexture = textureManager.getTexture(var35.withPath((UnaryOperator<String>)(name -> "textures/effect/" + name + ".png")));
					abstractTexture.setFilter(var38, false);
					postEffectPass.addSampler(new PostEffectPass.TextureSampler(var34, abstractTexture, var36, var37));
					break;
				case PostEffectPipeline.TargetSampler(String var21, Identifier var40, boolean var41, boolean var42):
					postEffectPass.addSampler(new PostEffectPass.TargetSampler(var21, var40, var41, var42));
					break;
				default:
					throw new MatchException(null, null);
			}
		}

		return postEffectPass;
	}

	public void render(FrameGraphBuilder builder, int textureWidth, int textureHeight, PostEffectProcessor.FramebufferSet framebufferSet) {
		Matrix4f matrix4f = new Matrix4f().setOrtho(0.0F, textureWidth, 0.0F, textureHeight, 0.1F, 1000.0F);
		Map<Identifier, Handle<Framebuffer>> map = new HashMap(this.internalTargets.size() + this.externalTargets.size());

		for (Identifier identifier : this.externalTargets) {
			map.put(identifier, framebufferSet.getOrThrow(identifier));
		}

		for (Entry<Identifier, PostEffectPipeline.Targets> entry : this.internalTargets.entrySet()) {
			Identifier identifier2 = (Identifier)entry.getKey();

			SimpleFramebufferFactory simpleFramebufferFactory = switch ((PostEffectPipeline.Targets)entry.getValue()) {
				case PostEffectPipeline.CustomSized(int var25, int var26) -> new SimpleFramebufferFactory(var25, var26, true);
				case PostEffectPipeline.ScreenSized var16 -> new SimpleFramebufferFactory(textureWidth, textureHeight, true);
				default -> throw new MatchException(null, null);
			};
			map.put(identifier2, builder.createResourceHandle(identifier2.toString(), simpleFramebufferFactory));
		}

		for (PostEffectPass postEffectPass : this.passes) {
			postEffectPass.render(builder, map, matrix4f);
		}

		for (Identifier identifier : this.externalTargets) {
			framebufferSet.set(identifier, (Handle<Framebuffer>)map.get(identifier));
		}
	}

	@Deprecated
	public void render(Framebuffer framebuffer, ObjectAllocator objectAllocator) {
		FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
		PostEffectProcessor.FramebufferSet framebufferSet = PostEffectProcessor.FramebufferSet.singleton(
			MAIN, frameGraphBuilder.createObjectNode("main", framebuffer)
		);
		this.render(frameGraphBuilder, framebuffer.textureWidth, framebuffer.textureHeight, framebufferSet);
		frameGraphBuilder.run(objectAllocator);
	}

	public void setUniforms(String name, float value) {
		for (PostEffectPass postEffectPass : this.passes) {
			postEffectPass.getProgram().getUniformOrDefault(name).set(value);
		}
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
