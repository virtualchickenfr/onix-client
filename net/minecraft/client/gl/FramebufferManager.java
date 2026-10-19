package net.minecraft.client.gl;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.ARBDirectStateAccess;
import org.lwjgl.opengl.GLCapabilities;

@Environment(EnvType.CLIENT)
public abstract class FramebufferManager {
	public static FramebufferManager createFramebuffer(GLCapabilities capabilities, Set<String> usedCapabilities) {
		if (capabilities.GL_ARB_direct_state_access && GlBackend.allowGlArbDirectAccess) {
			usedCapabilities.add("GL_ARB_direct_state_access");
			return new FramebufferManager.DirectFramebuffer();
		} else {
			return new FramebufferManager.GlFramebuffer();
		}
	}

	abstract int createFramebuffer();

	abstract void setupFramebuffer(int framebuffer, int colorAttachment, int depthAttachment, int mipLevel, int bindTarget);

	abstract void setupBlitFramebuffer(
		int readFramebuffer, int writeFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter
	);

	@Environment(EnvType.CLIENT)
	static class DirectFramebuffer extends FramebufferManager {
		@Override
		public int createFramebuffer() {
			return ARBDirectStateAccess.glCreateFramebuffers();
		}

		@Override
		public void setupFramebuffer(int framebuffer, int colorAttachment, int depthAttachment, int mipLevel, int bindTarget) {
			ARBDirectStateAccess.glNamedFramebufferTexture(framebuffer, GlConst.GL_COLOR_ATTACHMENT0, colorAttachment, mipLevel);
			ARBDirectStateAccess.glNamedFramebufferTexture(framebuffer, GlConst.GL_DEPTH_ATTACHMENT, depthAttachment, mipLevel);
			if (bindTarget != 0) {
				GlStateManager._glBindFramebuffer(bindTarget, framebuffer);
			}
		}

		@Override
		public void setupBlitFramebuffer(
			int readFramebuffer, int writeFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter
		) {
			ARBDirectStateAccess.glBlitNamedFramebuffer(readFramebuffer, writeFramebuffer, srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
		}
	}

	@Environment(EnvType.CLIENT)
	static class GlFramebuffer extends FramebufferManager {
		@Override
		public int createFramebuffer() {
			return GlStateManager.glGenFramebuffers();
		}

		@Override
		public void setupFramebuffer(int framebuffer, int colorAttachment, int depthAttachment, int mipLevel, int bindTarget) {
			int i = bindTarget == 0 ? GlConst.GL_DRAW_FRAMEBUFFER : bindTarget;
			int j = GlStateManager.getFrameBuffer(i);
			GlStateManager._glBindFramebuffer(i, framebuffer);
			GlStateManager._glFramebufferTexture2D(i, GlConst.GL_COLOR_ATTACHMENT0, GlConst.GL_TEXTURE_2D, colorAttachment, mipLevel);
			GlStateManager._glFramebufferTexture2D(i, GlConst.GL_DEPTH_ATTACHMENT, GlConst.GL_TEXTURE_2D, depthAttachment, mipLevel);
			if (bindTarget == 0) {
				GlStateManager._glBindFramebuffer(i, j);
			}
		}

		@Override
		public void setupBlitFramebuffer(
			int readFramebuffer, int writeFramebuffer, int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter
		) {
			int i = GlStateManager.getFrameBuffer(GlConst.GL_READ_FRAMEBUFFER);
			int j = GlStateManager.getFrameBuffer(GlConst.GL_DRAW_FRAMEBUFFER);
			GlStateManager._glBindFramebuffer(GlConst.GL_READ_FRAMEBUFFER, readFramebuffer);
			GlStateManager._glBindFramebuffer(GlConst.GL_DRAW_FRAMEBUFFER, writeFramebuffer);
			GlStateManager._glBlitFrameBuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
			GlStateManager._glBindFramebuffer(GlConst.GL_READ_FRAMEBUFFER, i);
			GlStateManager._glBindFramebuffer(GlConst.GL_DRAW_FRAMEBUFFER, j);
		}
	}
}
