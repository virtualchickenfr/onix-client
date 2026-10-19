package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.GpuTexture;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.annotation.DeobfuscateClass;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
@DeobfuscateClass
public interface CommandEncoder {
	RenderPass createRenderPass(GpuTexture colorAttachment, OptionalInt clearColor);

	RenderPass createRenderPass(GpuTexture colorAttachment, OptionalInt clearColor, @Nullable GpuTexture depthAttachment, OptionalDouble clearDepth);

	void clearColorTexture(GpuTexture texture, int color);

	void clearColorAndDepthTextures(GpuTexture colorAttachment, int color, GpuTexture depthAttachment, double depth);

	void clearDepthTexture(GpuTexture texture, double depth);

	void writeToBuffer(GpuBuffer target, ByteBuffer source, int offset);

	GpuBuffer.ReadView readBuffer(GpuBuffer source);

	GpuBuffer.ReadView readBuffer(GpuBuffer source, int offset, int count);

	void writeToTexture(GpuTexture target, NativeImage source);

	void writeToTexture(GpuTexture target, NativeImage source, int mipLevel, int intoX, int intoY, int width, int height, int x, int y);

	void writeToTexture(GpuTexture target, IntBuffer source, NativeImage.Format format, int mipLevel, int intoX, int intoY, int width, int height);

	void copyTextureToBuffer(GpuTexture target, GpuBuffer source, int offset, Runnable dataUploadedCallback, int mipLevel);

	void copyTextureToBuffer(
		GpuTexture target, GpuBuffer source, int offset, Runnable dataUploadedCallback, int mipLevel, int intoX, int intoY, int width, int height
	);

	void copyTextureToTexture(GpuTexture target, GpuTexture source, int mipLevel, int intoX, int intoY, int sourceX, int sourceY, int width, int height);

	void presentTexture(GpuTexture texture);
}
