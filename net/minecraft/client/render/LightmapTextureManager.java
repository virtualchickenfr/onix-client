package net.minecraft.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.OptionalInt;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.dimension.DimensionType;
import org.joml.Vector3f;

/**
 * The lightmap texture manager maintains a texture containing the RGBA overlay for each of the 16&times;16 sky and block light combinations.
 * <p>
 * Also contains some utilities to pack and unpack lightmap coordinates from sky and block light values,
 * and some lightmap coordinates constants.
 */
@Environment(EnvType.CLIENT)
public class LightmapTextureManager implements AutoCloseable {
	/**
	 * Represents the maximum lightmap coordinate, where both sky light and block light equals {@code 15}.
	 * The value of this maximum lightmap coordinate is {@value}.
	 */
	public static final int MAX_LIGHT_COORDINATE = 15728880;
	/**
	 * Represents the maximum sky-light-wise lightmap coordinate whose value is {@value}.
	 * This is equivalent to a {@code 15} sky light and {@code 0} block light.
	 */
	public static final int MAX_SKY_LIGHT_COORDINATE = 15728640;
	/**
	 * Represents the maximum block-light-wise lightmap coordinate whose value is {@value}.
	 * This is equivalent to a {@code 0} sky light and {@code 15} block light.
	 */
	public static final int MAX_BLOCK_LIGHT_COORDINATE = 240;
	private static final int field_53098 = 16;
	private final GpuTexture glTexture;
	private boolean dirty;
	private float flickerIntensity;
	private final GameRenderer renderer;
	private final MinecraftClient client;

	public LightmapTextureManager(GameRenderer renderer, MinecraftClient client) {
		this.renderer = renderer;
		this.client = client;
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.glTexture = gpuDevice.createTexture("Light Texture", TextureFormat.RGBA8, 16, 16, 1);
		this.glTexture.setTextureFilter(FilterMode.LINEAR, false);
		gpuDevice.createCommandEncoder().clearColorTexture(this.glTexture, -1);
	}

	public GpuTexture getGlTexture() {
		return this.glTexture;
	}

	public void close() {
		this.glTexture.close();
	}

	public void tick() {
		this.flickerIntensity = this.flickerIntensity + (float)((Math.random() - Math.random()) * Math.random() * Math.random() * 0.1);
		this.flickerIntensity *= 0.9F;
		this.dirty = true;
	}

	public void disable() {
		RenderSystem.setShaderTexture(2, null);
	}

	public void enable() {
		RenderSystem.setShaderTexture(2, this.glTexture);
	}

	private float getDarkness(LivingEntity entity, float factor, float tickProgress) {
		float f = 0.45F * factor;
		return Math.max(0.0F, MathHelper.cos((entity.age - tickProgress) * (float) Math.PI * 0.025F) * f);
	}

	public void update(float tickProgress) {
		if (this.dirty) {
			this.dirty = false;
			Profiler profiler = Profilers.get();
			profiler.push("lightTex");
			ClientWorld clientWorld = this.client.world;
			if (clientWorld != null) {
				float f = clientWorld.getSkyBrightness(1.0F);
				float g;
				if (clientWorld.getLightningTicksLeft() > 0) {
					g = 1.0F;
				} else {
					g = f * 0.95F + 0.05F;
				}

				float h = this.client.options.getDarknessEffectScale().getValue().floatValue();
				float i = this.client.player.getEffectFadeFactor(StatusEffects.DARKNESS, tickProgress) * h;
				float j = this.getDarkness(this.client.player, i, tickProgress) * h;
				float k = this.client.player.getUnderwaterVisibility();
				float l;
				if (this.client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
					l = GameRenderer.getNightVisionStrength(this.client.player, tickProgress);
				} else if (k > 0.0F && this.client.player.hasStatusEffect(StatusEffects.CONDUIT_POWER)) {
					l = k;
				} else {
					l = 0.0F;
				}

				Vector3f vector3f = new Vector3f(f, f, 1.0F).lerp(new Vector3f(1.0F, 1.0F, 1.0F), 0.35F);
				float m = this.flickerIntensity + 1.5F;
				float n = clientWorld.getDimension().ambientLight();
				boolean bl = clientWorld.getDimensionEffects().shouldBrightenLighting();
				float o = this.client.options.getGamma().getValue().floatValue();
				RenderSystem.ShapeIndexBuffer shapeIndexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.DrawMode.QUADS);
				GpuBuffer gpuBuffer = shapeIndexBuffer.getIndexBuffer(6);

				try (RenderPass renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(this.glTexture, OptionalInt.empty())) {
					renderPass.setPipeline(RenderPipelines.BILT_SCREEN_LIGHTMAP);
					renderPass.setUniform("AmbientLightFactor", n);
					renderPass.setUniform("SkyFactor", g);
					renderPass.setUniform("BlockFactor", m);
					renderPass.setUniform("UseBrightLightmap", bl ? 1 : 0);
					renderPass.setUniform("SkyLightColor", vector3f.x, vector3f.y, vector3f.z);
					renderPass.setUniform("NightVisionFactor", l);
					renderPass.setUniform("DarknessScale", j);
					renderPass.setUniform("DarkenWorldFactor", this.renderer.getSkyDarkness(tickProgress));
					renderPass.setUniform("BrightnessFactor", Math.max(0.0F, o - i));
					renderPass.setVertexBuffer(0, RenderSystem.getQuadVertexBuffer());
					renderPass.setIndexBuffer(gpuBuffer, shapeIndexBuffer.getIndexType());
					renderPass.drawIndexed(0, 6);
				}

				profiler.pop();
			}
		}
	}

	public static float getBrightness(DimensionType type, int lightLevel) {
		return getBrightness(type.ambientLight(), lightLevel);
	}

	public static float getBrightness(float ambientLight, int lightLevel) {
		float f = lightLevel / 15.0F;
		float g = f / (4.0F - 3.0F * f);
		return MathHelper.lerp(ambientLight, g, 1.0F);
	}

	public static int pack(int block, int sky) {
		return block << 4 | sky << 20;
	}

	public static int getBlockLightCoordinates(int light) {
		return light >>> 4 & 15;
	}

	public static int getSkyLightCoordinates(int light) {
		return light >>> 20 & 15;
	}

	public static int applyEmission(int light, int lightEmission) {
		if (lightEmission == 0) {
			return light;
		} else {
			int i = Math.max(getSkyLightCoordinates(light), lightEmission);
			int j = Math.max(getBlockLightCoordinates(light), lightEmission);
			return pack(j, i);
		}
	}
}
