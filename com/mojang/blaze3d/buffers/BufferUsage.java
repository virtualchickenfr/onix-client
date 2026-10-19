package com.mojang.blaze3d.buffers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.annotation.DeobfuscateClass;

@Environment(EnvType.CLIENT)
@DeobfuscateClass
public enum BufferUsage {
	DYNAMIC_WRITE(false, true),
	STATIC_WRITE(false, true),
	STREAM_WRITE(false, true),
	STATIC_READ(true, false),
	DYNAMIC_READ(true, false),
	STREAM_READ(true, false),
	DYNAMIC_COPY(false, false),
	STATIC_COPY(false, false),
	STREAM_COPY(false, false);

	final boolean readable;
	final boolean writable;

	private BufferUsage(final boolean readable, final boolean writable) {
		this.readable = readable;
		this.writable = writable;
	}

	public boolean isReadable() {
		return this.readable;
	}

	public boolean isWritable() {
		return this.writable;
	}
}
