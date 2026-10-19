package net.minecraft.client.util.tracy;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.jtracy.TracyClient;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class TracyFrameCapturer implements AutoCloseable {
	private static final int MAX_WIDTH = 320;
	private static final int MAX_HEIGHT = 180;
	private static final int field_54254 = 4;
	private int framebufferWidth;
	private int framebufferHeight;
	private int width;
	private int height;
	@Nullable
	private GpuTexture framebuffer;
	@Nullable
	private GpuBuffer buffer;
	private int offset;
	private boolean captured;
	private TracyFrameCapturer.Status status = TracyFrameCapturer.Status.WAITING_FOR_CAPTURE;

	private void resize(int framebufferWidth, int framebufferHeight) {
		float f = (float)framebufferWidth / framebufferHeight;
		if (framebufferWidth > 320) {
			framebufferWidth = 320;
			framebufferHeight = (int)(320.0F / f);
		}

		if (framebufferHeight > 180) {
			framebufferWidth = (int)(180.0F * f);
			framebufferHeight = 180;
		}

		framebufferWidth = framebufferWidth / 4 * 4;
		framebufferHeight = framebufferHeight / 4 * 4;
		if (this.width != framebufferWidth || this.height != framebufferHeight) {
			this.width = framebufferWidth;
			this.height = framebufferHeight;
			if (this.framebuffer != null) {
				this.framebuffer.close();
			}

			this.framebuffer = RenderSystem.getDevice().createTexture("Tracy Frame Capture", TextureFormat.RGBA8, framebufferWidth, framebufferHeight, 1);
			if (this.buffer != null) {
				this.buffer.close();
			}

			this.buffer = RenderSystem.getDevice()
				.createBuffer(() -> "Tracy Frame Capture buffer", BufferType.PIXEL_PACK, BufferUsage.STREAM_READ, framebufferWidth * framebufferHeight * 4);
		}
	}

	public void capture(Framebuffer framebuffer) {
		if (this.status == TracyFrameCapturer.Status.WAITING_FOR_CAPTURE
			&& !this.captured
			&& framebuffer.getColorAttachment() != null
			&& this.buffer != null
			&& this.framebuffer != null) {
			this.captured = true;
			if (framebuffer.textureWidth != this.framebufferWidth || framebuffer.textureHeight != this.framebufferHeight) {
				this.framebufferWidth = framebuffer.textureWidth;
				this.framebufferHeight = framebuffer.textureHeight;
				this.resize(this.framebufferWidth, this.framebufferHeight);
			}

			this.status = TracyFrameCapturer.Status.WAITING_FOR_COPY;
			CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
			RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
			GpuBuffer gpuBuffer = shapeIndexBuffer.getIndexBuffer(6);

			try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(this.framebuffer, OptionalInt.empty())) {
				renderPass.setPipeline(RenderPipelines.TRACY_BLIT);
				renderPass.setVertexBuffer(0, RenderSystem.getQuadVertexBuffer());
				renderPass.setIndexBuffer(gpuBuffer, shapeIndexBuffer.getIndexType());
				renderPass.bindSampler("InSampler", framebuffer.getColorAttachment());
				renderPass.drawIndexed(0, 6);
			}

			commandEncoder.copyTextureToBuffer(this.framebuffer, this.buffer, 0, () -> this.status = TracyFrameCapturer.Status.WAITING_FOR_UPLOAD, 0);
			this.offset = 0;
		}
	}

	public void upload() {
		if (this.status == TracyFrameCapturer.Status.WAITING_FOR_UPLOAD && this.buffer != null) {
			this.status = TracyFrameCapturer.Status.WAITING_FOR_CAPTURE;

			try (GpuBuffer.ReadView readView = RenderSystem.getDevice().createCommandEncoder().readBuffer(this.buffer)) {
				TracyClient.frameImage(readView.data(), this.width, this.height, this.offset, true);
			}
		}
	}

	public void markFrame() {
		this.offset++;
		this.captured = false;
		TracyClient.markFrame();
	}

	public void close() {
		if (this.framebuffer != null) {
			this.framebuffer.close();
		}

		if (this.buffer != null) {
			this.buffer.close();
		}
	}

	@Environment(EnvType.CLIENT)
	static enum Status {
		WAITING_FOR_CAPTURE,
		WAITING_FOR_COPY,
		WAITING_FOR_UPLOAD;
	}
}
