package net.minecraft.entity;

import com.mojang.logging.LogUtils;
import java.util.function.Predicate;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ConcretePowderBlock;
import net.minecraft.block.Falling;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.AutomaticItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class FallingBlockEntity extends Entity {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final BlockState DEFAULT_BLOCK_STATE = Blocks.SAND.getDefaultState();
	private static final int DEFAULT_TIME = 0;
	private static final float DEFAULT_FALL_HURT_AMOUNT = 0.0F;
	private static final int DEFAULT_FALL_HURT_MAX = 40;
	private static final boolean DEFAULT_DROP_ITEM = true;
	private static final boolean DEFAULT_DESTROYED_ON_LANDING = false;
	private BlockState blockState = DEFAULT_BLOCK_STATE;
	public int timeFalling = 0;
	public boolean dropItem = true;
	private boolean destroyedOnLanding = false;
	private boolean hurtEntities;
	private int fallHurtMax = 40;
	private float fallHurtAmount = 0.0F;
	@Nullable
	public NbtCompound blockEntityData;
	public boolean shouldDupe;
	protected static final TrackedData<BlockPos> BLOCK_POS = DataTracker.registerData(FallingBlockEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);

	public FallingBlockEntity(EntityType<? extends FallingBlockEntity> entityType, World world) {
		super(entityType, world);
	}

	private FallingBlockEntity(World world, double x, double y, double z, BlockState blockState) {
		this(EntityType.FALLING_BLOCK, world);
		this.blockState = blockState;
		this.intersectionChecked = true;
		this.setPosition(x, y, z);
		this.setVelocity(Vec3d.ZERO);
		this.lastX = x;
		this.lastY = y;
		this.lastZ = z;
		this.setFallingBlockPos(this.getBlockPos());
	}

	/**
	 * Spawns a falling block entity at {@code pos} from the block {@code state}.
	 * @return the spawned entity
	 */
	public static FallingBlockEntity spawnFromBlock(World world, BlockPos pos, BlockState state) {
		FallingBlockEntity fallingBlockEntity = new FallingBlockEntity(
			world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, state.contains(Properties.WATERLOGGED) ? state.with(Properties.WATERLOGGED, false) : state
		);
		world.setBlockState(pos, state.getFluidState().getBlockState(), Block.NOTIFY_ALL);
		world.spawnEntity(fallingBlockEntity);
		return fallingBlockEntity;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public final boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (!this.isAlwaysInvulnerableTo(source)) {
			this.scheduleVelocityUpdate();
		}

		return false;
	}

	public void setFallingBlockPos(BlockPos pos) {
		this.dataTracker.set(BLOCK_POS, pos);
	}

	public BlockPos getFallingBlockPos() {
		return this.dataTracker.get(BLOCK_POS);
	}

	@Override
	protected Entity.MoveEffect getMoveEffect() {
		return Entity.MoveEffect.NONE;
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		builder.add(BLOCK_POS, BlockPos.ORIGIN);
	}

	@Override
	public boolean canHit() {
		return !this.isRemoved();
	}

	@Override
	protected double getGravity() {
		return 0.04;
	}

	@Override
	public void tick() {
		if (this.blockState.isAir()) {
			this.discard();
		} else {
			Block block = this.blockState.getBlock();
			this.timeFalling++;
			this.applyGravity();
			this.move(MovementType.SELF, this.getVelocity());
			this.tickBlockCollision();
			this.tickPortalTeleportation();
			if (this.getWorld() instanceof ServerWorld serverWorld && (this.isAlive() || this.shouldDupe)) {
				BlockPos blockPos = this.getBlockPos();
				boolean bl = this.blockState.getBlock() instanceof ConcretePowderBlock;
				boolean bl2 = bl && this.getWorld().getFluidState(blockPos).isIn(FluidTags.WATER);
				double d = this.getVelocity().lengthSquared();
				if (bl && d > 1.0) {
					BlockHitResult blockHitResult = this.getWorld()
						.raycast(
							new RaycastContext(
								new Vec3d(this.lastX, this.lastY, this.lastZ), this.getPos(), RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.SOURCE_ONLY, this
							)
						);
					if (blockHitResult.getType() != HitResult.Type.MISS && this.getWorld().getFluidState(blockHitResult.getBlockPos()).isIn(FluidTags.WATER)) {
						blockPos = blockHitResult.getBlockPos();
						bl2 = true;
					}
				}

				if (!this.isOnGround() && !bl2) {
					if (this.timeFalling > 100 && (blockPos.getY() <= this.getWorld().getBottomY() || blockPos.getY() > this.getWorld().getTopYInclusive())
						|| this.timeFalling > 600) {
						if (this.dropItem && serverWorld.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
							this.dropItem(serverWorld, block);
						}

						this.discard();
					}
				} else {
					BlockState blockState = this.getWorld().getBlockState(blockPos);
					this.setVelocity(this.getVelocity().multiply(0.7, -0.5, 0.7));
					if (!blockState.isOf(Blocks.MOVING_PISTON)) {
						if (!this.destroyedOnLanding) {
							boolean bl3 = blockState.canReplace(new AutomaticItemPlacementContext(this.getWorld(), blockPos, Direction.DOWN, ItemStack.EMPTY, Direction.UP));
							boolean bl4 = FallingBlock.canFallThrough(this.getWorld().getBlockState(blockPos.down())) && (!bl || !bl2);
							boolean bl5 = this.blockState.canPlaceAt(this.getWorld(), blockPos) && !bl4;
							if (bl3 && bl5) {
								if (this.blockState.contains(Properties.WATERLOGGED) && this.getWorld().getFluidState(blockPos).getFluid() == Fluids.WATER) {
									this.blockState = this.blockState.with(Properties.WATERLOGGED, true);
								}

								if (this.getWorld().setBlockState(blockPos, this.blockState, Block.NOTIFY_ALL)) {
									((ServerWorld)this.getWorld())
										.getChunkManager()
										.chunkLoadingManager
										.sendToOtherNearbyPlayers(this, new BlockUpdateS2CPacket(blockPos, this.getWorld().getBlockState(blockPos)));
									this.discard();
									if (block instanceof Falling) {
										((Falling)block).onLanding(this.getWorld(), blockPos, this.blockState, blockState, this);
									}

									if (this.blockEntityData != null && this.blockState.hasBlockEntity()) {
										BlockEntity blockEntity = this.getWorld().getBlockEntity(blockPos);
										if (blockEntity != null) {
											NbtCompound nbtCompound = blockEntity.createNbt(this.getWorld().getRegistryManager());
											this.blockEntityData.forEach((string, nbtElement) -> nbtCompound.put(string, nbtElement.copy()));

											try {
												blockEntity.read(nbtCompound, this.getWorld().getRegistryManager());
											} catch (Exception var15) {
												LOGGER.error("Failed to load block entity from falling block", (Throwable)var15);
											}

											blockEntity.markDirty();
										}
									}
								} else if (this.dropItem && serverWorld.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
									this.discard();
									this.onDestroyedOnLanding(block, blockPos);
									this.dropItem(serverWorld, block);
								}
							} else {
								this.discard();
								if (this.dropItem && serverWorld.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
									this.onDestroyedOnLanding(block, blockPos);
									this.dropItem(serverWorld, block);
								}
							}
						} else {
							this.discard();
							this.onDestroyedOnLanding(block, blockPos);
						}
					}
				}
			}

			this.setVelocity(this.getVelocity().multiply(0.98));
		}
	}

	public void onDestroyedOnLanding(Block block, BlockPos pos) {
		if (block instanceof Falling) {
			((Falling)block).onDestroyedOnLanding(this.getWorld(), pos, this);
		}
	}

	@Override
	public boolean handleFallDamage(double fallDistance, float damagePerDistance, DamageSource damageSource) {
		if (!this.hurtEntities) {
			return false;
		} else {
			int i = MathHelper.ceil(fallDistance - 1.0);
			if (i < 0) {
				return false;
			} else {
				Predicate<Entity> predicate = EntityPredicates.EXCEPT_CREATIVE_OR_SPECTATOR.and(EntityPredicates.VALID_LIVING_ENTITY);
				DamageSource damageSource2 = this.blockState.getBlock() instanceof Falling falling
					? falling.getDamageSource(this)
					: this.getDamageSources().fallingBlock(this);
				float f = Math.min(MathHelper.floor(i * this.fallHurtAmount), this.fallHurtMax);
				this.getWorld().getOtherEntities(this, this.getBoundingBox(), predicate).forEach(entity -> entity.serverDamage(damageSource2, f));
				boolean bl = this.blockState.isIn(BlockTags.ANVIL);
				if (bl && f > 0.0F && this.random.nextFloat() < 0.05F + i * 0.05F) {
					BlockState blockState = AnvilBlock.getLandingState(this.blockState);
					if (blockState == null) {
						this.destroyedOnLanding = true;
					} else {
						this.blockState = blockState;
					}
				}

				return false;
			}
		}
	}

	@Override
	protected void writeCustomDataToNbt(NbtCompound nbt) {
		RegistryOps<NbtElement> registryOps = this.getRegistryManager().getOps(NbtOps.INSTANCE);
		nbt.put("BlockState", BlockState.CODEC, registryOps, this.blockState);
		nbt.putInt("Time", this.timeFalling);
		nbt.putBoolean("DropItem", this.dropItem);
		nbt.putBoolean("HurtEntities", this.hurtEntities);
		nbt.putFloat("FallHurtAmount", this.fallHurtAmount);
		nbt.putInt("FallHurtMax", this.fallHurtMax);
		if (this.blockEntityData != null) {
			nbt.put("TileEntityData", this.blockEntityData);
		}

		nbt.putBoolean("CancelDrop", this.destroyedOnLanding);
	}

	@Override
	protected void readCustomDataFromNbt(NbtCompound nbt) {
		RegistryOps<NbtElement> registryOps = this.getRegistryManager().getOps(NbtOps.INSTANCE);
		this.blockState = (BlockState)nbt.get("BlockState", BlockState.CODEC, registryOps).orElse(DEFAULT_BLOCK_STATE);
		this.timeFalling = nbt.getInt("Time", 0);
		boolean bl = this.blockState.isIn(BlockTags.ANVIL);
		this.hurtEntities = nbt.getBoolean("HurtEntities", bl);
		this.fallHurtAmount = nbt.getFloat("FallHurtAmount", 0.0F);
		this.fallHurtMax = nbt.getInt("FallHurtMax", 40);
		this.dropItem = nbt.getBoolean("DropItem", true);
		this.blockEntityData = (NbtCompound)nbt.getCompound("TileEntityData").map(NbtCompound::copy).orElse(null);
		this.destroyedOnLanding = nbt.getBoolean("CancelDrop", false);
	}

	public void setHurtEntities(float fallHurtAmount, int fallHurtMax) {
		this.hurtEntities = true;
		this.fallHurtAmount = fallHurtAmount;
		this.fallHurtMax = fallHurtMax;
	}

	public void setDestroyedOnLanding() {
		this.destroyedOnLanding = true;
	}

	@Override
	public boolean doesRenderOnFire() {
		return false;
	}

	@Override
	public void populateCrashReport(CrashReportSection section) {
		super.populateCrashReport(section);
		section.add("Immitating BlockState", this.blockState.toString());
	}

	public BlockState getBlockState() {
		return this.blockState;
	}

	@Override
	protected Text getDefaultName() {
		return Text.translatable("entity.minecraft.falling_block_type", this.blockState.getBlock().getName());
	}

	@Override
	public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry entityTrackerEntry) {
		return new EntitySpawnS2CPacket(this, entityTrackerEntry, Block.getRawIdFromState(this.getBlockState()));
	}

	@Override
	public void onSpawnPacket(EntitySpawnS2CPacket packet) {
		super.onSpawnPacket(packet);
		this.blockState = Block.getStateFromRawId(packet.getEntityData());
		this.intersectionChecked = true;
		double d = packet.getX();
		double e = packet.getY();
		double f = packet.getZ();
		this.setPosition(d, e, f);
		this.setFallingBlockPos(this.getBlockPos());
	}

	@Nullable
	@Override
	public Entity teleportTo(TeleportTarget teleportTarget) {
		RegistryKey<World> registryKey = teleportTarget.world().getRegistryKey();
		RegistryKey<World> registryKey2 = this.getWorld().getRegistryKey();
		boolean bl = (registryKey2 == World.END || registryKey == World.END) && registryKey2 != registryKey;
		Entity entity = super.teleportTo(teleportTarget);
		this.shouldDupe = entity != null && bl;
		return entity;
	}
}
