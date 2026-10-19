package net.minecraft.network.handler;

import io.netty.handler.codec.DecoderException;

public class PacketDecoderException extends DecoderException implements PacketException, PacketCodecDispatcher.UndecoratedException {
	public PacketDecoderException(String message) {
		super(message);
	}

	public PacketDecoderException(Throwable cause) {
		super(cause);
	}
}
