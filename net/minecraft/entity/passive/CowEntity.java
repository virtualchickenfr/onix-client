package net.minecraft.entity.passive;

import net.minecraft.component.ComponentType;
import net.minecraft.component.ComponentsAccess;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Variants;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.spawn.SpawnContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class CowEntity extends AbstractCowEntity {
	private static final TrackedData<RegistryEntry<CowVariant>> VARIANT = DataTracker.registerData(CowEntity.class, TrackedDataHandlerRegistry.COW_VARIANT);

	public CowEntity(EntityType<? extends CowEntity> entityType, World world) {
		super(entityType, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(VARIANT, Variants.getOrDefaultOrThrow(this.getRegistryManager(), CowVariants.TEMPERATE));
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		Variants.writeVariantToNbt(nbt, this.getVariant());
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		Variants.readVariantFromNbt(nbt, this.getRegistryManager(), RegistryKeys.COW_VARIANT).ifPresent(this::setVariant);
	}

	@Nullable
	public CowEntity createChild(ServerWorld serverWorld, PassiveEntity passiveEntity) {
		CowEntity cowEntity = EntityType.COW.create(serverWorld, SpawnReason.BREEDING);
		if (cowEntity != null && passiveEntity instanceof CowEntity cowEntity2) {
			cowEntity.setVariant(this.random.nextBoolean() ? this.getVariant() : cowEntity2.getVariant());
		}

		return cowEntity;
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason spawnReason, @Nullable EntityData entityData) {
		CowVariants.select(this.random, this.getRegistryManager(), SpawnContext.of(world, this.getBlockPos())).ifPresent(this::setVariant);
		return super.initialize(world, difficulty, spawnReason, entityData);
	}

	public void setVariant(RegistryEntry<CowVariant> variant) {
		this.dataTracker.set(VARIANT, variant);
	}

	public RegistryEntry<CowVariant> getVariant() {
		return this.dataTracker.get(VARIANT);
	}

	@Nullable
	@Override
	public <T> T get(ComponentType<? extends T> type) {
		return type == DataComponentTypes.COW_VARIANT ? castComponentValue((ComponentType<T>)type, this.getVariant()) : super.get(type);
	}

	@Override
	protected void copyComponentsFrom(ComponentsAccess from) {
		this.copyComponentFrom(from, DataComponentTypes.COW_VARIANT);
		super.copyComponentsFrom(from);
	}

	@Override
	protected <T> boolean setApplicableComponent(ComponentType<T> type, T value) {
		if (type == DataComponentTypes.COW_VARIANT) {
			this.setVariant(castComponentValue(DataComponentTypes.COW_VARIANT, value));
			return true;
		} else {
			return super.setApplicableComponent(type, value);
		}
	}
}
