package net.minecraft.util;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;

public enum Unit {
	INSTANCE;

	public static final Codec<Unit> CODEC = Codec.unit(INSTANCE);
	public static final PacketCodec<ByteBuf, Unit> PACKET_CODEC = PacketCodec.unit(INSTANCE);
}
