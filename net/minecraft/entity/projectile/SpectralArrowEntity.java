package net.minecraft.entity.projectile;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class SpectralArrowEntity extends PersistentProjectileEntity {
	private static final int DEFAULT_DURATION = 200;
	private int duration = 200;

	public SpectralArrowEntity(EntityType<? extends SpectralArrowEntity> entityType, World world) {
		super(entityType, world);
	}

	public SpectralArrowEntity(World world, LivingEntity owner, ItemStack stack, @Nullable ItemStack shotFrom) {
		super(EntityType.SPECTRAL_ARROW, owner, world, stack, shotFrom);
	}

	public SpectralArrowEntity(World world, double x, double y, double z, ItemStack stack, @Nullable ItemStack shotFrom) {
		super(EntityType.SPECTRAL_ARROW, x, y, z, world, stack, shotFrom);
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld().isClient && !this.isInGround()) {
			this.getWorld().addParticleClient(ParticleTypes.INSTANT_EFFECT, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
		}
	}

	@Override
	protected void onHit(LivingEntity target) {
		super.onHit(target);
		StatusEffectInstance statusEffectInstance = new StatusEffectInstance(StatusEffects.GLOWING, this.duration, 0);
		target.addStatusEffect(statusEffectInstance, this.getEffectCause());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.duration = nbt.getInt("Duration", 200);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Duration", this.duration);
	}

	@Override
	protected ItemStack getDefaultItemStack() {
		return new ItemStack(Items.SPECTRAL_ARROW);
	}
}
