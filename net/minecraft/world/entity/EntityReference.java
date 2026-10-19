package net.minecraft.world.entity;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.UUIDLookup;
import net.minecraft.world.level.entity.UniquelyIdentifyable;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

public final class EntityReference<StoredEntityType extends UniquelyIdentifyable> {
	private static final Codec<? extends EntityReference<?>> CODEC = UUIDUtil.CODEC.xmap(EntityReference::new, EntityReference::getUUID);
	private static final StreamCodec<ByteBuf, ? extends EntityReference<?>> STREAM_CODEC = UUIDUtil.STREAM_CODEC
		.map(EntityReference::new, EntityReference::getUUID);
	private Either<UUID, StoredEntityType> entity;

	public static <Type extends UniquelyIdentifyable> Codec<EntityReference<Type>> codec() {
		return (Codec<EntityReference<Type>>)CODEC;
	}

	public static <Type extends UniquelyIdentifyable> StreamCodec<ByteBuf, EntityReference<Type>> streamCodec() {
		return (StreamCodec<ByteBuf, EntityReference<Type>>)STREAM_CODEC;
	}

	public EntityReference(StoredEntityType uniquelyIdentifyable) {
		this.entity = Either.right(uniquelyIdentifyable);
	}

	public EntityReference(UUID uUID) {
		this.entity = Either.left(uUID);
	}

	public UUID getUUID() {
		return this.entity.map(uUID -> (UUID)uUID, UniquelyIdentifyable::getUUID);
	}

	@Nullable
	public StoredEntityType getEntity(UUIDLookup<? super StoredEntityType> uUIDLookup, Class<StoredEntityType> class_) {
		Optional<StoredEntityType> optional = this.entity.right();
		if (optional.isPresent()) {
			StoredEntityType uniquelyIdentifyable = optional.get();
			if (!uniquelyIdentifyable.isRemoved()) {
				return uniquelyIdentifyable;
			}

			this.entity = Either.left(uniquelyIdentifyable.getUUID());
		}

		Optional<UUID> optional2 = this.entity.left();
		if (optional2.isPresent()) {
			StoredEntityType uniquelyIdentifyable2 = this.resolve(uUIDLookup.getEntity(optional2.get()), class_);
			if (uniquelyIdentifyable2 != null && !uniquelyIdentifyable2.isRemoved()) {
				this.entity = Either.right(uniquelyIdentifyable2);
				return uniquelyIdentifyable2;
			}
		}

		return null;
	}

	@Nullable
	private StoredEntityType resolve(@Nullable UniquelyIdentifyable uniquelyIdentifyable, Class<StoredEntityType> class_) {
		return uniquelyIdentifyable != null && class_.isAssignableFrom(uniquelyIdentifyable.getClass()) ? class_.cast(uniquelyIdentifyable) : null;
	}

	public boolean matches(StoredEntityType uniquelyIdentifyable) {
		return this.getUUID().equals(uniquelyIdentifyable.getUUID());
	}

	public void store(ValueOutput valueOutput, String string) {
		valueOutput.store(string, UUIDUtil.CODEC, this.getUUID());
	}

	public static void store(@Nullable EntityReference<?> entityReference, ValueOutput valueOutput, String string) {
		if (entityReference != null) {
			entityReference.store(valueOutput, string);
		}
	}

	@Nullable
	public static <StoredEntityType extends UniquelyIdentifyable> StoredEntityType get(
		@Nullable EntityReference<StoredEntityType> entityReference, UUIDLookup<? super StoredEntityType> uUIDLookup, Class<StoredEntityType> class_
	) {
		return entityReference != null ? entityReference.getEntity(uUIDLookup, class_) : null;
	}

	@Nullable
	public static <StoredEntityType extends UniquelyIdentifyable> EntityReference<StoredEntityType> read(ValueInput valueInput, String string) {
		return valueInput.read(string, codec()).orElse(null);
	}

	@Nullable
	public static <StoredEntityType extends UniquelyIdentifyable> EntityReference<StoredEntityType> readWithOldOwnerConversion(
		ValueInput valueInput, String string, Level level
	) {
		Optional<UUID> optional = valueInput.read(string, UUIDUtil.CODEC);
		return optional.isPresent()
			? new EntityReference<>(optional.get())
			: valueInput.getString(string)
				.map(stringx -> OldUsersConverter.convertMobOwnerIfNecessary(level.getServer(), stringx))
				.map(EntityReference::new)
				.orElse(null);
	}

	@Override
	public boolean equals(Object object) {
		return object == this ? true : object instanceof EntityReference<?> entityReference && this.getUUID().equals(entityReference.getUUID());
	}

	@Override
	public int hashCode() {
		return this.getUUID().hashCode();
	}
}
