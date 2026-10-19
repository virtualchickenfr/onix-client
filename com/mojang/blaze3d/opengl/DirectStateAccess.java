package com.mojang.blaze3d.opengl;

import java.nio.ByteBuffer;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLCapabilities;

@Environment(EnvType.CLIENT)
public abstract class DirectStateAccess {
	public static DirectStateAccess create(GLCapabilities gLCapabilities, Set<String> set) {
		if (gLCapabilities.GL_ARB_direct_state_access && GlDevice.USE_GL_ARB_direct_state_access) {
			set.add("GL_ARB_direct_state_access");
			return new DirectStateAccess.Core();
		} else {
			return new DirectStateAccess.Emulated();
		}
	}

	abstract int createBuffer();

	abstract void bufferData(int i, long l, int j);

	abstract void bufferData(int i, ByteBuffer byteBuffer, int j);

	abstract void bufferSubData(int i, int j, ByteBuffer byteBuffer);

	abstract void bufferStorage(int i, long l, int j);

	abstract void bufferStorage(int i, ByteBuffer byteBuffer, int j);

	@Nullable
	abstract ByteBuffer mapBufferRange(int i, int j, int k, int l);

	abstract void unmapBuffer(int i);

	abstract int createFrameBufferObject();

	abstract void bindFrameBufferTextures(int i, int j, int k, int l, int m);

	abstract void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t);

	abstract void flushMappedBufferRange(int i, int j, int k);

	@Environment(EnvType.CLIENT)
	static class Core extends DirectStateAccess {
		@Override
		int createBuffer() {
			return ARBDirectStateAccess.glCreateBuffers();
		}

		@Override
		void bufferData(int i, long l, int j) {
			ARBDirectStateAccess.glNamedBufferData(i, l, j);
		}

		@Override
		void bufferData(int i, ByteBuffer byteBuffer, int j) {
			ARBDirectStateAccess.glNamedBufferData(i, byteBuffer, j);
		}

		@Override
		void bufferSubData(int i, int j, ByteBuffer byteBuffer) {
			ARBDirectStateAccess.glNamedBufferSubData(i, j, byteBuffer);
		}

		@Override
		void bufferStorage(int i, long l, int j) {
			ARBDirectStateAccess.glNamedBufferStorage(i, l, j);
		}

		@Override
		void bufferStorage(int i, ByteBuffer byteBuffer, int j) {
			ARBDirectStateAccess.glNamedBufferStorage(i, byteBuffer, j);
		}

		@Nullable
		@Override
		ByteBuffer mapBufferRange(int i, int j, int k, int l) {
			return ARBDirectStateAccess.glMapNamedBufferRange(i, j, k, l);
		}

		@Override
		void unmapBuffer(int i) {
			ARBDirectStateAccess.glUnmapNamedBuffer(i);
		}

		@Override
		public int createFrameBufferObject() {
			return ARBDirectStateAccess.glCreateFramebuffers();
		}

		@Override
		public void bindFrameBufferTextures(int i, int j, int k, int l, int m) {
			ARBDirectStateAccess.glNamedFramebufferTexture(i, 36064, j, l);
			ARBDirectStateAccess.glNamedFramebufferTexture(i, 36096, k, l);
			if (m != 0) {
				GlStateManager._glBindFramebuffer(m, i);
			}
		}

		@Override
		public void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t) {
			ARBDirectStateAccess.glBlitNamedFramebuffer(i, j, k, l, m, n, o, p, q, r, s, t);
		}

		@Override
		void flushMappedBufferRange(int i, int j, int k) {
			ARBDirectStateAccess.glFlushMappedNamedBufferRange(i, j, k);
		}
	}

	@Environment(EnvType.CLIENT)
	static class Emulated extends DirectStateAccess {
		@Override
		int createBuffer() {
			return GlStateManager._glGenBuffers();
		}

		@Override
		void bufferData(int i, long l, int j) {
			GlStateManager._glBindBuffer(36663, i);
			GlStateManager._glBufferData(36663, l, GlConst.bufferUsageToGlEnum(j));
			GlStateManager._glBindBuffer(36663, 0);
		}

		@Override
		void bufferData(int i, ByteBuffer byteBuffer, int j) {
			GlStateManager._glBindBuffer(36663, i);
			GlStateManager._glBufferData(36663, byteBuffer, GlConst.bufferUsageToGlEnum(j));
			GlStateManager._glBindBuffer(36663, 0);
		}

		@Override
		void bufferSubData(int i, int j, ByteBuffer byteBuffer) {
			GlStateManager._glBindBuffer(36663, i);
			GlStateManager._glBufferSubData(36663, j, byteBuffer);
			GlStateManager._glBindBuffer(36663, 0);
		}

		@Override
		void bufferStorage(int i, long l, int j) {
			GlStateManager._glBindBuffer(36663, i);
			ARBBufferStorage.glBufferStorage(36663, l, j);
			GlStateManager._glBindBuffer(36663, 0);
		}

		@Override
		void bufferStorage(int i, ByteBuffer byteBuffer, int j) {
			GlStateManager._glBindBuffer(36663, i);
			ARBBufferStorage.glBufferStorage(36663, byteBuffer, j);
			GlStateManager._glBindBuffer(36663, 0);
		}

		@Nullable
		@Override
		ByteBuffer mapBufferRange(int i, int j, int k, int l) {
			GlStateManager._glBindBuffer(36663, i);
			ByteBuffer byteBuffer = GlStateManager._glMapBufferRange(36663, j, k, l);
			GlStateManager._glBindBuffer(36663, 0);
			return byteBuffer;
		}

		@Override
		void unmapBuffer(int i) {
			GlStateManager._glBindBuffer(36663, i);
			GlStateManager._glUnmapBuffer(36663);
			GlStateManager._glBindBuffer(36663, 0);
		}

		@Override
		void flushMappedBufferRange(int i, int j, int k) {
			GlStateManager._glBindBuffer(36663, i);
			GL30.glFlushMappedBufferRange(36663, j, k);
			GlStateManager._glBindBuffer(36663, 0);
		}

		@Override
		public int createFrameBufferObject() {
			return GlStateManager.glGenFramebuffers();
		}

		@Override
		public void bindFrameBufferTextures(int i, int j, int k, int l, int m) {
			int n = m == 0 ? 36009 : m;
			int o = GlStateManager.getFrameBuffer(n);
			GlStateManager._glBindFramebuffer(n, i);
			GlStateManager._glFramebufferTexture2D(n, 36064, 3553, j, l);
			GlStateManager._glFramebufferTexture2D(n, 36096, 3553, k, l);
			if (m == 0) {
				GlStateManager._glBindFramebuffer(n, o);
			}
		}

		@Override
		public void blitFrameBuffers(int i, int j, int k, int l, int m, int n, int o, int p, int q, int r, int s, int t) {
			int u = GlStateManager.getFrameBuffer(36008);
			int v = GlStateManager.getFrameBuffer(36009);
			GlStateManager._glBindFramebuffer(36008, i);
			GlStateManager._glBindFramebuffer(36009, j);
			GlStateManager._glBlitFrameBuffer(k, l, m, n, o, p, q, r, s, t);
			GlStateManager._glBindFramebuffer(36008, u);
			GlStateManager._glBindFramebuffer(36009, v);
		}
	}
}
