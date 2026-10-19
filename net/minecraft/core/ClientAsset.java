package net.minecraft.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record ClientAsset(ResourceLocation id, ResourceLocation texturePath) {
	public static final Codec<ClientAsset> CODEC = ResourceLocation.CODEC.xmap(ClientAsset::new, ClientAsset::id);
	public static final MapCodec<ClientAsset> DEFAULT_FIELD_CODEC = CODEC.fieldOf("asset_id");
	public static final StreamCodec<ByteBuf, ClientAsset> STREAM_CODEC = StreamCodec.composite(ResourceLocation.STREAM_CODEC, ClientAsset::id, ClientAsset::new);

	public ClientAsset(ResourceLocation resourceLocation) {
		this(resourceLocation, resourceLocation.withPath(string -> "textures/" + string + ".png"));
	}
}
