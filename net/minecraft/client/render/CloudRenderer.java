package net.minecraft.client.render;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class CloudRenderer extends SinglePreparationResourceReloader<Optional<CloudRenderer.CloudCells>> implements AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Identifier CLOUD_TEXTURE = Identifier.ofVanilla("textures/environment/clouds.png");
	private static final float field_53043 = 12.0F;
	private static final float field_53044 = 4.0F;
	private static final float field_53045 = 0.6F;
	private static final long field_53046 = 0L;
	private static final int field_53047 = 4;
	private static final int field_53048 = 3;
	private static final int field_53049 = 2;
	private static final int field_53050 = 1;
	private static final int field_53051 = 0;
	private boolean rebuild = true;
	private int centerX = Integer.MIN_VALUE;
	private int centerZ = Integer.MIN_VALUE;
	private CloudRenderer.ViewMode viewMode = CloudRenderer.ViewMode.INSIDE_CLOUDS;
	@Nullable
	private CloudRenderMode renderMode;
	@Nullable
	private CloudRenderer.CloudCells cells;
	@Nullable
	private GpuBuffer vertexBuffer = null;
	private int indexCount = 0;
	private final RenderSystem.ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);

	protected Optional<CloudRenderer.CloudCells> prepare(ResourceManager resourceManager, Profiler profiler) {
		try {
			InputStream inputStream = resourceManager.open(CLOUD_TEXTURE);

			Optional var20;
			try (NativeImage nativeImage = NativeImage.read(inputStream)) {
				int i = nativeImage.getWidth();
				int j = nativeImage.getHeight();
				long[] ls = new long[i * j];

				for (int k = 0; k < j; k++) {
					for (int l = 0; l < i; l++) {
						int m = nativeImage.getColorArgb(l, k);
						if (isEmpty(m)) {
							ls[l + k * i] = 0L;
						} else {
							boolean bl = isEmpty(nativeImage.getColorArgb(l, Math.floorMod(k - 1, j)));
							boolean bl2 = isEmpty(nativeImage.getColorArgb(Math.floorMod(l + 1, j), k));
							boolean bl3 = isEmpty(nativeImage.getColorArgb(l, Math.floorMod(k + 1, j)));
							boolean bl4 = isEmpty(nativeImage.getColorArgb(Math.floorMod(l - 1, j), k));
							ls[l + k * i] = packCloudCell(m, bl, bl2, bl3, bl4);
						}
					}
				}

				var20 = Optional.of(new CloudRenderer.CloudCells(ls, i, j));
			} catch (Throwable var18) {
				if (inputStream != null) {
					try {
						inputStream.close();
					} catch (Throwable var15) {
						var18.addSuppressed(var15);
					}
				}

				throw var18;
			}

			if (inputStream != null) {
				inputStream.close();
			}

			return var20;
		} catch (IOException var19) {
			LOGGER.error("Failed to load cloud texture", (Throwable)var19);
			return Optional.empty();
		}
	}

	protected void apply(Optional<CloudRenderer.CloudCells> optional, ResourceManager resourceManager, Profiler profiler) {
		this.cells = (CloudRenderer.CloudCells)optional.orElse(null);
		this.rebuild = true;
	}

	private static boolean isEmpty(int color) {
		return ColorHelper.getAlpha(color) < 10;
	}

	private static long packCloudCell(int color, boolean borderNorth, boolean borderEast, boolean borderSouth, boolean borderWest) {
		return (long)color << 4 | (borderNorth ? 1 : 0) << 3 | (borderEast ? 1 : 0) << 2 | (borderSouth ? 1 : 0) << 1 | (borderWest ? 1 : 0) << 0;
	}

	private static int unpackColor(long packed) {
		return (int)(packed >> 4 & 4294967295L);
	}

	private static boolean hasBorderNorth(long packed) {
		return (packed >> 3 & 1L) != 0L;
	}

	private static boolean hasBorderEast(long packed) {
		return (packed >> 2 & 1L) != 0L;
	}

	private static boolean hasBorderSouth(long packed) {
		return (packed >> 1 & 1L) != 0L;
	}

	private static boolean hasBorderWest(long packed) {
		return (packed >> 0 & 1L) != 0L;
	}

	public void renderClouds(int color, CloudRenderMode cloudRenderMode, float cloudHeight, Vec3d cameraPos, float cloudsHeight) {
		if (this.cells != null) {
			float f = (float)(cloudHeight - cameraPos.y);
			float g = f + 4.0F;
			CloudRenderer.ViewMode viewMode;
			if (g < 0.0F) {
				viewMode = CloudRenderer.ViewMode.ABOVE_CLOUDS;
			} else if (f > 0.0F) {
				viewMode = CloudRenderer.ViewMode.BELOW_CLOUDS;
			} else {
				viewMode = CloudRenderer.ViewMode.INSIDE_CLOUDS;
			}

			double d = cameraPos.x + cloudsHeight * 0.030000001F;
			double e = cameraPos.z + 3.96F;
			double h = this.cells.width * 12.0;
			double i = this.cells.height * 12.0;
			d -= MathHelper.floor(d / h) * h;
			e -= MathHelper.floor(e / i) * i;
			int j = MathHelper.floor(d / 12.0);
			int k = MathHelper.floor(e / 12.0);
			float l = (float)(d - j * 12.0F);
			float m = (float)(e - k * 12.0F);
			boolean bl = cloudRenderMode == CloudRenderMode.FANCY;
			RenderPipeline renderPipeline = bl ? RenderPipelines.CLOUDS : RenderPipelines.FLAT_CLOUDS;
			if (this.rebuild || j != this.centerX || k != this.centerZ || viewMode != this.viewMode || cloudRenderMode != this.renderMode) {
				this.rebuild = false;
				this.centerX = j;
				this.centerZ = k;
				this.viewMode = viewMode;
				this.renderMode = cloudRenderMode;

				try (BuiltBuffer builtBuffer = this.tessellateClouds(Tessellator.getInstance(), j, k, cloudRenderMode, viewMode, renderPipeline)) {
					if (builtBuffer == null) {
						this.indexCount = 0;
					} else {
						if (this.vertexBuffer != null && this.vertexBuffer.size >= builtBuffer.getBuffer().remaining()) {
							CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
							commandEncoder.writeToBuffer(this.vertexBuffer, builtBuffer.getBuffer(), 0);
						} else {
							if (this.vertexBuffer != null) {
								this.vertexBuffer.close();
							}

							this.vertexBuffer = RenderSystem.getDevice()
								.createBuffer(() -> "Cloud vertex buffer", BufferType.VERTICES, BufferUsage.DYNAMIC_WRITE, builtBuffer.getBuffer());
						}

						this.indexCount = builtBuffer.getDrawParameters().indexCount();
					}
				}
			}

			if (this.indexCount != 0) {
				RenderSystem.setShaderColor(ColorHelper.getRedFloat(color), ColorHelper.getGreenFloat(color), ColorHelper.getBlueFloat(color), 1.0F);
				if (bl) {
					this.drawClouds(RenderPipelines.CLOUDS_DEPTH_ONLY, l, f, m);
				}

				this.drawClouds(renderPipeline, l, f, m);
				RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
			}
		}
	}

	private void drawClouds(RenderPipeline pipeline, float f, float g, float h) {
		RenderSystem.setModelOffset(-f, g, -h);
		Framebuffer framebuffer = MinecraftClient.getInstance().getFramebuffer();
		Framebuffer framebuffer2 = MinecraftClient.getInstance().worldRenderer.getCloudsFramebuffer();
		GpuTexture gpuTexture;
		GpuTexture gpuTexture2;
		if (framebuffer2 != null) {
			gpuTexture = framebuffer2.getColorAttachment();
			gpuTexture2 = framebuffer2.getDepthAttachment();
		} else {
			gpuTexture = framebuffer.getColorAttachment();
			gpuTexture2 = framebuffer.getDepthAttachment();
		}

		GpuBuffer gpuBuffer = this.indexBuffer.getIndexBuffer(this.indexCount);

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(gpuTexture, OptionalInt.empty(), gpuTexture2, OptionalDouble.empty())) {
			renderPass.setPipeline(pipeline);
			renderPass.setIndexBuffer(gpuBuffer, this.indexBuffer.getIndexType());
			renderPass.setVertexBuffer(0, this.vertexBuffer);
			renderPass.drawIndexed(0, this.indexCount);
		}

		RenderSystem.resetModelOffset();
	}

	@Nullable
	private BuiltBuffer tessellateClouds(
		Tessellator tessellator, int x, int z, CloudRenderMode renderMode, CloudRenderer.ViewMode viewMode, RenderPipeline pipeline
	) {
		float f = 0.8F;
		int i = ColorHelper.fromFloats(0.8F, 1.0F, 1.0F, 1.0F);
		int j = ColorHelper.fromFloats(0.8F, 0.9F, 0.9F, 0.9F);
		int k = ColorHelper.fromFloats(0.8F, 0.7F, 0.7F, 0.7F);
		int l = ColorHelper.fromFloats(0.8F, 0.8F, 0.8F, 0.8F);
		BufferBuilder bufferBuilder = tessellator.begin(pipeline.getVertexFormatMode(), pipeline.getVertexFormat());
		this.buildCloudCells(viewMode, bufferBuilder, x, z, k, i, j, l, renderMode == CloudRenderMode.FANCY);
		return bufferBuilder.endNullable();
	}

	private void buildCloudCells(
		CloudRenderer.ViewMode viewMode, BufferBuilder builder, int x, int z, int bottomColor, int topColor, int northSouthColor, int eastWestColor, boolean fancy
	) {
		if (this.cells != null) {
			int i = 32;
			long[] ls = this.cells.cells;
			int j = this.cells.width;
			int k = this.cells.height;

			for (int l = -32; l <= 32; l++) {
				for (int m = -32; m <= 32; m++) {
					int n = Math.floorMod(x + m, j);
					int o = Math.floorMod(z + l, k);
					long p = ls[n + o * j];
					if (p != 0L) {
						int q = unpackColor(p);
						if (fancy) {
							this.buildCloudCellFancy(
								viewMode,
								builder,
								ColorHelper.mix(bottomColor, q),
								ColorHelper.mix(topColor, q),
								ColorHelper.mix(northSouthColor, q),
								ColorHelper.mix(eastWestColor, q),
								m,
								l,
								p
							);
						} else {
							this.buildCloudCellFast(builder, ColorHelper.mix(topColor, q), m, l);
						}
					}
				}
			}
		}
	}

	private void buildCloudCellFast(BufferBuilder builder, int color, int x, int z) {
		float f = x * 12.0F;
		float g = f + 12.0F;
		float h = z * 12.0F;
		float i = h + 12.0F;
		builder.vertex(f, 0.0F, h).color(color);
		builder.vertex(f, 0.0F, i).color(color);
		builder.vertex(g, 0.0F, i).color(color);
		builder.vertex(g, 0.0F, h).color(color);
	}

	private void buildCloudCellFancy(
		CloudRenderer.ViewMode viewMode, BufferBuilder builder, int bottomColor, int topColor, int northSouthColor, int eastWestColor, int x, int z, long cell
	) {
		float f = x * 12.0F;
		float g = f + 12.0F;
		float h = 0.0F;
		float i = 4.0F;
		float j = z * 12.0F;
		float k = j + 12.0F;
		if (viewMode != CloudRenderer.ViewMode.BELOW_CLOUDS) {
			builder.vertex(f, 4.0F, j).color(topColor);
			builder.vertex(f, 4.0F, k).color(topColor);
			builder.vertex(g, 4.0F, k).color(topColor);
			builder.vertex(g, 4.0F, j).color(topColor);
		}

		if (viewMode != CloudRenderer.ViewMode.ABOVE_CLOUDS) {
			builder.vertex(g, 0.0F, j).color(bottomColor);
			builder.vertex(g, 0.0F, k).color(bottomColor);
			builder.vertex(f, 0.0F, k).color(bottomColor);
			builder.vertex(f, 0.0F, j).color(bottomColor);
		}

		if (hasBorderNorth(cell) && z > 0) {
			builder.vertex(f, 0.0F, j).color(eastWestColor);
			builder.vertex(f, 4.0F, j).color(eastWestColor);
			builder.vertex(g, 4.0F, j).color(eastWestColor);
			builder.vertex(g, 0.0F, j).color(eastWestColor);
		}

		if (hasBorderSouth(cell) && z < 0) {
			builder.vertex(g, 0.0F, k).color(eastWestColor);
			builder.vertex(g, 4.0F, k).color(eastWestColor);
			builder.vertex(f, 4.0F, k).color(eastWestColor);
			builder.vertex(f, 0.0F, k).color(eastWestColor);
		}

		if (hasBorderWest(cell) && x > 0) {
			builder.vertex(f, 0.0F, k).color(northSouthColor);
			builder.vertex(f, 4.0F, k).color(northSouthColor);
			builder.vertex(f, 4.0F, j).color(northSouthColor);
			builder.vertex(f, 0.0F, j).color(northSouthColor);
		}

		if (hasBorderEast(cell) && x < 0) {
			builder.vertex(g, 0.0F, j).color(northSouthColor);
			builder.vertex(g, 4.0F, j).color(northSouthColor);
			builder.vertex(g, 4.0F, k).color(northSouthColor);
			builder.vertex(g, 0.0F, k).color(northSouthColor);
		}

		boolean bl = Math.abs(x) <= 1 && Math.abs(z) <= 1;
		if (bl) {
			builder.vertex(g, 4.0F, j).color(topColor);
			builder.vertex(g, 4.0F, k).color(topColor);
			builder.vertex(f, 4.0F, k).color(topColor);
			builder.vertex(f, 4.0F, j).color(topColor);
			builder.vertex(f, 0.0F, j).color(bottomColor);
			builder.vertex(f, 0.0F, k).color(bottomColor);
			builder.vertex(g, 0.0F, k).color(bottomColor);
			builder.vertex(g, 0.0F, j).color(bottomColor);
			builder.vertex(g, 0.0F, j).color(eastWestColor);
			builder.vertex(g, 4.0F, j).color(eastWestColor);
			builder.vertex(f, 4.0F, j).color(eastWestColor);
			builder.vertex(f, 0.0F, j).color(eastWestColor);
			builder.vertex(f, 0.0F, k).color(eastWestColor);
			builder.vertex(f, 4.0F, k).color(eastWestColor);
			builder.vertex(g, 4.0F, k).color(eastWestColor);
			builder.vertex(g, 0.0F, k).color(eastWestColor);
			builder.vertex(f, 0.0F, j).color(northSouthColor);
			builder.vertex(f, 4.0F, j).color(northSouthColor);
			builder.vertex(f, 4.0F, k).color(northSouthColor);
			builder.vertex(f, 0.0F, k).color(northSouthColor);
			builder.vertex(g, 0.0F, k).color(northSouthColor);
			builder.vertex(g, 4.0F, k).color(northSouthColor);
			builder.vertex(g, 4.0F, j).color(northSouthColor);
			builder.vertex(g, 0.0F, j).color(northSouthColor);
		}
	}

	public void scheduleTerrainUpdate() {
		this.rebuild = true;
	}

	public void close() {
		if (this.vertexBuffer != null) {
			this.vertexBuffer.close();
		}
	}

	@Environment(EnvType.CLIENT)
	public record CloudCells(long[] cells, int width, int height) {
	}

	@Environment(EnvType.CLIENT)
	static enum ViewMode {
		ABOVE_CLOUDS,
		INSIDE_CLOUDS,
		BELOW_CLOUDS;
	}
}
