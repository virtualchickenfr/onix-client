package com.mojang.blaze3d.buffers;

import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.annotation.DeobfuscateClass;

@Environment(EnvType.CLIENT)
@DeobfuscateClass
public abstract class GpuBuffer implements AutoCloseable {
	private final BufferType type;
	private final BufferUsage usage;
	public int size;

	public GpuBuffer(BufferType type, BufferUsage usage, int size) {
		this.type = type;
		this.size = size;
		this.usage = usage;
	}

	public int size() {
		return this.size;
	}

	public BufferType type() {
		return this.type;
	}

	public BufferUsage usage() {
		return this.usage;
	}

	public abstract boolean isClosed();

	public abstract void close();

	@Environment(EnvType.CLIENT)
	@DeobfuscateClass
	public interface ReadView extends AutoCloseable {
		ByteBuffer data();

		void close();
	}
}
