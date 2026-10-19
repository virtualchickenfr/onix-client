package net.minecraft.entity;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.server.ServerConfigHandler;
import net.minecraft.util.Uuids;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityQueriable;
import net.minecraft.world.entity.UniquelyIdentifiable;
import org.jetbrains.annotations.Nullable;

public class LazyEntityReference<StoredEntityType extends UniquelyIdentifiable> {
	private static final Codec<? extends LazyEntityReference<?>> CODEC = Uuids.INT_STREAM_CODEC.xmap(LazyEntityReference::new, LazyEntityReference::getUuid);
	private static final PacketCodec<ByteBuf, ? extends LazyEntityReference<?>> PACKET_CODEC = Uuids.PACKET_CODEC
		.xmap(LazyEntityReference::new, LazyEntityReference::getUuid);
	private Either<UUID, StoredEntityType> value;

	public static <Type extends UniquelyIdentifiable> Codec<LazyEntityReference<Type>> createCodec() {
		return (Codec<LazyEntityReference<Type>>)CODEC;
	}

	public static <Type extends UniquelyIdentifiable> PacketCodec<ByteBuf, LazyEntityReference<Type>> createPacketCodec() {
		return (PacketCodec<ByteBuf, LazyEntityReference<Type>>)PACKET_CODEC;
	}

	public LazyEntityReference(StoredEntityType value) {
		this.value = Either.right(value);
	}

	public LazyEntityReference(UUID value) {
		this.value = Either.left(value);
	}

	public UUID getUuid() {
		return this.value.map(uuid -> uuid, UniquelyIdentifiable::getUuid);
	}

	@Nullable
	public StoredEntityType resolve(EntityQueriable<? super StoredEntityType> world, Class<StoredEntityType> type) {
		Optional<StoredEntityType> optional = this.value.right();
		if (optional.isPresent()) {
			StoredEntityType uniquelyIdentifiable = (StoredEntityType)optional.get();
			if (!uniquelyIdentifiable.isRemoved()) {
				return uniquelyIdentifiable;
			}

			this.value = Either.left(uniquelyIdentifiable.getUuid());
		}

		Optional<UUID> optional2 = this.value.left();
		if (optional2.isPresent()) {
			StoredEntityType uniquelyIdentifiable2 = this.cast(world.getEntity((UUID)optional2.get()), type);
			if (uniquelyIdentifiable2 != null && !uniquelyIdentifiable2.isRemoved()) {
				this.value = Either.right(uniquelyIdentifiable2);
				return uniquelyIdentifiable2;
			}
		}

		return null;
	}

	@Nullable
	private StoredEntityType cast(@Nullable UniquelyIdentifiable entity, Class<StoredEntityType> clazz) {
		return (StoredEntityType)(entity != null && clazz.isAssignableFrom(entity.getClass()) ? clazz.cast(entity) : null);
	}

	public boolean uuidEquals(StoredEntityType o) {
		return this.getUuid().equals(o.getUuid());
	}

	public void writeNbt(NbtCompound nbt, String key) {
		nbt.put(key, Uuids.INT_STREAM_CODEC, this.getUuid());
	}

	@Nullable
	public static <StoredEntityType extends UniquelyIdentifiable> StoredEntityType resolve(
		@Nullable LazyEntityReference<StoredEntityType> entity, EntityQueriable<? super StoredEntityType> world, Class<StoredEntityType> type
	) {
		return entity != null ? entity.resolve(world, type) : null;
	}

	@Nullable
	public static <StoredEntityType extends UniquelyIdentifiable> LazyEntityReference<StoredEntityType> fromNbt(NbtCompound nbt, String key) {
		return (LazyEntityReference<StoredEntityType>)nbt.get(key, createCodec()).orElse(null);
	}

	@Nullable
	public static <StoredEntityType extends UniquelyIdentifiable> LazyEntityReference<StoredEntityType> fromNbtOrPlayerName(
		NbtCompound nbt, String key, World world
	) {
		Optional<UUID> optional = nbt.get(key, Uuids.INT_STREAM_CODEC);
		return optional.isPresent()
			? new LazyEntityReference<>((UUID)optional.get())
			: (LazyEntityReference)nbt.getString(key)
				.map(name -> ServerConfigHandler.getPlayerUuidByName(world.getServer(), name))
				.map(LazyEntityReference::new)
				.orElse(null);
	}
}
