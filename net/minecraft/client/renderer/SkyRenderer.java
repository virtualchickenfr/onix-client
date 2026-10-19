package net.minecraft.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

@Environment(EnvType.CLIENT)
public class SkyRenderer implements AutoCloseable {
	private static final ResourceLocation SUN_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
	private static final ResourceLocation MOON_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
	public static final ResourceLocation END_SKY_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/end_sky.png");
	private static final float SKY_DISC_RADIUS = 512.0F;
	private static final int SKY_VERTICES = 10;
	private static final int STAR_COUNT = 1500;
	private static final int END_SKY_QUAD_COUNT = 6;
	private final GpuBuffer starBuffer;
	private final RenderSystem.AutoStorageIndexBuffer starIndices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
	private final GpuBuffer topSkyBuffer;
	private final GpuBuffer bottomSkyBuffer;
	private final GpuBuffer endSkyBuffer;
	private int starIndexCount;

	public SkyRenderer() {
		this.starBuffer = this.buildStars();
		this.endSkyBuffer = buildEndSky();

		try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(10 * DefaultVertexFormat.POSITION.getVertexSize())) {
			BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
			this.buildSkyDisc(bufferBuilder, 16.0F);

			try (MeshData meshData = bufferBuilder.buildOrThrow()) {
				this.topSkyBuffer = RenderSystem.getDevice().createBuffer(() -> "Top sky vertex buffer", 32, meshData.vertexBuffer());
			}

			bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
			this.buildSkyDisc(bufferBuilder, -16.0F);

			try (MeshData meshData = bufferBuilder.buildOrThrow()) {
				this.bottomSkyBuffer = RenderSystem.getDevice().createBuffer(() -> "Bottom sky vertex buffer", 32, meshData.vertexBuffer());
			}
		}
	}

	private GpuBuffer buildStars() {
		RandomSource randomSource = RandomSource.create(10842L);
		float f = 100.0F;

		try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(DefaultVertexFormat.POSITION.getVertexSize() * 1500 * 4)) {
			BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);

			for (int i = 0; i < 1500; i++) {
				float g = randomSource.nextFloat() * 2.0F - 1.0F;
				float h = randomSource.nextFloat() * 2.0F - 1.0F;
				float j = randomSource.nextFloat() * 2.0F - 1.0F;
				float k = 0.15F + randomSource.nextFloat() * 0.1F;
				float l = Mth.lengthSquared(g, h, j);
				if (!(l <= 0.010000001F) && !(l >= 1.0F)) {
					Vector3f vector3f = new Vector3f(g, h, j).normalize(100.0F);
					float m = (float)(randomSource.nextDouble() * (float) Math.PI * 2.0);
					Matrix3f matrix3f = new Matrix3f().rotateTowards(new Vector3f(vector3f).negate(), new Vector3f(0.0F, 1.0F, 0.0F)).rotateZ(-m);
					bufferBuilder.addVertex(new Vector3f(k, -k, 0.0F).mul(matrix3f).add(vector3f));
					bufferBuilder.addVertex(new Vector3f(k, k, 0.0F).mul(matrix3f).add(vector3f));
					bufferBuilder.addVertex(new Vector3f(-k, k, 0.0F).mul(matrix3f).add(vector3f));
					bufferBuilder.addVertex(new Vector3f(-k, -k, 0.0F).mul(matrix3f).add(vector3f));
				}
			}

			try (MeshData meshData = bufferBuilder.buildOrThrow()) {
				this.starIndexCount = meshData.drawState().indexCount();
				return RenderSystem.getDevice().createBuffer(() -> "Stars vertex buffer", 40, meshData.vertexBuffer());
			}
		}
	}

	private void buildSkyDisc(VertexConsumer vertexConsumer, float f) {
		float g = Math.signum(f) * 512.0F;
		vertexConsumer.addVertex(0.0F, f, 0.0F);

		for (int i = -180; i <= 180; i += 45) {
			vertexConsumer.addVertex(g * Mth.cos(i * (float) (Math.PI / 180.0)), f, 512.0F * Mth.sin(i * (float) (Math.PI / 180.0)));
		}
	}

	public void renderSkyDisc(float f, float g, float h) {
		GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
			.writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(f, g, h, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);
		GpuTextureView gpuTextureView = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
		GpuTextureView gpuTextureView2 = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Sky disc", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
			renderPass.setPipeline(RenderPipelines.SKY);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
			renderPass.setVertexBuffer(0, this.topSkyBuffer);
			renderPass.draw(0, 10);
		}
	}

	public void renderDarkDisc() {
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		matrix4fStack.translate(0.0F, 12.0F, 0.0F);
		GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
			.writeTransform(matrix4fStack, new Vector4f(0.0F, 0.0F, 0.0F, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);
		GpuTextureView gpuTextureView = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
		GpuTextureView gpuTextureView2 = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Sky dark", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
			renderPass.setPipeline(RenderPipelines.SKY);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
			renderPass.setVertexBuffer(0, this.bottomSkyBuffer);
			renderPass.draw(0, 10);
		}

		matrix4fStack.popMatrix();
	}

	public void renderSunMoonAndStars(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float f, int i, float g, float h) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(f * 360.0F));
		this.renderSun(g, bufferSource, poseStack);
		this.renderMoon(i, g, bufferSource, poseStack);
		bufferSource.endBatch();
		if (h > 0.0F) {
			this.renderStars(h, poseStack);
		}

		poseStack.popPose();
	}

	private void renderSun(float f, MultiBufferSource multiBufferSource, PoseStack poseStack) {
		float g = 30.0F;
		float h = 100.0F;
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.celestial(SUN_LOCATION));
		int i = ARGB.white(f);
		Matrix4f matrix4f = poseStack.last().pose();
		vertexConsumer.addVertex(matrix4f, -30.0F, 100.0F, -30.0F).setUv(0.0F, 0.0F).setColor(i);
		vertexConsumer.addVertex(matrix4f, 30.0F, 100.0F, -30.0F).setUv(1.0F, 0.0F).setColor(i);
		vertexConsumer.addVertex(matrix4f, 30.0F, 100.0F, 30.0F).setUv(1.0F, 1.0F).setColor(i);
		vertexConsumer.addVertex(matrix4f, -30.0F, 100.0F, 30.0F).setUv(0.0F, 1.0F).setColor(i);
	}

	private void renderMoon(int i, float f, MultiBufferSource multiBufferSource, PoseStack poseStack) {
		float g = 20.0F;
		int j = i % 4;
		int k = i / 4 % 2;
		float h = (j + 0) / 4.0F;
		float l = (k + 0) / 2.0F;
		float m = (j + 1) / 4.0F;
		float n = (k + 1) / 2.0F;
		float o = 100.0F;
		VertexConsumer vertexConsumer = multiBufferSource.getBuffer(RenderType.celestial(MOON_LOCATION));
		int p = ARGB.white(f);
		Matrix4f matrix4f = poseStack.last().pose();
		vertexConsumer.addVertex(matrix4f, -20.0F, -100.0F, 20.0F).setUv(m, n).setColor(p);
		vertexConsumer.addVertex(matrix4f, 20.0F, -100.0F, 20.0F).setUv(h, n).setColor(p);
		vertexConsumer.addVertex(matrix4f, 20.0F, -100.0F, -20.0F).setUv(h, l).setColor(p);
		vertexConsumer.addVertex(matrix4f, -20.0F, -100.0F, -20.0F).setUv(m, l).setColor(p);
	}

	private void renderStars(float f, PoseStack poseStack) {
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		matrix4fStack.mul(poseStack.last().pose());
		RenderPipeline renderPipeline = RenderPipelines.STARS;
		GpuTextureView gpuTextureView = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
		GpuTextureView gpuTextureView2 = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
		GpuBuffer gpuBuffer = this.starIndices.getBuffer(this.starIndexCount);
		GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
			.writeTransform(matrix4fStack, new Vector4f(f, f, f, f), new Vector3f(), new Matrix4f(), 0.0F);

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "Stars", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
			renderPass.setPipeline(renderPipeline);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
			renderPass.setVertexBuffer(0, this.starBuffer);
			renderPass.setIndexBuffer(gpuBuffer, this.starIndices.type());
			renderPass.drawIndexed(0, 0, this.starIndexCount, 1);
		}

		matrix4fStack.popMatrix();
	}

	public void renderSunriseAndSunset(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float f, int i) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
		float g = Mth.sin(f) < 0.0F ? 180.0F : 0.0F;
		poseStack.mulPose(Axis.ZP.rotationDegrees(g));
		poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
		Matrix4f matrix4f = poseStack.last().pose();
		VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.sunriseSunset());
		float h = ARGB.alphaFloat(i);
		vertexConsumer.addVertex(matrix4f, 0.0F, 100.0F, 0.0F).setColor(i);
		int j = ARGB.transparent(i);
		int k = 16;

		for (int l = 0; l <= 16; l++) {
			float m = l * (float) (Math.PI * 2) / 16.0F;
			float n = Mth.sin(m);
			float o = Mth.cos(m);
			vertexConsumer.addVertex(matrix4f, n * 120.0F, o * 120.0F, -o * 40.0F * h).setColor(j);
		}

		poseStack.popPose();
	}

	private static GpuBuffer buildEndSky() {
		try (ByteBufferBuilder byteBufferBuilder = ByteBufferBuilder.exactlySized(24 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize())) {
			BufferBuilder bufferBuilder = new BufferBuilder(byteBufferBuilder, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

			for (int i = 0; i < 6; i++) {
				Matrix4f matrix4f = new Matrix4f();
				switch (i) {
					case 1:
						matrix4f.rotationX((float) (Math.PI / 2));
						break;
					case 2:
						matrix4f.rotationX((float) (-Math.PI / 2));
						break;
					case 3:
						matrix4f.rotationX((float) Math.PI);
						break;
					case 4:
						matrix4f.rotationZ((float) (Math.PI / 2));
						break;
					case 5:
						matrix4f.rotationZ((float) (-Math.PI / 2));
				}

				bufferBuilder.addVertex(matrix4f, -100.0F, -100.0F, -100.0F).setUv(0.0F, 0.0F).setColor(-14145496);
				bufferBuilder.addVertex(matrix4f, -100.0F, -100.0F, 100.0F).setUv(0.0F, 16.0F).setColor(-14145496);
				bufferBuilder.addVertex(matrix4f, 100.0F, -100.0F, 100.0F).setUv(16.0F, 16.0F).setColor(-14145496);
				bufferBuilder.addVertex(matrix4f, 100.0F, -100.0F, -100.0F).setUv(16.0F, 0.0F).setColor(-14145496);
			}

			try (MeshData meshData = bufferBuilder.buildOrThrow()) {
				return RenderSystem.getDevice().createBuffer(() -> "End sky vertex buffer", 40, meshData.vertexBuffer());
			}
		}
	}

	public void renderEndSky() {
		TextureManager textureManager = Minecraft.getInstance().getTextureManager();
		AbstractTexture abstractTexture = textureManager.getTexture(END_SKY_LOCATION);
		abstractTexture.setUseMipmaps(false);
		RenderSystem.AutoStorageIndexBuffer autoStorageIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
		GpuBuffer gpuBuffer = autoStorageIndexBuffer.getBuffer(36);
		GpuTextureView gpuTextureView = Minecraft.getInstance().getMainRenderTarget().getColorTextureView();
		GpuTextureView gpuTextureView2 = Minecraft.getInstance().getMainRenderTarget().getDepthTextureView();
		GpuBufferSlice gpuBufferSlice = RenderSystem.getDynamicUniforms()
			.writeTransform(RenderSystem.getModelViewMatrix(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f(), 0.0F);

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(() -> "End sky", gpuTextureView, OptionalInt.empty(), gpuTextureView2, OptionalDouble.empty())) {
			renderPass.setPipeline(RenderPipelines.END_SKY);
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("DynamicTransforms", gpuBufferSlice);
			renderPass.bindSampler("Sampler0", abstractTexture.getTextureView());
			renderPass.setVertexBuffer(0, this.endSkyBuffer);
			renderPass.setIndexBuffer(gpuBuffer, autoStorageIndexBuffer.type());
			renderPass.drawIndexed(0, 0, 36, 1);
		}
	}

	@Override
	public void close() {
		this.starBuffer.close();
		this.topSkyBuffer.close();
		this.bottomSkyBuffer.close();
		this.endSkyBuffer.close();
	}
}
