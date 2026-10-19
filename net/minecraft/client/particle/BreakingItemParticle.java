package net.minecraft.client.particle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public class BreakingItemParticle extends TextureSheetParticle {
	private final float uo;
	private final float vo;

	BreakingItemParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i, ItemStackRenderState itemStackRenderState) {
		this(clientLevel, d, e, f, itemStackRenderState);
		this.xd *= 0.1F;
		this.yd *= 0.1F;
		this.zd *= 0.1F;
		this.xd += g;
		this.yd += h;
		this.zd += i;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.TERRAIN_SHEET;
	}

	protected BreakingItemParticle(ClientLevel clientLevel, double d, double e, double f, ItemStackRenderState itemStackRenderState) {
		super(clientLevel, d, e, f, 0.0, 0.0, 0.0);
		TextureAtlasSprite textureAtlasSprite = itemStackRenderState.pickParticleIcon(this.random);
		if (textureAtlasSprite != null) {
			this.setSprite(textureAtlasSprite);
		} else {
			this.setSprite(Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(MissingTextureAtlasSprite.getLocation()));
		}

		this.gravity = 1.0F;
		this.quadSize /= 2.0F;
		this.uo = this.random.nextFloat() * 3.0F;
		this.vo = this.random.nextFloat() * 3.0F;
	}

	@Override
	protected float getU0() {
		return this.sprite.getU((this.uo + 1.0F) / 4.0F);
	}

	@Override
	protected float getU1() {
		return this.sprite.getU(this.uo / 4.0F);
	}

	@Override
	protected float getV0() {
		return this.sprite.getV(this.vo / 4.0F);
	}

	@Override
	protected float getV1() {
		return this.sprite.getV((this.vo + 1.0F) / 4.0F);
	}

	@Environment(EnvType.CLIENT)
	public static class CobwebProvider extends BreakingItemParticle.ItemParticleProvider<SimpleParticleType> {
		public Particle createParticle(SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i) {
			return new BreakingItemParticle(clientLevel, d, e, f, this.calculateState(new ItemStack(Items.COBWEB), clientLevel));
		}
	}

	@Environment(EnvType.CLIENT)
	public abstract static class ItemParticleProvider<T extends ParticleOptions> implements ParticleProvider<T> {
		private final ItemStackRenderState scratchRenderState = new ItemStackRenderState();

		protected ItemStackRenderState calculateState(ItemStack itemStack, ClientLevel clientLevel) {
			Minecraft.getInstance().getItemModelResolver().updateForTopItem(this.scratchRenderState, itemStack, ItemDisplayContext.GROUND, clientLevel, null, 0);
			return this.scratchRenderState;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Provider extends BreakingItemParticle.ItemParticleProvider<ItemParticleOption> {
		public Particle createParticle(ItemParticleOption itemParticleOption, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i) {
			return new BreakingItemParticle(clientLevel, d, e, f, g, h, i, this.calculateState(itemParticleOption.getItem(), clientLevel));
		}
	}

	@Environment(EnvType.CLIENT)
	public static class SlimeProvider extends BreakingItemParticle.ItemParticleProvider<SimpleParticleType> {
		public Particle createParticle(SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i) {
			return new BreakingItemParticle(clientLevel, d, e, f, this.calculateState(new ItemStack(Items.SLIME_BALL), clientLevel));
		}
	}

	@Environment(EnvType.CLIENT)
	public static class SnowballProvider extends BreakingItemParticle.ItemParticleProvider<SimpleParticleType> {
		public Particle createParticle(SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i) {
			return new BreakingItemParticle(clientLevel, d, e, f, this.calculateState(new ItemStack(Items.SNOWBALL), clientLevel));
		}
	}
}
