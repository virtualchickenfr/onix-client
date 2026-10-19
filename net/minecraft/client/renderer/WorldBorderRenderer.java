package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public class WorldBorderRenderer {
	public static final ResourceLocation FORCEFIELD_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");
	private boolean needsRebuild = true;
	private double lastMinX;
	private double lastMinZ;
	private double lastBorderMinX;
	private double lastBorderMaxX;
	private double lastBorderMinZ;
	private double lastBorderMaxZ;
	private final GpuBuffer worldBorderBuffer = RenderSystem.getDevice()
		.createBuffer(() -> "World border vertex buffer", 40, 16 * DefaultVertexFormat.POSITION_TEX.getVertexSize());
	private final RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);

	private void rebuildWorldBorderBuffer(WorldBorder worldBorder, double d, double e, double f, float g, float h, float i) {
		try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION_TEX.getVertexSize() * 4 * 4)) {
			double j = worldBorder.getMinX();
			double k = worldBorder.getMaxX();
			double l = worldBorder.getMinZ();
			double m = worldBorder.getMaxZ();
			double n = Math.max(Mth.floor(e - d), l);
			double o = Math.min(Mth.ceil(e + d), m);
			float p = (Mth.floor(n) & 1) * 0.5F;
			float q = (float)(o - n) / 2.0F;
			double r = Math.max(Mth.floor(f - d), j);
			double s = Math.min(Mth.ceil(f + d), k);
			float t = (Mth.floor(r) & 1) * 0.5F;
			float u = (float)(s - r) / 2.0F;
			BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
			bufferBuilder.addVertex(0.0F, -g, (float)(m - n)).setUv(t, h);
			bufferBuilder.addVertex((float)(s - r), -g, (float)(m - n)).setUv(u + t, h);
			bufferBuilder.addVertex((float)(s - r), g, (float)(m - n)).setUv(u + t, i);
			bufferBuilder.addVertex(0.0F, g, (float)(m - n)).setUv(t, i);
			bufferBuilder.addVertex(0.0F, -g, 0.0F).setUv(p, h);
			bufferBuilder.addVertex(0.0F, -g, (float)(o - n)).setUv(q + p, h);
			bufferBuilder.addVertex(0.0F, g, (float)(o - n)).setUv(q + p, i);
			bufferBuilder.addVertex(0.0F, g, 0.0F).setUv(p, i);
			bufferBuilder.addVertex((float)(s - r), -g, 0.0F).setUv(t, h);
			bufferBuilder.addVertex(0.0F, -g, 0.0F).setUv(u + t, h);
			bufferBuilder.addVertex(0.0F, g, 0.0F).setUv(u + t, i);
			bufferBuilder.addVertex((float)(s - r), g, 0.0F).setUv(t, i);
			bufferBuilder.addVertex((float)(k - r), -g, (float)(o - n)).setUv(p, h);
			bufferBuilder.addVertex((float)(k - r), -g, 0.0F).setUv(q + p, h);
			bufferBuilder.addVertex((float)(k - r), g, 0.0F).setUv(q + p, i);
			bufferBuilder.addVertex((float)(k - r), g, (float)(o - n)).setUv(p, i);

			try (MeshData meshData = bufferBuilder.buildOrThrow()) {
				RenderSystem.getDevice().createCommandEncoder().writeToBuffer(this.worldBorderBuffer.slice(), meshData.vertexBuffer());
			}

			this.lastBorderMinX = j;
			this.lastBorderMaxX = k;
			this.lastBorderMinZ = l;
			this.lastBorderMaxZ = m;
			this.lastMinX = r;
			this.lastMinZ = n;
			this.needsRebuild = false;
		}
	}

	public void render(WorldBorder worldBorder, Vec3 vec3, double d, double e) {
		double f = worldBorder.getMinX();
		double g = worldBorder.getMaxX();
		double h = worldBorder.getMinZ();
		double i = worldBorder.getMaxZ();
		if ((!(vec3.x < g - d) || !(vec3.x > f + d) || !(vec3.z < i - d) || !(vec3.z > h + d))
			&& !(vec3.x < f - d)
			&& !(vec3.x > g + d)
			&& !(vec3.z < h - d)
			&& !(vec3.z > i + d)) {
			double j = 1.0 - worldBorder.getDistanceToBorder(vec3.x, vec3.z) / d;
			j = Math.pow(j, 4.0);
			j = Mth.clamp(j, 0.0, 1.0);
			double k = vec3.x;
			double l = vec3.z;
			float m = (float)e;
			int n = worldBorder.getStatus().getColor();
			float o = ARGB.red(n) / 255.0F;
			float p = ARGB.green(n) / 255.0F;
			float q = ARGB.blue(n) / 255.0F;
			float r = (float)(Util.getMillis() % 3000L) / 3000.0F;
			float s = (float)(-Mth.frac(vec3.y * 0.5));
			float t = s + m;
			if (this.shouldRebuildWorldBorderBuffer(worldBorder)) {
				this.rebuildWorldBorderBuffer(worldBorder, d, l, k, m, t, s);
			}

			TextureManager textureManager = Minecraft.getInstance().getTextureManager();
			AbstractTexture abstractTexture = textureManager.getTexture(FORCEFIELD_LOCATION);
			abstractTexture.setUseMipmaps(false);
			RenderPipeline renderPipeline = RenderPipelines.WORLD_BORDER;
			RenderTarget renderTarget = Minecraft.getInstance().getMainRenderTarget();
			RenderTarget renderTarget2 = Minecraft.getInstance().levelRenderer.getWeatherTarget();
			GpuTextureView gpuTextureView;
			GpuTextureView gpuTextureView2;
			if (renderTarget2 != null) {
				gpuTextureView = renderTarget2.getColorTextureView();
				gpuTextureView2 = renderTarget2.getDepthTextureView();
			} else {
				gpuTextureView = renderTarget.getColorTextureView();
				gpuTextureView2 = renderTarget.getDepthTextureView();
			}

			GpuBuffer gpuBuffer = this.indices.getBuffer(6);
			GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
				.writeTransform(
					RenderSystem.getModelViewMatrix(),
					new Vector4f(o, p, q, (float)j),
					new Vector3f((float)(this.lastMinX - k), (float)(-vec3.y), (float)(this.lastMinZ - l)),
					new Matrix4f().translation(r, r, 0.0F),
					0.0F
				);

			try (RenderPass renderPass = RenderSystem.getDevice()
					.createCommandEncoder()
					.createRenderPass(() -> "World border", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
				renderPass.setPipeline(renderPipeline);
				RenderSystem.bindDefaultUniforms(renderPass);
				renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
				renderPass.setIndexBuffer(gpuBuffer, this.indices.type());
				renderPass.bindSampler("Sampler0", abstractTexture.getTextureView());
				renderPass.setVertexBuffer(0, this.worldBorderBuffer);
				ArrayList<RenderPass.Draw<WorldBorderRenderer>> arrayList = new ArrayList<>();

				for (WorldBorder.DistancePerDirection distancePerDirection : worldBorder.closestBorder(k, l)) {
					if (distancePerDirection.distance() < d) {
						int u = distancePerDirection.direction().get2DDataValue();
						arrayList.add(new RenderPass.Draw<>(0, this.worldBorderBuffer, gpuBuffer, this.indices.type(), 6 * u, 6));
					}
				}

				renderPass.drawMultipleIndexed(arrayList, null, null, Collections.emptyList(), this);
			}
		}
	}

	public void invalidate() {
		this.needsRebuild = true;
	}

	private boolean shouldRebuildWorldBorderBuffer(WorldBorder worldBorder) {
		return this.needsRebuild
			|| worldBorder.getMinX() != this.lastBorderMinX
			|| worldBorder.getMinZ() != this.lastBorderMinZ
			|| worldBorder.getMaxX() != this.lastBorderMaxX
			|| worldBorder.getMaxZ() != this.lastBorderMaxZ;
	}
}
