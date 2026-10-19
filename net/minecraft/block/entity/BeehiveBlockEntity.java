package net.minecraft.block.entity;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.FireBlock;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BeesComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.network.DebugInfoSender;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.annotation.Debug;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

public class BeehiveBlockEntity extends BlockEntity {
	private static final String FLOWER_POS_KEY = "flower_pos";
	private static final String BEES_KEY = "bees";
	static final List<String> IRRELEVANT_BEE_NBT_KEYS = Arrays.asList(
		"Air",
		"drop_chances",
		"ArmorItems",
		"Brain",
		"CanPickUpLoot",
		"DeathTime",
		"fall_distance",
		"FallFlying",
		"Fire",
		"HandItems",
		"HurtByTimestamp",
		"HurtTime",
		"LeftHanded",
		"Motion",
		"NoGravity",
		"OnGround",
		"PortalCooldown",
		"Pos",
		"Rotation",
		"SleepingX",
		"SleepingY",
		"SleepingZ",
		"CannotEnterHiveTicks",
		"TicksSincePollination",
		"CropsGrownSincePollination",
		"hive_pos",
		"Passengers",
		"leash",
		"UUID"
	);
	public static final int MAX_BEE_COUNT = 3;
	private static final int ANGERED_CANNOT_ENTER_HIVE_TICKS = 400;
	private static final int MIN_OCCUPATION_TICKS_WITH_NECTAR = 2400;
	public static final int MIN_OCCUPATION_TICKS_WITHOUT_NECTAR = 600;
	private final List<BeehiveBlockEntity.Bee> bees = Lists.<BeehiveBlockEntity.Bee>newArrayList();
	@Nullable
	private BlockPos flowerPos;

	public BeehiveBlockEntity(BlockPos pos, BlockState state) {
		super(BlockEntityType.BEEHIVE, pos, state);
	}

	@Override
	public void markDirty() {
		if (this.isNearFire()) {
			this.angerBees(null, this.world.getBlockState(this.getPos()), BeehiveBlockEntity.BeeState.EMERGENCY);
		}

		super.markDirty();
	}

	public boolean isNearFire() {
		if (this.world == null) {
			return false;
		} else {
			for (BlockPos blockPos : BlockPos.iterate(this.pos.add(-1, -1, -1), this.pos.add(1, 1, 1))) {
				if (this.world.getBlockState(blockPos).getBlock() instanceof FireBlock) {
					return true;
				}
			}

			return false;
		}
	}

	public boolean hasNoBees() {
		return this.bees.isEmpty();
	}

	public boolean isFullOfBees() {
		return this.bees.size() == 3;
	}

	public void angerBees(@Nullable PlayerEntity player, BlockState state, BeehiveBlockEntity.BeeState beeState) {
		List<Entity> list = this.tryReleaseBee(state, beeState);
		if (player != null) {
			for (Entity entity : list) {
				if (entity instanceof BeeEntity beeEntity && player.getPos().squaredDistanceTo(entity.getPos()) <= 16.0) {
					if (!this.isSmoked()) {
						beeEntity.setTarget(player);
					} else {
						beeEntity.setCannotEnterHiveTicks(400);
					}
				}
			}
		}
	}

	private List<Entity> tryReleaseBee(BlockState state, BeehiveBlockEntity.BeeState beeState) {
		List<Entity> list = Lists.<Entity>newArrayList();
		this.bees.removeIf(bee -> releaseBee(this.world, this.pos, state, bee.createData(), list, beeState, this.flowerPos));
		if (!list.isEmpty()) {
			super.markDirty();
		}

		return list;
	}

	@Debug
	public int getBeeCount() {
		return this.bees.size();
	}

	public static int getHoneyLevel(BlockState state) {
		return (Integer)state.get(BeehiveBlock.HONEY_LEVEL);
	}

	@Debug
	public boolean isSmoked() {
		return CampfireBlock.isLitCampfireInRange(this.world, this.getPos());
	}

	public void tryEnterHive(BeeEntity entity) {
		if (this.bees.size() < 3) {
			entity.stopRiding();
			entity.removeAllPassengers();
			entity.detachLeash();
			this.addBee(BeehiveBlockEntity.BeeData.of(entity));
			if (this.world != null) {
				if (entity.hasFlower() && (!this.hasFlowerPos() || this.world.random.nextBoolean())) {
					this.flowerPos = entity.getFlowerPos();
				}

				BlockPos blockPos = this.getPos();
				this.world
					.playSound(
						null, (double)blockPos.getX(), (double)blockPos.getY(), (double)blockPos.getZ(), SoundEvents.BLOCK_BEEHIVE_ENTER, SoundCategory.BLOCKS, 1.0F, 1.0F
					);
				this.world.emitGameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Emitter.of(entity, this.getCachedState()));
			}

			entity.discard();
			super.markDirty();
		}
	}

	public void addBee(BeehiveBlockEntity.BeeData bee) {
		this.bees.add(new BeehiveBlockEntity.Bee(bee));
	}

	private static boolean releaseBee(
		World world,
		BlockPos pos,
		BlockState state,
		BeehiveBlockEntity.BeeData bee,
		@Nullable List<Entity> entities,
		BeehiveBlockEntity.BeeState beeState,
		@Nullable BlockPos flowerPos
	) {
		if (BeeEntity.isNightOrRaining(world) && beeState != BeehiveBlockEntity.BeeState.EMERGENCY) {
			return false;
		} else {
			Direction direction = state.get(BeehiveBlock.FACING);
			BlockPos blockPos = pos.offset(direction);
			boolean bl = !world.getBlockState(blockPos).getCollisionShape(world, blockPos).isEmpty();
			if (bl && beeState != BeehiveBlockEntity.BeeState.EMERGENCY) {
				return false;
			} else {
				Entity entity = bee.loadEntity(world, pos);
				if (entity != null) {
					if (entity instanceof BeeEntity beeEntity) {
						if (flowerPos != null && !beeEntity.hasFlower() && world.random.nextFloat() < 0.9F) {
							beeEntity.setFlowerPos(flowerPos);
						}

						if (beeState == BeehiveBlockEntity.BeeState.HONEY_DELIVERED) {
							beeEntity.onHoneyDelivered();
							if (state.isIn(BlockTags.BEEHIVES, statex -> statex.contains(BeehiveBlock.HONEY_LEVEL))) {
								int i = getHoneyLevel(state);
								if (i < 5) {
									int j = world.random.nextInt(100) == 0 ? 2 : 1;
									if (i + j > 5) {
										j--;
									}

									world.setBlockState(pos, state.with(BeehiveBlock.HONEY_LEVEL, i + j));
								}
							}
						}

						if (entities != null) {
							entities.add(beeEntity);
						}

						float f = entity.getWidth();
						double d = bl ? 0.0 : 0.55 + f / 2.0F;
						double e = pos.getX() + 0.5 + d * direction.getOffsetX();
						double g = pos.getY() + 0.5 - entity.getHeight() / 2.0F;
						double h = pos.getZ() + 0.5 + d * direction.getOffsetZ();
						entity.refreshPositionAndAngles(e, g, h, entity.getYaw(), entity.getPitch());
					}

					world.playSound(null, pos, SoundEvents.BLOCK_BEEHIVE_EXIT, SoundCategory.BLOCKS, 1.0F, 1.0F);
					world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(entity, world.getBlockState(pos)));
					return world.spawnEntity(entity);
				} else {
					return false;
				}
			}
		}
	}

	private boolean hasFlowerPos() {
		return this.flowerPos != null;
	}

	private static void tickBees(World world, BlockPos pos, BlockState state, List<BeehiveBlockEntity.Bee> bees, @Nullable BlockPos flowerPos) {
		boolean bl = false;
		Iterator<BeehiveBlockEntity.Bee> iterator = bees.iterator();

		while (iterator.hasNext()) {
			BeehiveBlockEntity.Bee bee = (BeehiveBlockEntity.Bee)iterator.next();
			if (bee.canExitHive()) {
				BeehiveBlockEntity.BeeState beeState = bee.hasNectar() ? BeehiveBlockEntity.BeeState.HONEY_DELIVERED : BeehiveBlockEntity.BeeState.BEE_RELEASED;
				if (releaseBee(world, pos, state, bee.createData(), null, beeState, flowerPos)) {
					bl = true;
					iterator.remove();
				}
			}
		}

		if (bl) {
			markDirty(world, pos, state);
		}
	}

	public static void serverTick(World world, BlockPos pos, BlockState state, BeehiveBlockEntity blockEntity) {
		tickBees(world, pos, state, blockEntity.bees, blockEntity.flowerPos);
		if (!blockEntity.bees.isEmpty() && world.getRandom().nextDouble() < 0.005) {
			double d = pos.getX() + 0.5;
			double e = pos.getY();
			double f = pos.getZ() + 0.5;
			world.playSound(null, d, e, f, SoundEvents.BLOCK_BEEHIVE_WORK, SoundCategory.BLOCKS, 1.0F, 1.0F);
		}

		DebugInfoSender.sendBeehiveDebugData(world, pos, state, blockEntity);
	}

	@Override
	protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.readNbt(nbt, registries);
		this.bees.clear();
		((List)nbt.get("bees", BeehiveBlockEntity.BeeData.LIST_CODEC).orElse(List.of())).forEach(this::addBee);
		this.flowerPos = (BlockPos)nbt.get("flower_pos", BlockPos.CODEC).orElse(null);
	}

	@Override
	protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
		super.writeNbt(nbt, registries);
		nbt.put("bees", BeehiveBlockEntity.BeeData.LIST_CODEC, this.createBeesData());
		nbt.putNullable("flower_pos", BlockPos.CODEC, this.flowerPos);
	}

	@Override
	protected void readComponents(ComponentsAccess components) {
		super.readComponents(components);
		this.bees.clear();
		List<BeehiveBlockEntity.BeeData> list = components.getOrDefault(DataComponentTypes.BEES, BeesComponent.DEFAULT).bees();
		list.forEach(this::addBee);
	}

	@Override
	protected void addComponents(ComponentMap.Builder builder) {
		super.addComponents(builder);
		builder.add(DataComponentTypes.BEES, new BeesComponent(this.createBeesData()));
	}

	@Override
	public void removeFromCopiedStackNbt(NbtCompound nbt) {
		super.removeFromCopiedStackNbt(nbt);
		nbt.remove("bees");
	}

	private List<BeehiveBlockEntity.BeeData> createBeesData() {
		return this.bees.stream().map(BeehiveBlockEntity.Bee::createData).toList();
	}

	static class Bee {
		private final BeehiveBlockEntity.BeeData data;
		private int ticksInHive;

		Bee(BeehiveBlockEntity.BeeData data) {
			this.data = data;
			this.ticksInHive = data.ticksInHive();
		}

		public boolean canExitHive() {
			return this.ticksInHive++ > this.data.minTicksInHive;
		}

		public BeehiveBlockEntity.BeeData createData() {
			return new BeehiveBlockEntity.BeeData(this.data.entityData, this.ticksInHive, this.data.minTicksInHive);
		}

		public boolean hasNectar() {
			return this.data.entityData.getNbt().getBoolean("HasNectar", false);
		}
	}

	public record BeeData(NbtComponent entityData, int ticksInHive, int minTicksInHive) {
		public static final Codec<BeehiveBlockEntity.BeeData> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
					NbtComponent.CODEC.optionalFieldOf("entity_data", NbtComponent.DEFAULT).forGetter(BeehiveBlockEntity.BeeData::entityData),
					Codec.INT.fieldOf("ticks_in_hive").forGetter(BeehiveBlockEntity.BeeData::ticksInHive),
					Codec.INT.fieldOf("min_ticks_in_hive").forGetter(BeehiveBlockEntity.BeeData::minTicksInHive)
				)
				.apply(instance, BeehiveBlockEntity.BeeData::new)
		);
		public static final Codec<List<BeehiveBlockEntity.BeeData>> LIST_CODEC = CODEC.listOf();
		public static final PacketCodec<ByteBuf, BeehiveBlockEntity.BeeData> PACKET_CODEC = PacketCodec.tuple(
			NbtComponent.PACKET_CODEC,
			BeehiveBlockEntity.BeeData::entityData,
			PacketCodecs.VAR_INT,
			BeehiveBlockEntity.BeeData::ticksInHive,
			PacketCodecs.VAR_INT,
			BeehiveBlockEntity.BeeData::minTicksInHive,
			BeehiveBlockEntity.BeeData::new
		);

		public static BeehiveBlockEntity.BeeData of(Entity entity) {
			NbtCompound nbtCompound = new NbtCompound();
			entity.saveNbt(nbtCompound);
			BeehiveBlockEntity.IRRELEVANT_BEE_NBT_KEYS.forEach(nbtCompound::remove);
			boolean bl = nbtCompound.getBoolean("HasNectar", false);
			return new BeehiveBlockEntity.BeeData(NbtComponent.of(nbtCompound), 0, bl ? 2400 : 600);
		}

		public static BeehiveBlockEntity.BeeData create(int ticksInHive) {
			NbtCompound nbtCompound = new NbtCompound();
			nbtCompound.putString("id", Registries.ENTITY_TYPE.getId(EntityType.BEE).toString());
			return new BeehiveBlockEntity.BeeData(NbtComponent.of(nbtCompound), ticksInHive, 600);
		}

		@Nullable
		public Entity loadEntity(World world, BlockPos pos) {
			NbtCompound nbtCompound = this.entityData.copyNbt();
			BeehiveBlockEntity.IRRELEVANT_BEE_NBT_KEYS.forEach(nbtCompound::remove);
			Entity entity = EntityType.loadEntityWithPassengers(nbtCompound, world, SpawnReason.LOAD, entityx -> entityx);
			if (entity != null && entity.getType().isIn(EntityTypeTags.BEEHIVE_INHABITORS)) {
				entity.setNoGravity(true);
				if (entity instanceof BeeEntity beeEntity) {
					beeEntity.setHivePos(pos);
					tickEntity(this.ticksInHive, beeEntity);
				}

				return entity;
			} else {
				return null;
			}
		}

		private static void tickEntity(int ticksInHive, BeeEntity beeEntity) {
			int i = beeEntity.getBreedingAge();
			if (i < 0) {
				beeEntity.setBreedingAge(Math.min(0, i + ticksInHive));
			} else if (i > 0) {
				beeEntity.setBreedingAge(Math.max(0, i - ticksInHive));
			}

			beeEntity.setLoveTicks(Math.max(0, beeEntity.getLoveTicks() - ticksInHive));
		}
	}

	public static enum BeeState {
		HONEY_DELIVERED,
		BEE_RELEASED,
		EMERGENCY;
	}
}
