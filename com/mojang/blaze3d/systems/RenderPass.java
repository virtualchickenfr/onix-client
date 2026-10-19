package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Collection;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.annotation.DeobfuscateClass;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
@DeobfuscateClass
public interface RenderPass extends AutoCloseable {
	void setPipeline(RenderPipeline pipeline);

	void bindSampler(String name, GpuTexture texture);

	void setUniform(String name, int... values);

	void setUniform(String name, float... values);

	void setUniform(String name, Matrix4f matrix);

	void enableScissor(ScissorState scissor);

	void enableScissor(int x, int y, int width, int height);

	void disableScissor();

	void setVertexBuffer(int index, GpuBuffer buffer);

	void setIndexBuffer(GpuBuffer indexBuffer, VertexFormat.IndexType indexType);

	void drawIndexed(int offset, int count);

	void drawMultipleIndexed(Collection<RenderPass.RenderObject> objects, @Nullable GpuBuffer buffer, @Nullable VertexFormat.IndexType indexType);

	void draw(int offset, int count);

	void close();

	@Environment(EnvType.CLIENT)
	public record RenderObject(
		int slot,
		GpuBuffer vertexBuffer,
		@Nullable GpuBuffer indexBuffer,
		@Nullable VertexFormat.IndexType indexType,
		int firstIndex,
		int indexCount,
		@Nullable Consumer<RenderPass.UniformUploader> uniformUploaderConsumer
	) {
		public RenderObject(int slot, GpuBuffer vertexBuffer, GpuBuffer indexBuffer, VertexFormat.IndexType indexType, int firstIndex, int indexCount) {
			this(slot, vertexBuffer, indexBuffer, indexType, firstIndex, indexCount, null);
		}
	}

	@Environment(EnvType.CLIENT)
	public interface UniformUploader {
		void upload(String name, float... values);
	}
}
