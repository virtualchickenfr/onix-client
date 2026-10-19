package net.minecraft.entity.projectile.thrown;

import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import java.util.function.Predicate;
import net.minecraft.block.AbstractCandleBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.Potions;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.jetbrains.annotations.Nullable;

public abstract class PotionEntity extends ThrownItemEntity {
	public static final double field_30667 = 4.0;
	protected static final double WATER_POTION_EXPLOSION_SQUARED_RADIUS = 16.0;
	public static final Predicate<LivingEntity> AFFECTED_BY_WATER = entity -> entity.hurtByWater() || entity.isOnFire();

	public PotionEntity(EntityType<? extends PotionEntity> entityType, World world) {
		super(entityType, world);
	}

	public PotionEntity(EntityType<? extends PotionEntity> type, World world, LivingEntity owner, ItemStack stack) {
		super(type, owner, world, stack);
	}

	public PotionEntity(EntityType<? extends PotionEntity> type, World world, double x, double y, double z, ItemStack stack) {
		super(type, x, y, z, world, stack);
	}

	@Override
	protected double getGravity() {
		return 0.05;
	}

	@Override
	protected void onBlockHit(BlockHitResult blockHitResult) {
		super.onBlockHit(blockHitResult);
		if (!this.getWorld().isClient) {
			ItemStack itemStack = this.getStack();
			Direction direction = blockHitResult.getSide();
			BlockPos blockPos = blockHitResult.getBlockPos();
			BlockPos blockPos2 = blockPos.offset(direction);
			PotionContentsComponent potionContentsComponent = itemStack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
			if (potionContentsComponent.matches(Potions.WATER)) {
				this.extinguishFire(blockPos2);
				this.extinguishFire(blockPos2.offset(direction.getOpposite()));

				for (Direction direction2 : Direction.Type.HORIZONTAL) {
					this.extinguishFire(blockPos2.offset(direction2));
				}
			}
		}
	}

	@Override
	protected void onCollision(HitResult hitResult) {
		super.onCollision(hitResult);
		if (this.getWorld() instanceof ServerWorld serverWorld) {
			ItemStack itemStack = this.getStack();
			PotionContentsComponent potionContentsComponent = itemStack.getOrDefault(DataComponentTypes.POTION_CONTENTS, PotionContentsComponent.DEFAULT);
			if (potionContentsComponent.matches(Potions.WATER)) {
				this.explodeWaterPotion(serverWorld);
			} else if (potionContentsComponent.hasEffects()) {
				this.spawnAreaEffectCloud(serverWorld, itemStack, hitResult.getType() == HitResult.Type.ENTITY ? ((EntityHitResult)hitResult).getEntity() : null);
			}

			int i = potionContentsComponent.potion().isPresent() && ((Potion)((RegistryEntry)potionContentsComponent.potion().get()).value()).hasInstantEffect()
				? WorldEvents.INSTANT_SPLASH_POTION_SPLASHED
				: WorldEvents.SPLASH_POTION_SPLASHED;
			serverWorld.syncWorldEvent(i, this.getBlockPos(), potionContentsComponent.getColor());
			this.discard();
		}
	}

	private void explodeWaterPotion(ServerWorld world) {
		Box box = this.getBoundingBox().expand(4.0, 2.0, 4.0);

		for (LivingEntity livingEntity : this.getWorld().getEntitiesByClass(LivingEntity.class, box, AFFECTED_BY_WATER)) {
			double d = this.squaredDistanceTo(livingEntity);
			if (d < 16.0) {
				if (livingEntity.hurtByWater()) {
					livingEntity.damage(world, this.getDamageSources().indirectMagic(this, this.getOwner()), 1.0F);
				}

				if (livingEntity.isOnFire() && livingEntity.isAlive()) {
					livingEntity.extinguishWithSound();
				}
			}
		}

		for (AxolotlEntity axolotlEntity : this.getWorld().getNonSpectatingEntities(AxolotlEntity.class, box)) {
			axolotlEntity.hydrateFromPotion();
		}
	}

	protected abstract void spawnAreaEffectCloud(ServerWorld world, ItemStack stack, @Nullable Entity entityHit);

	private void extinguishFire(BlockPos pos) {
		BlockState blockState = this.getWorld().getBlockState(pos);
		if (blockState.isIn(BlockTags.FIRE)) {
			this.getWorld().breakBlock(pos, false, this);
		} else if (AbstractCandleBlock.isLitCandle(blockState)) {
			AbstractCandleBlock.extinguish(null, blockState, this.getWorld(), pos);
		} else if (CampfireBlock.isLitCampfire(blockState)) {
			this.getWorld().syncWorldEvent(null, WorldEvents.FIRE_EXTINGUISHED, pos, 0);
			CampfireBlock.extinguish(this.getOwner(), this.getWorld(), pos, blockState);
			this.getWorld().setBlockState(pos, blockState.with(CampfireBlock.LIT, false));
		}
	}

	@Override
	public DoubleDoubleImmutablePair getKnockback(LivingEntity target, DamageSource source) {
		double d = target.getPos().x - this.getPos().x;
		double e = target.getPos().z - this.getPos().z;
		return DoubleDoubleImmutablePair.of(d, e);
	}
}
