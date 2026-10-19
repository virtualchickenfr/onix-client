package net.minecraft.client.gl;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.jtracy.MemoryPool;
import com.mojang.jtracy.TracyClient;
import java.nio.ByteBuffer;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GlGpuBuffer extends GpuBuffer {
	protected static final MemoryPool POOL = TracyClient.createMemoryPool("GPU Buffers");
	protected boolean closed;
	protected boolean hasData = false;
	@Nullable
	protected final Supplier<String> debugLabelSupplier;
	protected final int id;

	protected GlGpuBuffer(DebugLabelManager labeler, @Nullable Supplier<String> debugLabelSupplier, BufferType type, BufferUsage usage, int size, int id) {
		super(type, usage, size);
		this.debugLabelSupplier = debugLabelSupplier;
		this.id = id;
		if (usage.isReadable()) {
			GlStateManager._glBindBuffer(GlConst.toGl(type), id);
			GlStateManager._glBufferData(GlConst.toGl(type), size, GlConst.toGl(usage));
			POOL.malloc(id, size);
			this.hasData = true;
			labeler.labelGlGpuBuffer(this);
		}
	}

	protected void ensureAllocated() {
		if (!this.hasData) {
			GlStateManager._glBindBuffer(GlConst.toGl(this.type()), this.id);
			GlStateManager._glBindBuffer(GlConst.toGl(this.type()), 0);
		}
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			GlStateManager._glDeleteBuffers(this.id);
			if (this.hasData) {
				POOL.free(this.id);
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static class ReadResultImpl implements GpuBuffer.ReadView {
		private final int handle;
		private final ByteBuffer buf;

		protected ReadResultImpl(int handle, ByteBuffer buf) {
			this.handle = handle;
			this.buf = buf;
		}

		@Override
		public ByteBuffer data() {
			return this.buf;
		}

		@Override
		public void close() {
			GlStateManager._glUnmapBuffer(this.handle);
		}
	}
}
