package net.minecraft.client.gl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.FramePass;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.Handle;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class PostEffectPass {
	private final String id;
	private final RenderPipeline pipeline;
	private final Identifier outputTargetId;
	private final List<PostEffectPipeline.Uniform> uniforms;
	private final List<PostEffectPass.Sampler> samplers = new ArrayList();

	public PostEffectPass(RenderPipeline pipeline, Identifier outputTargetId, List<PostEffectPipeline.Uniform> uniforms) {
		this.pipeline = pipeline;
		this.id = pipeline.getLocation().toString();
		this.outputTargetId = outputTargetId;
		this.uniforms = uniforms;
	}

	public void addSampler(PostEffectPass.Sampler sampler) {
		this.samplers.add(sampler);
	}

	public void render(
		FrameGraphBuilder builder, Map<Identifier, Handle<Framebuffer>> handles, Matrix4f viewMatrix, @Nullable Consumer<RenderPass> additionalUniformsSetter
	) {
		FramePass framePass = builder.createPass(this.id);

		for (PostEffectPass.Sampler sampler : this.samplers) {
			sampler.preRender(framePass, handles);
		}

		Handle<Framebuffer> handle = (Handle<Framebuffer>)handles.computeIfPresent(this.outputTargetId, (id, handlex) -> framePass.transfer(handlex));
		if (handle == null) {
			throw new IllegalStateException("Missing handle for target " + this.outputTargetId);
		} else {
			framePass.setRenderer(
				() -> {
					Framebuffer framebuffer = handle.get();
					RenderSystem.backupProjectionMatrix();
					RenderSystem.setProjectionMatrix(viewMatrix, ProjectionType.ORTHOGRAPHIC);
					GpuBuffer gpuBuffer = RenderSystem.getQuadVertexBuffer();
					RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
					GpuBuffer gpuBuffer2 = shapeIndexBuffer.getIndexBuffer(6);

					try (RenderPass renderPass = RenderSystem.getDevice()
							.createCommandEncoder()
							.createRenderPass(
								framebuffer.getColorAttachment(), OptionalInt.empty(), framebuffer.useDepthAttachment ? framebuffer.getDepthAttachment() : null, OptionalDouble.empty()
							)) {
						renderPass.setPipeline(this.pipeline);
						renderPass.setUniform("OutSize", (float)framebuffer.textureWidth, (float)framebuffer.textureHeight);
						renderPass.setVertexBuffer(0, gpuBuffer);
						renderPass.setIndexBuffer(gpuBuffer2, shapeIndexBuffer.getIndexType());

						for (PostEffectPass.Sampler samplerx : this.samplers) {
							samplerx.bindSampler(renderPass, handles);
						}

						if (additionalUniformsSetter != null) {
							additionalUniformsSetter.accept(renderPass);
						}

						for (PostEffectPipeline.Uniform uniform : this.uniforms) {
							uniform.apply(renderPass);
						}

						renderPass.drawIndexed(0, 6);
					}

					RenderSystem.restoreProjectionMatrix();

					for (PostEffectPass.Sampler sampler2 : this.samplers) {
						sampler2.postRender(handles);
					}
				}
			);
		}
	}

	@Environment(EnvType.CLIENT)
	public interface Sampler {
		void preRender(FramePass pass, Map<Identifier, Handle<Framebuffer>> internalTargets);

		void bindSampler(RenderPass pass, Map<Identifier, Handle<Framebuffer>> internalTargets);

		default void postRender(Map<Identifier, Handle<Framebuffer>> internalTargets) {
		}
	}

	@Environment(EnvType.CLIENT)
	public record TargetSampler(String samplerName, Identifier targetId, boolean depthBuffer, boolean bilinear) implements PostEffectPass.Sampler {
		private Handle<Framebuffer> getTarget(Map<Identifier, Handle<Framebuffer>> internalTargets) {
			Handle<Framebuffer> handle = (Handle<Framebuffer>)internalTargets.get(this.targetId);
			if (handle == null) {
				throw new IllegalStateException("Missing handle for target " + this.targetId);
			} else {
				return handle;
			}
		}

		@Override
		public void preRender(FramePass pass, Map<Identifier, Handle<Framebuffer>> internalTargets) {
			pass.dependsOn(this.getTarget(internalTargets));
		}

		@Override
		public void bindSampler(RenderPass pass, Map<Identifier, Handle<Framebuffer>> internalTargets) {
			Handle<Framebuffer> handle = this.getTarget(internalTargets);
			Framebuffer framebuffer = handle.get();
			framebuffer.setFilter(this.bilinear ? FilterMode.LINEAR : FilterMode.NEAREST);
			GpuTexture gpuTexture = this.depthBuffer ? framebuffer.getDepthAttachment() : framebuffer.getColorAttachment();
			if (gpuTexture == null) {
				throw new IllegalStateException("Missing " + (this.depthBuffer ? "depth" : "color") + "texture for target " + this.targetId);
			} else {
				pass.bindSampler(this.samplerName + "Sampler", gpuTexture);
				pass.setUniform(this.samplerName + "Size", (float)framebuffer.textureWidth, (float)framebuffer.textureHeight);
			}
		}

		@Override
		public void postRender(Map<Identifier, Handle<Framebuffer>> internalTargets) {
			if (this.bilinear) {
				this.getTarget(internalTargets).get().setFilter(FilterMode.NEAREST);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public record TextureSampler(String samplerName, AbstractTexture texture, int width, int height) implements PostEffectPass.Sampler {
		@Override
		public void preRender(FramePass pass, Map<Identifier, Handle<Framebuffer>> internalTargets) {
		}

		@Override
		public void bindSampler(RenderPass pass, Map<Identifier, Handle<Framebuffer>> internalTargets) {
			pass.bindSampler(this.samplerName + "Sampler", this.texture.getGlTexture());
			pass.setUniform(this.samplerName + "Size", (float)this.width, (float)this.height);
		}
	}
}
