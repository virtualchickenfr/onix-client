package net.minecraft.client.gl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class Framebuffer {
	private static int index = 0;
	public int textureWidth;
	public int textureHeight;
	public int viewportWidth;
	public int viewportHeight;
	protected final String name;
	public final boolean useDepthAttachment;
	@Nullable
	protected GpuTexture colorAttachment;
	@Nullable
	protected GpuTexture depthAttachment;
	public FilterMode filterMode;

	public Framebuffer(@Nullable String name, boolean useDepthAttachment) {
		this.name = name == null ? "FBO " + index++ : name;
		this.useDepthAttachment = useDepthAttachment;
	}

	public void resize(int width, int height) {
		RenderSystem.assertOnRenderThread();
		this.delete();
		this.initFbo(width, height);
	}

	public void delete() {
		RenderSystem.assertOnRenderThread();
		if (this.depthAttachment != null) {
			this.depthAttachment.close();
			this.depthAttachment = null;
		}

		if (this.colorAttachment != null) {
			this.colorAttachment.close();
			this.colorAttachment = null;
		}
	}

	public void copyDepthFrom(Framebuffer framebuffer) {
		RenderSystem.assertOnRenderThread();
		if (this.depthAttachment == null) {
			throw new IllegalStateException("Trying to copy depth texture to a RenderTarget without a depth texture");
		} else if (framebuffer.depthAttachment == null) {
			throw new IllegalStateException("Trying to copy depth texture from a RenderTarget without a depth texture");
		} else {
			RenderSystem.getDevice()
				.createCommandEncoder()
				.copyTextureToTexture(framebuffer.depthAttachment, this.depthAttachment, 0, 0, 0, 0, 0, this.textureWidth, this.textureHeight);
		}
	}

	public void initFbo(int width, int height) {
		RenderSystem.assertOnRenderThread();
		int i = RenderSystem.getDevice().getMaxTextureSize();
		if (width > 0 && width <= i && height > 0 && height <= i) {
			this.viewportWidth = width;
			this.viewportHeight = height;
			this.textureWidth = width;
			this.textureHeight = height;
			if (this.useDepthAttachment) {
				this.depthAttachment = RenderSystem.getDevice().createTexture(() -> this.name + " / Depth", TextureFormat.DEPTH32, width, height, 1);
				this.depthAttachment.setTextureFilter(FilterMode.NEAREST, false);
				this.depthAttachment.setAddressMode(AddressMode.CLAMP_TO_EDGE);
			}

			this.colorAttachment = RenderSystem.getDevice().createTexture(() -> this.name + " / Color", TextureFormat.RGBA8, width, height, 1);
			this.colorAttachment.setAddressMode(AddressMode.CLAMP_TO_EDGE);
			this.setFilter(FilterMode.NEAREST, true);
		} else {
			throw new IllegalArgumentException("Window " + width + "x" + height + " size out of bounds (max. size: " + i + ")");
		}
	}

	public void setFilter(FilterMode filter) {
		this.setFilter(filter, false);
	}

	private void setFilter(FilterMode filter, boolean force) {
		if (this.colorAttachment == null) {
			throw new IllegalStateException("Can't change filter mode, color texture doesn't exist yet");
		} else {
			if (force || filter != this.filterMode) {
				this.filterMode = filter;
				this.colorAttachment.setTextureFilter(filter, false);
			}
		}
	}

	public void blitToScreen() {
		if (this.colorAttachment == null) {
			throw new IllegalStateException("Can't blit to screen, color texture doesn't exist yet");
		} else {
			RenderSystem.getDevice().createCommandEncoder().presentTexture(this.colorAttachment);
		}
	}

	public void drawBlit(GpuTexture texture) {
		RenderSystem.assertOnRenderThread();
		RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
		GpuBuffer gpuBuffer = shapeIndexBuffer.getIndexBuffer(6);
		GpuBuffer gpuBuffer2 = RenderSystem.getQuadVertexBuffer();

		try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(texture, OptionalInt.empty())) {
			renderPass.setPipeline(RenderPipelines.ENTITY_OUTLINE_BLIT);
			renderPass.setVertexBuffer(0, gpuBuffer2);
			renderPass.setIndexBuffer(gpuBuffer, shapeIndexBuffer.getIndexType());
			renderPass.bindSampler("InSampler", this.colorAttachment);
			renderPass.drawIndexed(0, 6);
		}
	}

	@Nullable
	public GpuTexture getColorAttachment() {
		return this.colorAttachment;
	}

	@Nullable
	public GpuTexture getDepthAttachment() {
		return this.depthAttachment;
	}
}
