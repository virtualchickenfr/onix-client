package net.minecraft.entity;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.EntityAttachS2CPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import org.jetbrains.annotations.Nullable;

public interface Leashable {
	String LEASH_NBT_KEY = "leash";
	double MAX_LEASH_LENGTH = 10.0;
	double SHORT_LEASH_LENGTH = 6.0;

	@Nullable
	Leashable.LeashData getLeashData();

	void setLeashData(@Nullable Leashable.LeashData leashData);

	default boolean isLeashed() {
		return this.getLeashData() != null && this.getLeashData().leashHolder != null;
	}

	default boolean mightBeLeashed() {
		return this.getLeashData() != null;
	}

	default boolean canLeashAttachTo() {
		return this.canBeLeashed() && !this.isLeashed();
	}

	default boolean canBeLeashed() {
		return true;
	}

	default void setUnresolvedLeashHolderId(int unresolvedLeashHolderId) {
		this.setLeashData(new Leashable.LeashData(unresolvedLeashHolderId));
		detachLeash((Entity & Leashable)this, false, false);
	}

	default void readLeashDataFromNbt(NbtCompound nbt) {
		Leashable.LeashData leashData = (Leashable.LeashData)nbt.get("leash", Leashable.LeashData.CODEC).orElse(null);
		if (this.getLeashData() != null && leashData == null) {
			this.detachLeashWithoutDrop();
		}

		this.setLeashData(leashData);
	}

	default void writeLeashDataToNbt(NbtCompound nbt, @Nullable Leashable.LeashData leashData) {
		nbt.putNullable("leash", Leashable.LeashData.CODEC, leashData);
	}

	private static <E extends Entity & Leashable> void resolveLeashData(E entity, Leashable.LeashData leashData) {
		if (leashData.unresolvedLeashData != null && entity.getWorld() instanceof ServerWorld serverWorld) {
			Optional<UUID> optional = leashData.unresolvedLeashData.left();
			Optional<BlockPos> optional2 = leashData.unresolvedLeashData.right();
			if (optional.isPresent()) {
				Entity entity2 = serverWorld.getEntity((UUID)optional.get());
				if (entity2 != null) {
					attachLeash(entity, entity2, true);
					return;
				}
			} else if (optional2.isPresent()) {
				attachLeash(entity, LeashKnotEntity.getOrCreate(serverWorld, (BlockPos)optional2.get()), true);
				return;
			}

			if (entity.age > 100) {
				entity.dropItem(serverWorld, Items.LEAD);
				entity.setLeashData(null);
			}
		}
	}

	default void detachLeash() {
		detachLeash((Entity & Leashable)this, true, true);
	}

	default void detachLeashWithoutDrop() {
		detachLeash((Entity & Leashable)this, true, false);
	}

	default void onLeashRemoved() {
	}

	private static <E extends Entity & Leashable> void detachLeash(E entity, boolean sendPacket, boolean dropItem) {
		Leashable.LeashData leashData = entity.getLeashData();
		if (leashData != null && leashData.leashHolder != null) {
			entity.setLeashData(null);
			entity.onLeashRemoved();
			if (entity.getWorld() instanceof ServerWorld serverWorld) {
				if (dropItem) {
					entity.dropItem(serverWorld, Items.LEAD);
				}

				if (sendPacket) {
					serverWorld.getChunkManager().sendToOtherNearbyPlayers(entity, new EntityAttachS2CPacket(entity, null));
				}
			}
		}
	}

	static <E extends Entity & Leashable> void tickLeash(ServerWorld world, E entity) {
		Leashable.LeashData leashData = entity.getLeashData();
		if (leashData != null && leashData.unresolvedLeashData != null) {
			resolveLeashData(entity, leashData);
		}

		if (leashData != null && leashData.leashHolder != null) {
			if (!entity.isAlive() || !leashData.leashHolder.isAlive()) {
				if (world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
					entity.detachLeash();
				} else {
					entity.detachLeashWithoutDrop();
				}
			}

			Entity entity2 = entity.getLeashHolder();
			if (entity2 != null && entity2.getWorld() == entity.getWorld()) {
				float f = entity.distanceTo(entity2);
				if (!entity.beforeLeashTick(entity2, f)) {
					return;
				}

				if (f > 10.0) {
					entity.breakLongLeash();
				} else if (f > 6.0) {
					entity.applyLeashElasticity(entity2, f);
					entity.limitFallDistance();
				} else {
					entity.onShortLeashTick(entity2);
				}
			}
		}
	}

	/**
	 * Called before the default leash-ticking logic.
	 * Subclasses can override this to add their own logic to it.
	 * 
	 * @return whether the default logic should run after this
	 * 
	 * @see Leashable#tickLeash
	 */
	default boolean beforeLeashTick(Entity leashHolder, float distance) {
		return true;
	}

	default void breakLongLeash() {
		this.detachLeash();
	}

	default void onShortLeashTick(Entity entity) {
	}

	default void applyLeashElasticity(Entity leashHolder, float distance) {
		applyLeashElasticity((Entity & Leashable)this, leashHolder, distance);
	}

	private static <E extends Entity & Leashable> void applyLeashElasticity(E entity, Entity leashHolder, float distance) {
		double d = (leashHolder.getX() - entity.getX()) / distance;
		double e = (leashHolder.getY() - entity.getY()) / distance;
		double f = (leashHolder.getZ() - entity.getZ()) / distance;
		entity.setVelocity(entity.getVelocity().add(Math.copySign(d * d * 0.4, d), Math.copySign(e * e * 0.4, e), Math.copySign(f * f * 0.4, f)));
	}

	default void attachLeash(Entity leashHolder, boolean sendPacket) {
		attachLeash((Entity & Leashable)this, leashHolder, sendPacket);
	}

	private static <E extends Entity & Leashable> void attachLeash(E entity, Entity leashHolder, boolean sendPacket) {
		Leashable.LeashData leashData = entity.getLeashData();
		if (leashData == null) {
			leashData = new Leashable.LeashData(leashHolder);
			entity.setLeashData(leashData);
		} else {
			leashData.setLeashHolder(leashHolder);
		}

		if (sendPacket && entity.getWorld() instanceof ServerWorld serverWorld) {
			serverWorld.getChunkManager().sendToOtherNearbyPlayers(entity, new EntityAttachS2CPacket(entity, leashHolder));
		}

		if (entity.hasVehicle()) {
			entity.stopRiding();
		}
	}

	@Nullable
	default Entity getLeashHolder() {
		return getLeashHolder((Entity & Leashable)this);
	}

	@Nullable
	private static <E extends Entity & Leashable> Entity getLeashHolder(E entity) {
		Leashable.LeashData leashData = entity.getLeashData();
		if (leashData == null) {
			return null;
		} else {
			if (leashData.unresolvedLeashHolderId != 0 && entity.getWorld().isClient) {
				Entity var3 = entity.getWorld().getEntityById(leashData.unresolvedLeashHolderId);
				if (var3 instanceof Entity) {
					leashData.setLeashHolder(var3);
				}
			}

			return leashData.leashHolder;
		}
	}

	public static final class LeashData {
		public static final Codec<Leashable.LeashData> CODEC = Codec.xor(Uuids.INT_STREAM_CODEC.fieldOf("UUID").codec(), BlockPos.CODEC)
			.xmap(
				Leashable.LeashData::new,
				data -> {
					if (data.leashHolder instanceof LeashKnotEntity leashKnotEntity) {
						return Either.right(leashKnotEntity.getAttachedBlockPos());
					} else {
						return data.leashHolder != null
							? Either.left(data.leashHolder.getUuid())
							: (Either)Objects.requireNonNull(data.unresolvedLeashData, "Invalid LeashData had no attachment");
					}
				}
			);
		int unresolvedLeashHolderId;
		@Nullable
		public Entity leashHolder;
		@Nullable
		public Either<UUID, BlockPos> unresolvedLeashData;

		private LeashData(Either<UUID, BlockPos> unresolvedLeashData) {
			this.unresolvedLeashData = unresolvedLeashData;
		}

		LeashData(Entity leashHolder) {
			this.leashHolder = leashHolder;
		}

		LeashData(int unresolvedLeashHolderId) {
			this.unresolvedLeashHolderId = unresolvedLeashHolderId;
		}

		public void setLeashHolder(Entity leashHolder) {
			this.leashHolder = leashHolder;
			this.unresolvedLeashData = null;
			this.unresolvedLeashHolderId = 0;
		}
	}
}
