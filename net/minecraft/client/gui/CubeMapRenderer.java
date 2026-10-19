package net.minecraft.client.gui;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.ProjectionType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

@Environment(EnvType.CLIENT)
public class CubeMapRenderer {
	private static final int FACES_COUNT = 6;
	@Nullable
	private GpuBuffer buffer = null;
	private final List<Identifier> faces;

	public CubeMapRenderer(Identifier faces) {
		this.faces = IntStream.range(0, 6).mapToObj(face -> faces.withPath(faces.getPath() + "_" + face + ".png")).toList();
	}

	public void draw(MinecraftClient client, float x, float y, float alpha) {
		if (this.buffer == null) {
			this.upload();
		}

		Matrix4f matrix4f = new Matrix4f()
			.setPerspective(1.4835298F, (float)client.getWindow().getFramebufferWidth() / client.getWindow().getFramebufferHeight(), 0.05F, 10.0F);
		RenderSystem.backupProjectionMatrix();
		RenderSystem.setProjectionMatrix(matrix4f, ProjectionType.PERSPECTIVE);
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		matrix4fStack.rotationX((float) Math.PI);
		int i = 2;
		RenderPipeline renderPipeline = RenderPipelines.POSITION_TEX_PANORAMA;
		Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
		GpuTexture gpuTexture = framebuffer.getColorAttachment();
		GpuTexture gpuTexture2 = framebuffer.getDepthAttachment();
		RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
		GpuBuffer gpuBuffer = shapeIndexBuffer.getIndexBuffer(36);

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(gpuTexture, OptionalInt.empty(), gpuTexture2, OptionalDouble.empty())) {
			renderPass.setPipeline(renderPipeline);
			renderPass.setVertexBuffer(0, this.buffer);
			renderPass.setIndexBuffer(gpuBuffer, shapeIndexBuffer.getIndexType());

			for (int j = 0; j < 4; j++) {
				matrix4fStack.pushMatrix();
				float f = (j % 2 / 2.0F - 0.5F) / 256.0F;
				float g = (j / 2 / 2.0F - 0.5F) / 256.0F;
				float h = 0.0F;
				matrix4fStack.translate(f, g, 0.0F);
				matrix4fStack.rotateX(x * (float) (Math.PI / 180.0));
				matrix4fStack.rotateY(y * (float) (Math.PI / 180.0));
				RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha / (j + 1));

				for (int k = 0; k < 6; k++) {
					renderPass.bindSampler("Sampler0", client.getTextureManager().getTexture((Identifier)this.faces.get(k)).getGlTexture());
					renderPass.drawIndexed(6 * k, 6);
				}

				matrix4fStack.popMatrix();
			}
		}

		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.restoreProjectionMatrix();
		matrix4fStack.popMatrix();
	}

	private void upload() {
		this.buffer = RenderSystem.getDevice()
			.createBuffer(() -> "Cube map vertex buffer", BufferType.VERTICES, BufferUsage.DYNAMIC_WRITE, 24 * VertexFormats.POSITION_TEXTURE.getVertexSize());

		try (BufferAllocator bufferAllocator = new BufferAllocator(VertexFormats.POSITION_TEXTURE.getVertexSize() * 4)) {
			BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
			bufferBuilder.vertex(-1.0F, -1.0F, 1.0F).texture(0.0F, 0.0F);
			bufferBuilder.vertex(-1.0F, 1.0F, 1.0F).texture(0.0F, 1.0F);
			bufferBuilder.vertex(1.0F, 1.0F, 1.0F).texture(1.0F, 1.0F);
			bufferBuilder.vertex(1.0F, -1.0F, 1.0F).texture(1.0F, 0.0F);
			bufferBuilder.vertex(1.0F, -1.0F, 1.0F).texture(0.0F, 0.0F);
			bufferBuilder.vertex(1.0F, 1.0F, 1.0F).texture(0.0F, 1.0F);
			bufferBuilder.vertex(1.0F, 1.0F, -1.0F).texture(1.0F, 1.0F);
			bufferBuilder.vertex(1.0F, -1.0F, -1.0F).texture(1.0F, 0.0F);
			bufferBuilder.vertex(1.0F, -1.0F, -1.0F).texture(0.0F, 0.0F);
			bufferBuilder.vertex(1.0F, 1.0F, -1.0F).texture(0.0F, 1.0F);
			bufferBuilder.vertex(-1.0F, 1.0F, -1.0F).texture(1.0F, 1.0F);
			bufferBuilder.vertex(-1.0F, -1.0F, -1.0F).texture(1.0F, 0.0F);
			bufferBuilder.vertex(-1.0F, -1.0F, -1.0F).texture(0.0F, 0.0F);
			bufferBuilder.vertex(-1.0F, 1.0F, -1.0F).texture(0.0F, 1.0F);
			bufferBuilder.vertex(-1.0F, 1.0F, 1.0F).texture(1.0F, 1.0F);
			bufferBuilder.vertex(-1.0F, -1.0F, 1.0F).texture(1.0F, 0.0F);
			bufferBuilder.vertex(-1.0F, -1.0F, -1.0F).texture(0.0F, 0.0F);
			bufferBuilder.vertex(-1.0F, -1.0F, 1.0F).texture(0.0F, 1.0F);
			bufferBuilder.vertex(1.0F, -1.0F, 1.0F).texture(1.0F, 1.0F);
			bufferBuilder.vertex(1.0F, -1.0F, -1.0F).texture(1.0F, 0.0F);
			bufferBuilder.vertex(-1.0F, 1.0F, 1.0F).texture(0.0F, 0.0F);
			bufferBuilder.vertex(-1.0F, 1.0F, -1.0F).texture(0.0F, 1.0F);
			bufferBuilder.vertex(1.0F, 1.0F, -1.0F).texture(1.0F, 1.0F);
			bufferBuilder.vertex(1.0F, 1.0F, 1.0F).texture(1.0F, 0.0F);

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
				commandEncoder.writeToBuffer(this.buffer, builtBuffer.getBuffer(), 0);
			}
		}
	}

	public void registerTextures(TextureManager textureManager) {
		for (Identifier identifier : this.faces) {
			textureManager.registerTexture(identifier);
		}
	}
}
