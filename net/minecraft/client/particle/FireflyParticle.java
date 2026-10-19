package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class FireflyParticle extends SpriteBillboardParticle {
	private static final float field_56803 = 0.3F;
	private static final float field_56804 = 0.1F;
	private static final float field_56801 = 0.5F;
	private static final float field_56802 = 0.3F;
	private static final int MIN_MAX_AGE = 36;
	private static final int MAX_MAX_AGE = 180;

	FireflyParticle(ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
		super(clientWorld, d, e, f, g, h, i);
		this.ascending = true;
		this.velocityMultiplier = 0.96F;
		this.scale *= 0.75F;
		this.velocityY *= 0.8F;
		this.velocityX *= 0.8F;
		this.velocityZ *= 0.8F;
	}

	@Override
	public ParticleTextureSheet getType() {
		return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;
	}

	@Override
	public int getBrightness(float tint) {
		return (int)(255.0F * method_67878(this.method_67879(this.age + tint), 0.1F, 0.3F));
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.world.getBlockState(BlockPos.ofFloored(this.x, this.y, this.z)).isAir()) {
			this.markDead();
		} else {
			this.setAlpha(method_67878(this.method_67879(this.age), 0.3F, 0.5F));
			if (Math.random() > 0.95 || this.age == 1) {
				this.setVelocity(-0.05F + 0.1F * Math.random(), -0.05F + 0.1F * Math.random(), -0.05F + 0.1F * Math.random());
			}
		}
	}

	private float method_67879(float f) {
		return MathHelper.clamp(f / this.maxAge, 0.0F, 1.0F);
	}

	private static float method_67878(float f, float g, float h) {
		if (f >= 1.0F - g) {
			return (1.0F - f) / g;
		} else {
			return f <= h ? f / h : 1.0F;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Factory implements ParticleFactory<SimpleParticleType> {
		private final SpriteProvider spriteProvider;

		public Factory(SpriteProvider spriteProvider) {
			this.spriteProvider = spriteProvider;
		}

		public Particle createParticle(SimpleParticleType simpleParticleType, ClientWorld clientWorld, double d, double e, double f, double g, double h, double i) {
			FireflyParticle fireflyParticle = new FireflyParticle(
				clientWorld, d, e, f, 0.5 - clientWorld.random.nextDouble(), clientWorld.random.nextBoolean() ? h : -h, 0.5 - clientWorld.random.nextDouble()
			);
			fireflyParticle.setMaxAge(clientWorld.random.nextBetween(36, 180));
			fireflyParticle.scale(1.5F);
			fireflyParticle.setSprite(this.spriteProvider);
			fireflyParticle.setAlpha(0.0F);
			return fireflyParticle;
		}
	}
}
