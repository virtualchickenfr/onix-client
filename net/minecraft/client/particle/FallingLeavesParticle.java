package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.SimpleParticleType;

@Environment(EnvType.CLIENT)
public class FallingLeavesParticle extends TextureSheetParticle {
	private static final float ACCELERATION_SCALE = 0.0025F;
	private static final int INITIAL_LIFETIME = 300;
	private static final int CURVE_ENDPOINT_TIME = 300;
	private float rotSpeed;
	private final float particleRandom;
	private final float spinAcceleration;
	private final float windBig;
	private boolean swirl;
	private boolean flowAway;
	private double xaFlowScale;
	private double zaFlowScale;
	private double swirlPeriod;

	protected FallingLeavesParticle(
		ClientLevel clientLevel, double d, double e, double f, SpriteSet spriteSet, float g, float h, boolean bl, boolean bl2, float i, float j
	) {
		super(clientLevel, d, e, f);
		this.setSprite(spriteSet.get(this.random.nextInt(12), 12));
		this.rotSpeed = (float)Math.toRadians(this.random.nextBoolean() ? -30.0 : 30.0);
		this.particleRandom = this.random.nextFloat();
		this.spinAcceleration = (float)Math.toRadians(this.random.nextBoolean() ? -5.0 : 5.0);
		this.windBig = h;
		this.swirl = bl;
		this.flowAway = bl2;
		this.lifetime = 300;
		this.gravity = g * 1.2F * 0.0025F;
		float k = i * (this.random.nextBoolean() ? 0.05F : 0.075F);
		this.quadSize = k;
		this.setSize(k, k);
		this.friction = 1.0F;
		this.yd = -j;
		this.xaFlowScale = Math.cos(Math.toRadians(this.particleRandom * 60.0F)) * this.windBig;
		this.zaFlowScale = Math.sin(Math.toRadians(this.particleRandom * 60.0F)) * this.windBig;
		this.swirlPeriod = Math.toRadians(1000.0F + this.particleRandom * 3000.0F);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
	}

	@Override
	public void tick() {
		this.xo = this.x;
		this.yo = this.y;
		this.zo = this.z;
		if (this.lifetime-- <= 0) {
			this.remove();
		}

		if (!this.removed) {
			float f = 300 - this.lifetime;
			float g = Math.min(f / 300.0F, 1.0F);
			double d = 0.0;
			double e = 0.0;
			if (this.flowAway) {
				d += this.xaFlowScale * Math.pow(g, 1.25);
				e += this.zaFlowScale * Math.pow(g, 1.25);
			}

			if (this.swirl) {
				d += g * Math.cos(g * this.swirlPeriod) * this.windBig;
				e += g * Math.sin(g * this.swirlPeriod) * this.windBig;
			}

			this.xd += d * 0.0025F;
			this.zd += e * 0.0025F;
			this.yd = this.yd - this.gravity;
			this.rotSpeed = this.rotSpeed + this.spinAcceleration / 20.0F;
			this.oRoll = this.roll;
			this.roll = this.roll + this.rotSpeed / 20.0F;
			this.move(this.xd, this.yd, this.zd);
			if (this.onGround || this.lifetime < 299 && (this.xd == 0.0 || this.zd == 0.0)) {
				this.remove();
			}

			if (!this.removed) {
				this.xd = this.xd * this.friction;
				this.yd = this.yd * this.friction;
				this.zd = this.zd * this.friction;
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static class CherryProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public CherryProvider(SpriteSet spriteSet) {
			this.sprites = spriteSet;
		}

		public Particle createParticle(SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i) {
			return new FallingLeavesParticle(clientLevel, d, e, f, this.sprites, 0.25F, 2.0F, false, true, 1.0F, 0.0F);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class PaleOakProvider implements ParticleProvider<SimpleParticleType> {
		private final SpriteSet sprites;

		public PaleOakProvider(SpriteSet spriteSet) {
			this.sprites = spriteSet;
		}

		public Particle createParticle(SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i) {
			return new FallingLeavesParticle(clientLevel, d, e, f, this.sprites, 0.07F, 10.0F, true, false, 2.0F, 0.021F);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class TintedLeavesProvider implements ParticleProvider<ColorParticleOption> {
		private final SpriteSet sprites;

		public TintedLeavesProvider(SpriteSet spriteSet) {
			this.sprites = spriteSet;
		}

		public Particle createParticle(ColorParticleOption colorParticleOption, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i) {
			Particle particle = new FallingLeavesParticle(clientLevel, d, e, f, this.sprites, 0.07F, 10.0F, true, false, 2.0F, 0.021F);
			particle.setColor(colorParticleOption.getRed(), colorParticleOption.getGreen(), colorParticleOption.getBlue());
			return particle;
		}
	}
}
