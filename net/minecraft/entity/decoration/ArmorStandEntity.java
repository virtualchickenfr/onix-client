package net.minecraft.entity.decoration;

import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;

public class ArmorStandEntity extends LivingEntity {
	public static final int field_30443 = 5;
	private static final boolean field_30445 = true;
	public static final EulerAngle DEFAULT_HEAD_ROTATION = new EulerAngle(0.0F, 0.0F, 0.0F);
	public static final EulerAngle DEFAULT_BODY_ROTATION = new EulerAngle(0.0F, 0.0F, 0.0F);
	public static final EulerAngle DEFAULT_LEFT_ARM_ROTATION = new EulerAngle(-10.0F, 0.0F, -10.0F);
	public static final EulerAngle DEFAULT_RIGHT_ARM_ROTATION = new EulerAngle(-15.0F, 0.0F, 10.0F);
	public static final EulerAngle DEFAULT_LEFT_LEG_ROTATION = new EulerAngle(-1.0F, 0.0F, -1.0F);
	public static final EulerAngle DEFAULT_RIGHT_LEG_ROTATION = new EulerAngle(1.0F, 0.0F, 1.0F);
	private static final EntityDimensions MARKER_DIMENSIONS = EntityDimensions.fixed(0.0F, 0.0F);
	private static final EntityDimensions SMALL_DIMENSIONS = EntityType.ARMOR_STAND.getDimensions().scaled(0.5F).withEyeHeight(0.9875F);
	private static final double field_30447 = 0.1;
	private static final double field_30448 = 0.9;
	private static final double field_30449 = 0.4;
	private static final double field_30450 = 1.6;
	public static final int field_30446 = 8;
	public static final int field_30451 = 16;
	public static final int SMALL_FLAG = 1;
	public static final int SHOW_ARMS_FLAG = 4;
	public static final int HIDE_BASE_PLATE_FLAG = 8;
	public static final int MARKER_FLAG = 16;
	public static final TrackedData<Byte> ARMOR_STAND_FLAGS = DataTracker.registerData(ArmorStandEntity.class, TrackedDataHandlerRegistry.BYTE);
	public static final TrackedData<EulerAngle> TRACKER_HEAD_ROTATION = DataTracker.registerData(ArmorStandEntity.class, TrackedDataHandlerRegistry.ROTATION);
	public static final TrackedData<EulerAngle> TRACKER_BODY_ROTATION = DataTracker.registerData(ArmorStandEntity.class, TrackedDataHandlerRegistry.ROTATION);
	public static final TrackedData<EulerAngle> TRACKER_LEFT_ARM_ROTATION = DataTracker.registerData(ArmorStandEntity.class, TrackedDataHandlerRegistry.ROTATION);
	public static final TrackedData<EulerAngle> TRACKER_RIGHT_ARM_ROTATION = DataTracker.registerData(ArmorStandEntity.class, TrackedDataHandlerRegistry.ROTATION);
	public static final TrackedData<EulerAngle> TRACKER_LEFT_LEG_ROTATION = DataTracker.registerData(ArmorStandEntity.class, TrackedDataHandlerRegistry.ROTATION);
	public static final TrackedData<EulerAngle> TRACKER_RIGHT_LEG_ROTATION = DataTracker.registerData(ArmorStandEntity.class, TrackedDataHandlerRegistry.ROTATION);
	private static final Predicate<Entity> RIDEABLE_MINECART_PREDICATE = entity -> entity instanceof AbstractMinecartEntity abstractMinecartEntity
		&& abstractMinecartEntity.isRideable();
	private static final boolean field_57644 = false;
	private static final int DEFAULT_DISABLED_SLOTS = 0;
	private static final boolean field_57646 = false;
	private static final boolean field_57647 = false;
	private static final boolean field_57648 = false;
	private static final boolean field_57649 = false;
	private boolean invisible = false;
	public long lastHitTime;
	private int disabledSlots = 0;
	private EulerAngle headRotation = DEFAULT_HEAD_ROTATION;
	private EulerAngle bodyRotation = DEFAULT_BODY_ROTATION;
	private EulerAngle leftArmRotation = DEFAULT_LEFT_ARM_ROTATION;
	private EulerAngle rightArmRotation = DEFAULT_RIGHT_ARM_ROTATION;
	private EulerAngle leftLegRotation = DEFAULT_LEFT_LEG_ROTATION;
	private EulerAngle rightLegRotation = DEFAULT_RIGHT_LEG_ROTATION;

	public ArmorStandEntity(EntityType<? extends ArmorStandEntity> entityType, World world) {
		super(entityType, world);
	}

	public ArmorStandEntity(World world, double x, double y, double z) {
		this(EntityType.ARMOR_STAND, world);
		this.setPosition(x, y, z);
	}

	public static DefaultAttributeContainer.Builder createArmorStandAttributes() {
		return createLivingAttributes().add(EntityAttributes.STEP_HEIGHT, 0.0);
	}

	@Override
	public void calculateDimensions() {
		double d = this.getX();
		double e = this.getY();
		double f = this.getZ();
		super.calculateDimensions();
		this.setPosition(d, e, f);
	}

	private boolean canClip() {
		return !this.isMarker() && !this.hasNoGravity();
	}

	@Override
	public boolean canActVoluntarily() {
		return super.canActVoluntarily() && this.canClip();
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ARMOR_STAND_FLAGS, (byte)0);
		builder.add(TRACKER_HEAD_ROTATION, DEFAULT_HEAD_ROTATION);
		builder.add(TRACKER_BODY_ROTATION, DEFAULT_BODY_ROTATION);
		builder.add(TRACKER_LEFT_ARM_ROTATION, DEFAULT_LEFT_ARM_ROTATION);
		builder.add(TRACKER_RIGHT_ARM_ROTATION, DEFAULT_RIGHT_ARM_ROTATION);
		builder.add(TRACKER_LEFT_LEG_ROTATION, DEFAULT_LEFT_LEG_ROTATION);
		builder.add(TRACKER_RIGHT_LEG_ROTATION, DEFAULT_RIGHT_LEG_ROTATION);
	}

	@Override
	public boolean canUseSlot(EquipmentSlot slot) {
		return slot != EquipmentSlot.BODY && slot != EquipmentSlot.SADDLE && !this.isSlotDisabled(slot);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putBoolean("Invisible", this.isInvisible());
		nbt.putBoolean("Small", this.isSmall());
		nbt.putBoolean("ShowArms", this.shouldShowArms());
		nbt.putInt("DisabledSlots", this.disabledSlots);
		nbt.putBoolean("NoBasePlate", !this.shouldShowBasePlate());
		if (this.isMarker()) {
			nbt.putBoolean("Marker", this.isMarker());
		}

		nbt.put("Pose", this.poseToNbt());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.setInvisible(nbt.getBoolean("Invisible", false));
		this.setSmall(nbt.getBoolean("Small", false));
		this.setShowArms(nbt.getBoolean("ShowArms", false));
		this.disabledSlots = nbt.getInt("DisabledSlots", 0);
		this.setHideBasePlate(nbt.getBoolean("NoBasePlate", false));
		this.setMarker(nbt.getBoolean("Marker", false));
		this.noClip = !this.canClip();
		this.readPoseNbt(nbt.getCompoundOrEmpty("Pose"));
	}

	private void readPoseNbt(NbtCompound nbtCompound) {
		this.setHeadRotation((EulerAngle)nbtCompound.get("Head", EulerAngle.CODEC).orElse(DEFAULT_HEAD_ROTATION));
		this.setBodyRotation((EulerAngle)nbtCompound.get("Body", EulerAngle.CODEC).orElse(DEFAULT_BODY_ROTATION));
		this.setLeftArmRotation((EulerAngle)nbtCompound.get("LeftArm", EulerAngle.CODEC).orElse(DEFAULT_LEFT_ARM_ROTATION));
		this.setRightArmRotation((EulerAngle)nbtCompound.get("RightArm", EulerAngle.CODEC).orElse(DEFAULT_RIGHT_ARM_ROTATION));
		this.setLeftLegRotation((EulerAngle)nbtCompound.get("LeftLeg", EulerAngle.CODEC).orElse(DEFAULT_LEFT_LEG_ROTATION));
		this.setRightLegRotation((EulerAngle)nbtCompound.get("RightLeg", EulerAngle.CODEC).orElse(DEFAULT_RIGHT_LEG_ROTATION));
	}

	private NbtCompound poseToNbt() {
		NbtCompound nbtCompound = new NbtCompound();
		if (!DEFAULT_HEAD_ROTATION.equals(this.headRotation)) {
			nbtCompound.put("Head", EulerAngle.CODEC, this.headRotation);
		}

		if (!DEFAULT_BODY_ROTATION.equals(this.bodyRotation)) {
			nbtCompound.put("Body", EulerAngle.CODEC, this.bodyRotation);
		}

		if (!DEFAULT_LEFT_ARM_ROTATION.equals(this.leftArmRotation)) {
			nbtCompound.put("LeftArm", EulerAngle.CODEC, this.leftArmRotation);
		}

		if (!DEFAULT_RIGHT_ARM_ROTATION.equals(this.rightArmRotation)) {
			nbtCompound.put("RightArm", EulerAngle.CODEC, this.rightArmRotation);
		}

		if (!DEFAULT_LEFT_LEG_ROTATION.equals(this.leftLegRotation)) {
			nbtCompound.put("LeftLeg", EulerAngle.CODEC, this.leftLegRotation);
		}

		if (!DEFAULT_RIGHT_LEG_ROTATION.equals(this.rightLegRotation)) {
			nbtCompound.put("RightLeg", EulerAngle.CODEC, this.rightLegRotation);
		}

		return nbtCompound;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void pushAway(Entity entity) {
	}

	@Override
	protected void tickCramming() {
		for (Entity entity : this.getWorld().getOtherEntities(this, this.getBoundingBox(), RIDEABLE_MINECART_PREDICATE)) {
			if (this.squaredDistanceTo(entity) <= 0.2) {
				entity.pushAwayFrom(this);
			}
		}
	}

	@Override
	public ActionResult interactAt(PlayerEntity player, Vec3d hitPos, Hand hand) {
		ItemStack itemStack = player.getStackInHand(hand);
		if (this.isMarker() || itemStack.isOf(Items.NAME_TAG)) {
			return ActionResult.PASS;
		} else if (player.isSpectator()) {
			return ActionResult.SUCCESS;
		} else if (player.getWorld().isClient) {
			return ActionResult.SUCCESS_SERVER;
		} else {
			EquipmentSlot equipmentSlot = this.getPreferredEquipmentSlot(itemStack);
			if (itemStack.isEmpty()) {
				EquipmentSlot equipmentSlot2 = this.getSlotFromPosition(hitPos);
				EquipmentSlot equipmentSlot3 = this.isSlotDisabled(equipmentSlot2) ? equipmentSlot : equipmentSlot2;
				if (this.hasStackEquipped(equipmentSlot3) && this.equip(player, equipmentSlot3, itemStack, hand)) {
					return ActionResult.SUCCESS_SERVER;
				}
			} else {
				if (this.isSlotDisabled(equipmentSlot)) {
					return ActionResult.FAIL;
				}

				if (equipmentSlot.getType() == EquipmentSlot.Type.HAND && !this.shouldShowArms()) {
					return ActionResult.FAIL;
				}

				if (this.equip(player, equipmentSlot, itemStack, hand)) {
					return ActionResult.SUCCESS_SERVER;
				}
			}

			return ActionResult.PASS;
		}
	}

	private EquipmentSlot getSlotFromPosition(Vec3d hitPos) {
		EquipmentSlot equipmentSlot = EquipmentSlot.MAINHAND;
		boolean bl = this.isSmall();
		double d = hitPos.y / (this.getScale() * this.getScaleFactor());
		EquipmentSlot equipmentSlot2 = EquipmentSlot.FEET;
		if (d >= 0.1 && d < 0.1 + (bl ? 0.8 : 0.45) && this.hasStackEquipped(equipmentSlot2)) {
			equipmentSlot = EquipmentSlot.FEET;
		} else if (d >= 0.9 + (bl ? 0.3 : 0.0) && d < 0.9 + (bl ? 1.0 : 0.7) && this.hasStackEquipped(EquipmentSlot.CHEST)) {
			equipmentSlot = EquipmentSlot.CHEST;
		} else if (d >= 0.4 && d < 0.4 + (bl ? 1.0 : 0.8) && this.hasStackEquipped(EquipmentSlot.LEGS)) {
			equipmentSlot = EquipmentSlot.LEGS;
		} else if (d >= 1.6 && this.hasStackEquipped(EquipmentSlot.HEAD)) {
			equipmentSlot = EquipmentSlot.HEAD;
		} else if (!this.hasStackEquipped(EquipmentSlot.MAINHAND) && this.hasStackEquipped(EquipmentSlot.OFFHAND)) {
			equipmentSlot = EquipmentSlot.OFFHAND;
		}

		return equipmentSlot;
	}

	private boolean isSlotDisabled(EquipmentSlot slot) {
		return (this.disabledSlots & 1 << slot.getOffsetIndex(0)) != 0 || slot.getType() == EquipmentSlot.Type.HAND && !this.shouldShowArms();
	}

	private boolean equip(PlayerEntity player, EquipmentSlot slot, ItemStack stack, Hand hand) {
		ItemStack itemStack = this.getEquippedStack(slot);
		if (!itemStack.isEmpty() && (this.disabledSlots & 1 << slot.getOffsetIndex(8)) != 0) {
			return false;
		} else if (itemStack.isEmpty() && (this.disabledSlots & 1 << slot.getOffsetIndex(16)) != 0) {
			return false;
		} else if (player.isInCreativeMode() && itemStack.isEmpty() && !stack.isEmpty()) {
			this.equipStack(slot, stack.copyWithCount(1));
			return true;
		} else if (stack.isEmpty() || stack.getCount() <= 1) {
			this.equipStack(slot, stack);
			player.setStackInHand(hand, itemStack);
			return true;
		} else if (!itemStack.isEmpty()) {
			return false;
		} else {
			this.equipStack(slot, stack.split(1));
			return true;
		}
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (this.isRemoved()) {
			return false;
		} else if (!world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING) && source.getAttacker() instanceof MobEntity) {
			return false;
		} else if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			this.kill(world);
			return false;
		} else if (this.isInvulnerableTo(world, source) || this.invisible || this.isMarker()) {
			return false;
		} else if (source.isIn(DamageTypeTags.IS_EXPLOSION)) {
			this.onBreak(world, source);
			this.kill(world);
			return false;
		} else if (source.isIn(DamageTypeTags.IGNITES_ARMOR_STANDS)) {
			if (this.isOnFire()) {
				this.updateHealth(world, source, 0.15F);
			} else {
				this.setOnFireFor(5.0F);
			}

			return false;
		} else if (source.isIn(DamageTypeTags.BURNS_ARMOR_STANDS) && this.getHealth() > 0.5F) {
			this.updateHealth(world, source, 4.0F);
			return false;
		} else {
			boolean bl = source.isIn(DamageTypeTags.CAN_BREAK_ARMOR_STAND);
			boolean bl2 = source.isIn(DamageTypeTags.ALWAYS_KILLS_ARMOR_STANDS);
			if (!bl && !bl2) {
				return false;
			} else if (source.getAttacker() instanceof PlayerEntity playerEntity && !playerEntity.getAbilities().allowModifyWorld) {
				return false;
			} else if (source.isSourceCreativePlayer()) {
				this.playBreakSound();
				this.spawnBreakParticles();
				this.kill(world);
				return true;
			} else {
				long l = world.getTime();
				if (l - this.lastHitTime > 5L && !bl2) {
					world.sendEntityStatus(this, EntityStatuses.HIT_ARMOR_STAND);
					this.emitGameEvent(GameEvent.ENTITY_DAMAGE, source.getAttacker());
					this.lastHitTime = l;
				} else {
					this.breakAndDropItem(world, source);
					this.spawnBreakParticles();
					this.kill(world);
				}

				return true;
			}
		}
	}

	@Override
	public void handleStatus(byte status) {
		if (status == EntityStatuses.HIT_ARMOR_STAND) {
			if (this.getWorld().isClient) {
				this.getWorld().playSoundClient(this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_ARMOR_STAND_HIT, this.getSoundCategory(), 0.3F, 1.0F, false);
				this.lastHitTime = this.getWorld().getTime();
			}
		} else {
			super.handleStatus(status);
		}
	}

	@Override
	public boolean shouldRender(double distance) {
		double d = this.getBoundingBox().getAverageSideLength() * 4.0;
		if (Double.isNaN(d) || d == 0.0) {
			d = 4.0;
		}

		d *= 64.0;
		return distance < d * d;
	}

	private void spawnBreakParticles() {
		if (this.getWorld() instanceof ServerWorld) {
			((ServerWorld)this.getWorld())
				.spawnParticles(
					new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.OAK_PLANKS.getDefaultState()),
					this.getX(),
					this.getBodyY(0.6666666666666666),
					this.getZ(),
					10,
					this.getWidth() / 4.0F,
					this.getHeight() / 4.0F,
					this.getWidth() / 4.0F,
					0.05
				);
		}
	}

	private void updateHealth(ServerWorld world, DamageSource damageSource, float amount) {
		float f = this.getHealth();
		f -= amount;
		if (f <= 0.5F) {
			this.onBreak(world, damageSource);
			this.kill(world);
		} else {
			this.setHealth(f);
			this.emitGameEvent(GameEvent.ENTITY_DAMAGE, damageSource.getAttacker());
		}
	}

	private void breakAndDropItem(ServerWorld world, DamageSource damageSource) {
		ItemStack itemStack = new ItemStack(Items.ARMOR_STAND);
		itemStack.set(DataComponentTypes.CUSTOM_NAME, this.getCustomName());
		Block.dropStack(this.getWorld(), this.getBlockPos(), itemStack);
		this.onBreak(world, damageSource);
	}

	private void onBreak(ServerWorld world, DamageSource damageSource) {
		this.playBreakSound();
		this.drop(world, damageSource);

		for (EquipmentSlot equipmentSlot : EquipmentSlot.VALUES) {
			ItemStack itemStack = this.equipment.put(equipmentSlot, ItemStack.EMPTY);
			if (!itemStack.isEmpty()) {
				Block.dropStack(this.getWorld(), this.getBlockPos().up(), itemStack);
			}
		}
	}

	private void playBreakSound() {
		this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ENTITY_ARMOR_STAND_BREAK, this.getSoundCategory(), 1.0F, 1.0F);
	}

	@Override
	protected void turnHead(float bodyRotation) {
		this.lastBodyYaw = this.lastYaw;
		this.bodyYaw = this.getYaw();
	}

	@Override
	public void travel(Vec3d movementInput) {
		if (this.canClip()) {
			super.travel(movementInput);
		}
	}

	@Override
	public void setBodyYaw(float bodyYaw) {
		this.lastBodyYaw = this.lastYaw = bodyYaw;
		this.lastHeadYaw = this.headYaw = bodyYaw;
	}

	@Override
	public void setHeadYaw(float headYaw) {
		this.lastBodyYaw = this.lastYaw = headYaw;
		this.lastHeadYaw = this.headYaw = headYaw;
	}

	@Override
	public void tick() {
		super.tick();
		EulerAngle eulerAngle = this.dataTracker.get(TRACKER_HEAD_ROTATION);
		if (!this.headRotation.equals(eulerAngle)) {
			this.setHeadRotation(eulerAngle);
		}

		EulerAngle eulerAngle2 = this.dataTracker.get(TRACKER_BODY_ROTATION);
		if (!this.bodyRotation.equals(eulerAngle2)) {
			this.setBodyRotation(eulerAngle2);
		}

		EulerAngle eulerAngle3 = this.dataTracker.get(TRACKER_LEFT_ARM_ROTATION);
		if (!this.leftArmRotation.equals(eulerAngle3)) {
			this.setLeftArmRotation(eulerAngle3);
		}

		EulerAngle eulerAngle4 = this.dataTracker.get(TRACKER_RIGHT_ARM_ROTATION);
		if (!this.rightArmRotation.equals(eulerAngle4)) {
			this.setRightArmRotation(eulerAngle4);
		}

		EulerAngle eulerAngle5 = this.dataTracker.get(TRACKER_LEFT_LEG_ROTATION);
		if (!this.leftLegRotation.equals(eulerAngle5)) {
			this.setLeftLegRotation(eulerAngle5);
		}

		EulerAngle eulerAngle6 = this.dataTracker.get(TRACKER_RIGHT_LEG_ROTATION);
		if (!this.rightLegRotation.equals(eulerAngle6)) {
			this.setRightLegRotation(eulerAngle6);
		}
	}

	@Override
	protected void updatePotionVisibility() {
		this.setInvisible(this.invisible);
	}

	@Override
	public void setInvisible(boolean invisible) {
		this.invisible = invisible;
		super.setInvisible(invisible);
	}

	@Override
	public boolean isBaby() {
		return this.isSmall();
	}

	@Override
	public void kill(ServerWorld world) {
		this.remove(Entity.RemovalReason.KILLED);
		this.emitGameEvent(GameEvent.ENTITY_DIE);
	}

	@Override
	public boolean isImmuneToExplosion(Explosion explosion) {
		return explosion.preservesDecorativeEntities() ? this.isInvisible() : true;
	}

	@Override
	public PistonBehavior getPistonBehavior() {
		return this.isMarker() ? PistonBehavior.IGNORE : super.getPistonBehavior();
	}

	@Override
	public boolean canAvoidTraps() {
		return this.isMarker();
	}

	private void setSmall(boolean small) {
		this.dataTracker.set(ARMOR_STAND_FLAGS, this.setBitField(this.dataTracker.get(ARMOR_STAND_FLAGS), SMALL_FLAG, small));
	}

	public boolean isSmall() {
		return (this.dataTracker.get(ARMOR_STAND_FLAGS) & 1) != 0;
	}

	public void setShowArms(boolean showArms) {
		this.dataTracker.set(ARMOR_STAND_FLAGS, this.setBitField(this.dataTracker.get(ARMOR_STAND_FLAGS), SHOW_ARMS_FLAG, showArms));
	}

	public boolean shouldShowArms() {
		return (this.dataTracker.get(ARMOR_STAND_FLAGS) & 4) != 0;
	}

	public void setHideBasePlate(boolean hideBasePlate) {
		this.dataTracker.set(ARMOR_STAND_FLAGS, this.setBitField(this.dataTracker.get(ARMOR_STAND_FLAGS), HIDE_BASE_PLATE_FLAG, hideBasePlate));
	}

	public boolean shouldShowBasePlate() {
		return (this.dataTracker.get(ARMOR_STAND_FLAGS) & 8) == 0;
	}

	private void setMarker(boolean marker) {
		this.dataTracker.set(ARMOR_STAND_FLAGS, this.setBitField(this.dataTracker.get(ARMOR_STAND_FLAGS), MARKER_FLAG, marker));
	}

	public boolean isMarker() {
		return (this.dataTracker.get(ARMOR_STAND_FLAGS) & 16) != 0;
	}

	private byte setBitField(byte value, int bitField, boolean set) {
		if (set) {
			value = (byte)(value | bitField);
		} else {
			value = (byte)(value & ~bitField);
		}

		return value;
	}

	public void setHeadRotation(EulerAngle angle) {
		this.headRotation = angle;
		this.dataTracker.set(TRACKER_HEAD_ROTATION, angle);
	}

	public void setBodyRotation(EulerAngle angle) {
		this.bodyRotation = angle;
		this.dataTracker.set(TRACKER_BODY_ROTATION, angle);
	}

	public void setLeftArmRotation(EulerAngle angle) {
		this.leftArmRotation = angle;
		this.dataTracker.set(TRACKER_LEFT_ARM_ROTATION, angle);
	}

	public void setRightArmRotation(EulerAngle angle) {
		this.rightArmRotation = angle;
		this.dataTracker.set(TRACKER_RIGHT_ARM_ROTATION, angle);
	}

	public void setLeftLegRotation(EulerAngle angle) {
		this.leftLegRotation = angle;
		this.dataTracker.set(TRACKER_LEFT_LEG_ROTATION, angle);
	}

	public void setRightLegRotation(EulerAngle angle) {
		this.rightLegRotation = angle;
		this.dataTracker.set(TRACKER_RIGHT_LEG_ROTATION, angle);
	}

	public EulerAngle getHeadRotation() {
		return this.headRotation;
	}

	public EulerAngle getBodyRotation() {
		return this.bodyRotation;
	}

	public EulerAngle getLeftArmRotation() {
		return this.leftArmRotation;
	}

	public EulerAngle getRightArmRotation() {
		return this.rightArmRotation;
	}

	public EulerAngle getLeftLegRotation() {
		return this.leftLegRotation;
	}

	public EulerAngle getRightLegRotation() {
		return this.rightLegRotation;
	}

	@Override
	public boolean canHit() {
		return super.canHit() && !this.isMarker();
	}

	@Override
	public boolean handleAttack(Entity attacker) {
		return attacker instanceof PlayerEntity playerEntity && !this.getWorld().canEntityModifyAt(playerEntity, this.getBlockPos());
	}

	@Override
	public Arm getMainArm() {
		return Arm.RIGHT;
	}

	@Override
	public LivingEntity.FallSounds getFallSounds() {
		return new LivingEntity.FallSounds(SoundEvents.ENTITY_ARMOR_STAND_FALL, SoundEvents.ENTITY_ARMOR_STAND_FALL);
	}

	@Nullable
	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_ARMOR_STAND_HIT;
	}

	@Nullable
	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_ARMOR_STAND_BREAK;
	}

	@Override
	public void onStruckByLightning(ServerWorld world, LightningEntity lightning) {
	}

	@Override
	public boolean isAffectedBySplashPotions() {
		return false;
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		if (ARMOR_STAND_FLAGS.equals(data)) {
			this.calculateDimensions();
			this.intersectionChecked = !this.isMarker();
		}

		super.onTrackedDataSet(data);
	}

	@Override
	public boolean isMobOrPlayer() {
		return false;
	}

	@Override
	public EntityDimensions getBaseDimensions(EntityPose pose) {
		return this.getDimensions(this.isMarker());
	}

	private EntityDimensions getDimensions(boolean marker) {
		if (marker) {
			return MARKER_DIMENSIONS;
		} else {
			return this.isBaby() ? SMALL_DIMENSIONS : this.getType().getDimensions();
		}
	}

	@Override
	public Vec3d getClientCameraPosVec(float tickProgress) {
		if (this.isMarker()) {
			Box box = this.getDimensions(false).getBoxAt(this.getPos());
			BlockPos blockPos = this.getBlockPos();
			int i = Integer.MIN_VALUE;

			for (BlockPos blockPos2 : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ), BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
				int j = Math.max(this.getWorld().getLightLevel(LightType.BLOCK, blockPos2), this.getWorld().getLightLevel(LightType.SKY, blockPos2));
				if (j == 15) {
					return Vec3d.ofCenter(blockPos2);
				}

				if (j > i) {
					i = j;
					blockPos = blockPos2.toImmutable();
				}
			}

			return Vec3d.ofCenter(blockPos);
		} else {
			return super.getClientCameraPosVec(tickProgress);
		}
	}

	@Override
	public ItemStack getPickBlockStack() {
		return new ItemStack(Items.ARMOR_STAND);
	}

	@Override
	public boolean isPartOfGame() {
		return !this.isInvisible() && !this.isMarker();
	}
}
