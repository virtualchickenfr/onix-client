package net.minecraft.entity.mob;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public interface Angerable {
	String ANGER_TIME_KEY = "AngerTime";
	String ANGRY_AT_KEY = "AngryAt";

	int getAngerTime();

	void setAngerTime(int angerTime);

	@Nullable
	UUID getAngryAt();

	void setAngryAt(@Nullable UUID angryAt);

	void chooseRandomAngerTime();

	default void writeAngerToNbt(NbtCompound nbt) {
		nbt.putInt("AngerTime", this.getAngerTime());
		nbt.putNullable("AngryAt", Uuids.INT_STREAM_CODEC, this.getAngryAt());
	}

	default void readAngerFromNbt(World world, NbtCompound nbt) {
		this.setAngerTime(nbt.getInt("AngerTime", 0));
		if (world instanceof ServerWorld serverWorld) {
			UUID uUID = (UUID)nbt.get("AngryAt", Uuids.INT_STREAM_CODEC).orElse(null);
			this.setAngryAt(uUID);
			if ((uUID != null ? serverWorld.getEntity(uUID) : null) instanceof LivingEntity livingEntity) {
				this.setTarget(livingEntity);
			}
		}
	}

	/**
	 * @param angerPersistent if {@code true}, the anger time will not decrease for a player target
	 */
	default void tickAngerLogic(ServerWorld world, boolean angerPersistent) {
		LivingEntity livingEntity = this.getTarget();
		UUID uUID = this.getAngryAt();
		if ((livingEntity == null || livingEntity.isDead()) && uUID != null && world.getEntity(uUID) instanceof MobEntity) {
			this.stopAnger();
		} else {
			if (livingEntity != null && !Objects.equals(uUID, livingEntity.getUuid())) {
				this.setAngryAt(livingEntity.getUuid());
				this.chooseRandomAngerTime();
			}

			if (this.getAngerTime() > 0 && (livingEntity == null || livingEntity.getType() != EntityType.PLAYER || !angerPersistent)) {
				this.setAngerTime(this.getAngerTime() - 1);
				if (this.getAngerTime() == 0) {
					this.stopAnger();
				}
			}
		}
	}

	default boolean shouldAngerAt(LivingEntity entity, ServerWorld world) {
		if (!this.canTarget(entity)) {
			return false;
		} else {
			return entity.getType() == EntityType.PLAYER && this.isUniversallyAngry(world) ? true : entity.getUuid().equals(this.getAngryAt());
		}
	}

	default boolean isUniversallyAngry(ServerWorld world) {
		return world.getGameRules().getBoolean(GameRules.UNIVERSAL_ANGER) && this.hasAngerTime() && this.getAngryAt() == null;
	}

	default boolean hasAngerTime() {
		return this.getAngerTime() > 0;
	}

	default void forgive(ServerWorld world, PlayerEntity player) {
		if (world.getGameRules().getBoolean(GameRules.FORGIVE_DEAD_PLAYERS)) {
			if (player.getUuid().equals(this.getAngryAt())) {
				this.stopAnger();
			}
		}
	}

	default void universallyAnger() {
		this.stopAnger();
		this.chooseRandomAngerTime();
	}

	default void stopAnger() {
		this.setAttacker(null);
		this.setAngryAt(null);
		this.setTarget(null);
		this.setAngerTime(0);
	}

	@Nullable
	LivingEntity getAttacker();

	void setAttacker(@Nullable LivingEntity attacker);

	void setTarget(@Nullable LivingEntity target);

	boolean canTarget(LivingEntity target);

	@Nullable
	LivingEntity getTarget();
}
