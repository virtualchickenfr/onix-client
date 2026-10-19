package net.minecraft.client.render;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.TriState;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class SkyRendering implements AutoCloseable {
	private static final Identifier SUN_TEXTURE = Identifier.ofVanilla("textures/environment/sun.png");
	private static final Identifier MOON_PHASES_TEXTURE = Identifier.ofVanilla("textures/environment/moon_phases.png");
	public static final Identifier END_SKY_TEXTURE = Identifier.ofVanilla("textures/environment/end_sky.png");
	private static final float field_53144 = 512.0F;
	private static final int field_57932 = 10;
	private static final int field_57933 = 1500;
	private static final int field_57934 = 6;
	private final GpuBuffer starVertexBuffer;
	private final RenderSystem.ShapeIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
	private final GpuBuffer topSkyVertexBuffer;
	private final GpuBuffer bottomSkyVertexBuffer;
	private final GpuBuffer endSkyVertexBuffer;
	private int starIndexCount;

	public SkyRendering() {
		this.starVertexBuffer = this.createStars();
		this.endSkyVertexBuffer = createEndSky();

		try (BufferAllocator bufferAllocator = new BufferAllocator(10 * VertexFormats.POSITION.getVertexSize())) {
			BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION);
			this.createSky(bufferBuilder, 16.0F);

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				this.topSkyVertexBuffer = RenderSystem.getDevice()
					.createBuffer(() -> "Top sky vertex buffer", BufferType.VERTICES, BufferUsage.STATIC_WRITE, builtBuffer.getBuffer());
			}

			bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION);
			this.createSky(bufferBuilder, -16.0F);

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				this.bottomSkyVertexBuffer = RenderSystem.getDevice()
					.createBuffer(() -> "Bottom sky vertex buffer", BufferType.VERTICES, BufferUsage.STATIC_WRITE, builtBuffer.getBuffer());
			}
		}
	}

	private GpuBuffer createStars() {
		Random random = Random.create(10842L);
		float f = 100.0F;

		GpuBuffer var19;
		try (BufferAllocator bufferAllocator = new BufferAllocator(VertexFormats.POSITION.getVertexSize() * 1500 * 4)) {
			BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);

			for (int i = 0; i < 1500; i++) {
				float g = random.nextFloat() * 2.0F - 1.0F;
				float h = random.nextFloat() * 2.0F - 1.0F;
				float j = random.nextFloat() * 2.0F - 1.0F;
				float k = 0.15F + random.nextFloat() * 0.1F;
				float l = MathHelper.magnitude(g, h, j);
				if (!(l <= 0.010000001F) && !(l >= 1.0F)) {
					Vector3f vector3f = new Vector3f(g, h, j).normalize(100.0F);
					float m = (float)(random.nextDouble() * (float) Math.PI * 2.0);
					Matrix3f matrix3f = new Matrix3f().rotateTowards(new Vector3f(vector3f).negate(), new Vector3f(0.0F, 1.0F, 0.0F)).rotateZ(-m);
					bufferBuilder.vertex(new Vector3f(k, -k, 0.0F).mul(matrix3f).add(vector3f));
					bufferBuilder.vertex(new Vector3f(k, k, 0.0F).mul(matrix3f).add(vector3f));
					bufferBuilder.vertex(new Vector3f(-k, k, 0.0F).mul(matrix3f).add(vector3f));
					bufferBuilder.vertex(new Vector3f(-k, -k, 0.0F).mul(matrix3f).add(vector3f));
				}
			}

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				this.starIndexCount = builtBuffer.getDrawParameters().indexCount();
				var19 = RenderSystem.getDevice().createBuffer(() -> "Stars vertex buffer", BufferType.VERTICES, BufferUsage.STATIC_WRITE, builtBuffer.getBuffer());
			}
		}

		return var19;
	}

	private void createSky(VertexConsumer vertexConsumer, float height) {
		float f = Math.signum(height) * 512.0F;
		vertexConsumer.vertex(0.0F, height, 0.0F);

		for (int i = -180; i <= 180; i += 45) {
			vertexConsumer.vertex(f * MathHelper.cos(i * (float) (Math.PI / 180.0)), height, 512.0F * MathHelper.sin(i * (float) (Math.PI / 180.0)));
		}
	}

	public void renderTopSky(float red, float green, float blue) {
		RenderSystem.setShaderColor(red, green, blue, 1.0F);
		GpuTexture gpuTexture = MinecraftClient.getInstance().getFramebuffer().getColorAttachment();
		GpuTexture gpuTexture2 = MinecraftClient.getInstance().getFramebuffer().getDepthAttachment();

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(gpuTexture, OptionalInt.empty(), gpuTexture2, OptionalDouble.empty())) {
			renderPass.setPipeline(RenderPipelines.POSITION_SKY);
			renderPass.setVertexBuffer(0, this.topSkyVertexBuffer);
			renderPass.draw(0, 10);
		}

		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	public void renderSkyDark() {
		RenderSystem.setShaderColor(0.0F, 0.0F, 0.0F, 1.0F);
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		matrix4fStack.translate(0.0F, 12.0F, 0.0F);
		GpuTexture gpuTexture = MinecraftClient.getInstance().getFramebuffer().getColorAttachment();
		GpuTexture gpuTexture2 = MinecraftClient.getInstance().getFramebuffer().getDepthAttachment();

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(gpuTexture, OptionalInt.empty(), gpuTexture2, OptionalDouble.empty())) {
			renderPass.setPipeline(RenderPipelines.POSITION_SKY);
			renderPass.setVertexBuffer(0, this.bottomSkyVertexBuffer);
			renderPass.draw(0, 10);
		}

		matrix4fStack.popMatrix();
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
	}

	public void renderCelestialBodies(
		MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, float rot, int phase, float alpha, float starBrightness, Fog fog
	) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rot * 360.0F));
		this.renderSun(alpha, vertexConsumers, matrices);
		this.renderMoon(phase, alpha, vertexConsumers, matrices);
		vertexConsumers.draw();
		if (starBrightness > 0.0F) {
			this.renderStars(fog, starBrightness, matrices);
		}

		matrices.pop();
	}

	private void renderSun(float alpha, VertexConsumerProvider vertexConsumers, MatrixStack matrices) {
		float f = 30.0F;
		float g = 100.0F;
		VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getCelestial(SUN_TEXTURE));
		int i = ColorHelper.getWhite(alpha);
		Matrix4f matrix4f = matrices.peek().getPositionMatrix();
		vertexConsumer.vertex(matrix4f, -30.0F, 100.0F, -30.0F).texture(0.0F, 0.0F).color(i);
		vertexConsumer.vertex(matrix4f, 30.0F, 100.0F, -30.0F).texture(1.0F, 0.0F).color(i);
		vertexConsumer.vertex(matrix4f, 30.0F, 100.0F, 30.0F).texture(1.0F, 1.0F).color(i);
		vertexConsumer.vertex(matrix4f, -30.0F, 100.0F, 30.0F).texture(0.0F, 1.0F).color(i);
	}

	private void renderMoon(int phase, float alpha, VertexConsumerProvider vertexConsumers, MatrixStack matrices) {
		float f = 20.0F;
		int i = phase % 4;
		int j = phase / 4 % 2;
		float g = (i + 0) / 4.0F;
		float h = (j + 0) / 2.0F;
		float k = (i + 1) / 4.0F;
		float l = (j + 1) / 2.0F;
		float m = 100.0F;
		VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getCelestial(MOON_PHASES_TEXTURE));
		int n = ColorHelper.getWhite(alpha);
		Matrix4f matrix4f = matrices.peek().getPositionMatrix();
		vertexConsumer.vertex(matrix4f, -20.0F, -100.0F, 20.0F).texture(k, l).color(n);
		vertexConsumer.vertex(matrix4f, 20.0F, -100.0F, 20.0F).texture(g, l).color(n);
		vertexConsumer.vertex(matrix4f, 20.0F, -100.0F, -20.0F).texture(g, h).color(n);
		vertexConsumer.vertex(matrix4f, -20.0F, -100.0F, -20.0F).texture(k, h).color(n);
	}

	private void renderStars(Fog fog, float color, MatrixStack matrices) {
		Matrix4fStack matrix4fStack = RenderSystem.getModelViewStack();
		matrix4fStack.pushMatrix();
		matrix4fStack.mul(matrices.peek().getPositionMatrix());
		RenderSystem.setShaderColor(color, color, color, color);
		RenderSystem.setShaderFog(Fog.DUMMY);
		RenderPipeline renderPipeline = RenderPipelines.POSITION_STARS;
		GpuTexture gpuTexture = MinecraftClient.getInstance().getFramebuffer().getColorAttachment();
		GpuTexture gpuTexture2 = MinecraftClient.getInstance().getFramebuffer().getDepthAttachment();
		GpuBuffer gpuBuffer = this.indexBuffer.getIndexBuffer(this.starIndexCount);

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(gpuTexture, OptionalInt.empty(), gpuTexture2, OptionalDouble.empty())) {
			renderPass.setPipeline(renderPipeline);
			renderPass.setVertexBuffer(0, this.starVertexBuffer);
			renderPass.setIndexBuffer(gpuBuffer, this.indexBuffer.getIndexType());
			renderPass.drawIndexed(0, this.starIndexCount);
		}

		RenderSystem.setShaderFog(fog);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		matrix4fStack.popMatrix();
	}

	public void renderGlowingSky(MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, float angleRadians, int color) {
		matrices.push();
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
		float f = MathHelper.sin(angleRadians) < 0.0F ? 180.0F : 0.0F;
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f));
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90.0F));
		Matrix4f matrix4f = matrices.peek().getPositionMatrix();
		VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getSunriseSunset());
		float g = ColorHelper.getAlphaFloat(color);
		vertexConsumer.vertex(matrix4f, 0.0F, 100.0F, 0.0F).color(color);
		int i = ColorHelper.zeroAlpha(color);
		int j = 16;

		for (int k = 0; k <= 16; k++) {
			float h = k * (float) (Math.PI * 2) / 16.0F;
			float l = MathHelper.sin(h);
			float m = MathHelper.cos(h);
			vertexConsumer.vertex(matrix4f, l * 120.0F, m * 120.0F, -m * 40.0F * g).color(i);
		}

		matrices.pop();
	}

	private static GpuBuffer createEndSky() {
		GpuBuffer var10;
		try (BufferAllocator bufferAllocator = new BufferAllocator(24 * VertexFormats.POSITION_TEXTURE_COLOR.getVertexSize())) {
			BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

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

				bufferBuilder.vertex(matrix4f, -100.0F, -100.0F, -100.0F).texture(0.0F, 0.0F).color(-14145496);
				bufferBuilder.vertex(matrix4f, -100.0F, -100.0F, 100.0F).texture(0.0F, 16.0F).color(-14145496);
				bufferBuilder.vertex(matrix4f, 100.0F, -100.0F, 100.0F).texture(16.0F, 16.0F).color(-14145496);
				bufferBuilder.vertex(matrix4f, 100.0F, -100.0F, -100.0F).texture(16.0F, 0.0F).color(-14145496);
			}

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				var10 = RenderSystem.getDevice().createBuffer(() -> "End sky vertex buffer", BufferType.VERTICES, BufferUsage.STATIC_WRITE, builtBuffer.getBuffer());
			}
		}

		return var10;
	}

	public void renderEndSky() {
		TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
		AbstractTexture abstractTexture = textureManager.getTexture(END_SKY_TEXTURE);
		abstractTexture.setFilter(TriState.FALSE, false);
		RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
		GpuBuffer gpuBuffer = shapeIndexBuffer.getIndexBuffer(36);
		GpuTexture gpuTexture = MinecraftClient.getInstance().getFramebuffer().getColorAttachment();
		GpuTexture gpuTexture2 = MinecraftClient.getInstance().getFramebuffer().getDepthAttachment();

		try (RenderPass renderPass = RenderSystem.getDevice()
				.createCommandEncoder()
				.createRenderPass(gpuTexture, OptionalInt.empty(), gpuTexture2, OptionalDouble.empty())) {
			renderPass.setPipeline(RenderPipelines.POSITION_TEX_COLOR_END_SKY);
			renderPass.bindSampler("Sampler0", abstractTexture.getGlTexture());
			renderPass.setVertexBuffer(0, this.endSkyVertexBuffer);
			renderPass.setIndexBuffer(gpuBuffer, shapeIndexBuffer.getIndexType());
			renderPass.drawIndexed(0, 36);
		}
	}

	public void close() {
		this.starVertexBuffer.close();
		this.topSkyVertexBuffer.close();
		this.bottomSkyVertexBuffer.close();
		this.endSkyVertexBuffer.close();
	}
}
