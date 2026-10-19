package net.minecraft.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.netty.buffer.ByteBuf;
import java.util.function.UnaryOperator;
import net.minecraft.network.codec.PacketCodec;

public record AssetInfo(Identifier id, Identifier texturePath) {
	public static final Codec<AssetInfo> CODEC = Identifier.CODEC.xmap(AssetInfo::new, AssetInfo::id);
	public static final MapCodec<AssetInfo> MAP_CODEC = CODEC.fieldOf("asset_id");
	public static final PacketCodec<ByteBuf, AssetInfo> PACKET_CODEC = PacketCodec.tuple(Identifier.PACKET_CODEC, AssetInfo::id, AssetInfo::new);

	public AssetInfo(Identifier id) {
		this(id, id.withPath((UnaryOperator<String>)(path -> "textures/" + path + ".png")));
	}
}
