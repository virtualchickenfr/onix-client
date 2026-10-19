package com.mojang.blaze3d.buffers;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.annotation.DeobfuscateClass;

@Environment(EnvType.CLIENT)
@DeobfuscateClass
public class GpuFence implements AutoCloseable {
	private long handle = GlStateManager._glFenceSync(GlConst.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);

	public void close() {
		if (this.handle != 0L) {
			GlStateManager._glDeleteSync(this.handle);
			this.handle = 0L;
		}
	}

	public boolean awaitCompletion(long timeoutNanos) {
		if (this.handle == 0L) {
			return true;
		} else {
			int i = GlStateManager._glClientWaitSync(this.handle, 0, timeoutNanos);
			if (i == GlConst.GL_TIMEOUT_EXPIRED) {
				return false;
			} else if (i == GlConst.GL_WAIT_FAILED) {
				throw new IllegalStateException("Failed to complete gpu fence");
			} else {
				return true;
			}
		}
	}
}
