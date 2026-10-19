package net.minecraft.world.item.component;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import org.jetbrains.annotations.Nullable;

public record ResolvableProfile(Optional<String> name, Optional<UUID> id, PropertyMap properties, GameProfile gameProfile) {
	private static final Codec<ResolvableProfile> FULL_CODEC = RecordCodecBuilder.create(
		instance -> instance.group(
				ExtraCodecs.PLAYER_NAME.optionalFieldOf("name").forGetter(ResolvableProfile::name),
				UUIDUtil.CODEC.optionalFieldOf("id").forGetter(ResolvableProfile::id),
				ExtraCodecs.PROPERTY_MAP.optionalFieldOf("properties", new PropertyMap()).forGetter(ResolvableProfile::properties)
			)
			.apply(instance, ResolvableProfile::new)
	);
	public static final Codec<ResolvableProfile> CODEC = Codec.withAlternative(
		FULL_CODEC, ExtraCodecs.PLAYER_NAME, string -> new ResolvableProfile(Optional.of(string), Optional.empty(), new PropertyMap())
	);
	public static final StreamCodec<ByteBuf, ResolvableProfile> STREAM_CODEC = StreamCodec.composite(
		ByteBufCodecs.stringUtf8(16).apply(ByteBufCodecs::optional),
		ResolvableProfile::name,
		UUIDUtil.STREAM_CODEC.apply(ByteBufCodecs::optional),
		ResolvableProfile::id,
		ByteBufCodecs.GAME_PROFILE_PROPERTIES,
		ResolvableProfile::properties,
		ResolvableProfile::new
	);

	public ResolvableProfile(Optional<String> optional, Optional<UUID> optional2, PropertyMap propertyMap) {
		this(optional, optional2, propertyMap, createGameProfile(optional2, optional, propertyMap));
	}

	public ResolvableProfile(GameProfile gameProfile) {
		this(Optional.of(gameProfile.getName()), Optional.of(gameProfile.getId()), gameProfile.getProperties(), gameProfile);
	}

	@Nullable
	public ResolvableProfile pollResolve() {
		if (this.isResolved()) {
			return this;
		}

		Optional<GameProfile> optional;
		if (this.id.isPresent()) {
			optional = SkullBlockEntity.fetchGameProfile(this.id.get()).getNow(null);
		} else {
			optional = SkullBlockEntity.fetchGameProfile(this.name.orElseThrow()).getNow(null);
		}

		return optional != null ? this.createProfile(optional) : null;
	}

	public CompletableFuture<ResolvableProfile> resolve() {
		if (this.isResolved()) {
			return CompletableFuture.completedFuture(this);
		} else {
			return this.id.isPresent()
				? SkullBlockEntity.fetchGameProfile(this.id.get()).thenApply(this::createProfile)
				: SkullBlockEntity.fetchGameProfile(this.name.orElseThrow()).thenApply(this::createProfile);
		}
	}

	private ResolvableProfile createProfile(Optional<GameProfile> optional) {
		return new ResolvableProfile(optional.orElseGet(() -> createGameProfile(this.id, this.name)));
	}

	private static GameProfile createGameProfile(Optional<UUID> optional, Optional<String> optional2) {
		return new GameProfile(optional.orElse(Util.NIL_UUID), optional2.orElse(""));
	}

	private static GameProfile createGameProfile(Optional<UUID> optional, Optional<String> optional2, PropertyMap propertyMap) {
		GameProfile gameProfile = createGameProfile(optional, optional2);
		gameProfile.getProperties().putAll(propertyMap);
		return gameProfile;
	}

	public boolean isResolved() {
		return !this.properties.isEmpty() ? true : this.id.isPresent() == this.name.isPresent();
	}
}
