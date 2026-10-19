package net.minecraft.client.render;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.border.WorldBorder;
import org.joml.Matrix4f;

@Environment(EnvType.CLIENT)
public class WorldBorderRendering {
	public static final Identifier FORCEFIELD = Identifier.ofVanilla("textures/misc/forcefield.png");
	private boolean forceRefreshBuffers = true;
	private double lastUploadedBoundWest;
	private double lastUploadedBoundNorth;
	private double lastXMin;
	private double lastXMax;
	private double lastZMin;
	private double lastZMax;
	private final GpuBuffer vertexBuffer = RenderSystem.getDevice()
		.createBuffer(() -> "World border vertex buffer", BufferType.VERTICES, BufferUsage.DYNAMIC_WRITE, 16 * VertexFormats.POSITION_TEXTURE.getVertexSize());
	private final RenderSystem.ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);

	private void refreshDirectionBuffer(WorldBorder border, double viewDistanceBlocks, double z, double x, float farPlaneDistance, float f, float g) {
		try (BufferAllocator bufferAllocator = new BufferAllocator(VertexFormats.POSITION_TEXTURE.getVertexSize() * 4)) {
			double d = border.getBoundWest();
			double e = border.getBoundEast();
			double h = border.getBoundNorth();
			double i = border.getBoundSouth();
			double j = Math.max(MathHelper.floor(z - viewDistanceBlocks), h);
			double k = Math.min(MathHelper.ceil(z + viewDistanceBlocks), i);
			float l = (MathHelper.floor(j) & 1) * 0.5F;
			float m = (float)(k - j) / 2.0F;
			double n = Math.max(MathHelper.floor(x - viewDistanceBlocks), d);
			double o = Math.min(MathHelper.ceil(x + viewDistanceBlocks), e);
			float p = (MathHelper.floor(n) & 1) * 0.5F;
			float q = (float)(o - n) / 2.0F;
			BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
			bufferBuilder.vertex(0.0F, -farPlaneDistance, (float)(i - j)).texture(p, f);
			bufferBuilder.vertex((float)(o - n), -farPlaneDistance, (float)(i - j)).texture(q + p, f);
			bufferBuilder.vertex((float)(o - n), farPlaneDistance, (float)(i - j)).texture(q + p, g);
			bufferBuilder.vertex(0.0F, farPlaneDistance, (float)(i - j)).texture(p, g);
			bufferBuilder.vertex(0.0F, -farPlaneDistance, 0.0F).texture(l, f);
			bufferBuilder.vertex(0.0F, -farPlaneDistance, (float)(k - j)).texture(m + l, f);
			bufferBuilder.vertex(0.0F, farPlaneDistance, (float)(k - j)).texture(m + l, g);
			bufferBuilder.vertex(0.0F, farPlaneDistance, 0.0F).texture(l, g);
			bufferBuilder.vertex((float)(o - n), -farPlaneDistance, 0.0F).texture(p, f);
			bufferBuilder.vertex(0.0F, -farPlaneDistance, 0.0F).texture(q + p, f);
			bufferBuilder.vertex(0.0F, farPlaneDistance, 0.0F).texture(q + p, g);
			bufferBuilder.vertex((float)(o - n), farPlaneDistance, 0.0F).texture(p, g);
			bufferBuilder.vertex((float)(e - n), -farPlaneDistance, (float)(k - j)).texture(l, f);
			bufferBuilder.vertex((float)(e - n), -farPlaneDistance, 0.0F).texture(m + l, f);
			bufferBuilder.vertex((float)(e - n), farPlaneDistance, 0.0F).texture(m + l, g);
			bufferBuilder.vertex((float)(e - n), farPlaneDistance, (float)(k - j)).texture(l, g);

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.vertexBuffer, builtBuffer.getBuffer(), 0);
			}

			this.lastXMin = d;
			this.lastXMax = e;
			this.lastZMin = h;
			this.lastZMax = i;
			this.lastUploadedBoundWest = n;
			this.lastUploadedBoundNorth = j;
			this.forceRefreshBuffers = false;
		}
	}

	public void render(WorldBorder border, Vec3d cameraPos, double viewDistanceBlocks, double farPlaneDistance) {
		double d = border.getBoundWest();
		double e = border.getBoundEast();
		double f = border.getBoundNorth();
		double g = border.getBoundSouth();
		if ((
				!(cameraPos.x < e - viewDistanceBlocks)
					|| !(cameraPos.x > d + viewDistanceBlocks)
					|| !(cameraPos.z < g - viewDistanceBlocks)
					|| !(cameraPos.z > f + viewDistanceBlocks)
			)
			&& !(cameraPos.x < d - viewDistanceBlocks)
			&& !(cameraPos.x > e + viewDistanceBlocks)
			&& !(cameraPos.z < f - viewDistanceBlocks)
			&& !(cameraPos.z > g + viewDistanceBlocks)) {
			double h = 1.0 - border.getDistanceInsideBorder(cameraPos.x, cameraPos.z) / viewDistanceBlocks;
			h = Math.pow(h, 4.0);
			h = MathHelper.clamp(h, 0.0, 1.0);
			double i = cameraPos.x;
			double j = cameraPos.z;
			float k = (float)farPlaneDistance;
			int l = border.getStage().getColor();
			float m = ColorHelper.getRed(l) / 255.0F;
			float n = ColorHelper.getGreen(l) / 255.0F;
			float o = ColorHelper.getBlue(l) / 255.0F;
			RenderSystem.setShaderColor(m, n, o, (float)h);
			float p = (float)(Util.getMeasuringTimeMs() % 3000L) / 3000.0F;
			RenderSystem.setTextureMatrix(new Matrix4f().translation(p, p, 0.0F));
			float q = (float)(-MathHelper.fractionalPart(cameraPos.y * 0.5));
			float r = q + k;
			if (this.shouldRefreshBuffers(border)) {
				this.refreshDirectionBuffer(border, viewDistanceBlocks, j, i, k, r, q);
			}

			RenderSystem.setModelOffset((float)(this.lastUploadedBoundWest - i), (float)(-cameraPos.y), (float)(this.lastUploadedBoundNorth - j));
			TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
			AbstractTexture abstractTexture = textureManager.getTexture(FORCEFIELD);
			abstractTexture.setFilter(TriState.FALSE, false);
			RenderPipeline renderPipeline = RenderPipelines.RENDERTYPE_WORLD_BORDER;
			Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
			Framebuffer framebuffer2 = MinecraftClient.getInstance().worldRenderer.getWeatherFramebuffer();
			GpuTexture gpuTexture;
			GpuTexture gpuTexture2;
			if (framebuffer2 != null) {
				gpuTexture = framebuffer2.getColorAttachment();
				gpuTexture2 = framebuffer2.getDepthAttachment();
			} else {
				gpuTexture = framebuffer.getColorAttachment();
				gpuTexture2 = framebuffer.getDepthAttachment();
			}

			GpuBuffer gpuBuffer = this.indexBuffer.getIndexBuffer(6);

			try (RenderPass renderPass = RenderSystem.getDevice()
					.createCommandEncoder()
					.createRenderPass(gpuTexture, OptionalInt.empty(), gpuTexture2, OptionalDouble.empty())) {
				renderPass.setPipeline(renderPipeline);
				renderPass.setIndexBuffer(gpuBuffer, this.indexBuffer.getIndexType());
				renderPass.bindSampler("Sampler0", abstractTexture.getGlTexture());
				renderPass.setVertexBuffer(0, this.vertexBuffer);
				ArrayList<RenderPass.RenderObject> arrayList = new ArrayList();

				for (WorldBorder.DistanceFromCamera distanceFromCamera : border.calculateDistancesFromCamera(i, j)) {
					if (distanceFromCamera.distance() < viewDistanceBlocks) {
						int s = distanceFromCamera.direction().getHorizontalQuarterTurns();
						arrayList.add(new RenderPass.RenderObject(0, this.vertexBuffer, gpuBuffer, this.indexBuffer.getIndexType(), 6 * s, 6));
					}
				}

				renderPass.drawMultipleIndexed(arrayList, null, null);
			}

			RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			RenderSystem.resetTextureMatrix();
			RenderSystem.resetModelOffset();
		}
	}

	public void markBuffersDirty() {
		this.forceRefreshBuffers = true;
	}

	private boolean shouldRefreshBuffers(WorldBorder border) {
		return this.forceRefreshBuffers
			|| border.getBoundWest() != this.lastXMin
			|| border.getBoundNorth() != this.lastZMin
			|| border.getBoundEast() != this.lastXMax
			|| border.getBoundSouth() != this.lastZMax;
	}
}
