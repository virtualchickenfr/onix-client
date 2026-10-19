package net.minecraft.client.texture;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.FramebufferManager;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class GlTexture extends GpuTexture {
	protected final int glId;
	private final Int2IntMap depthTexToFramebufferIdCache = new Int2IntOpenHashMap();
	protected boolean closed;
	protected boolean needsReinit = true;

	protected GlTexture(String label, TextureFormat format, int width, int height, int mipLevels, int glId) {
		super(label, format, width, height, mipLevels);
		this.glId = glId;
	}

	@Override
	public void close() {
		if (!this.closed) {
			this.closed = true;
			GlStateManager._deleteTexture(this.glId);
			IntIterator var1 = this.depthTexToFramebufferIdCache.values().iterator();

			while (var1.hasNext()) {
				int i = (Integer)var1.next();
				GlStateManager._glDeleteFramebuffers(i);
			}
		}
	}

	@Override
	public boolean isClosed() {
		return this.closed;
	}

	public int getOrCreateFramebuffer(FramebufferManager manager, @Nullable GpuTexture depthTexture) {
		int i = depthTexture == null ? 0 : ((GlTexture)depthTexture).glId;
		return this.depthTexToFramebufferIdCache.computeIfAbsent(i, (Int2IntFunction)(unused -> {
			int j = manager.createFramebuffer();
			manager.setupFramebuffer(j, this.glId, i, 0, 0);
			return j;
		}));
	}

	public void checkDirty() {
		if (this.needsReinit) {
			GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_WRAP_S, GlConst.toGl(this.addressModeU));
			GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_WRAP_T, GlConst.toGl(this.addressModeV));
			switch (this.minFilter) {
				case NEAREST:
					GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_MIN_FILTER, this.useMipmaps ? GlConst.GL_NEAREST_MIPMAP_LINEAR : GlConst.GL_NEAREST);
					break;
				case LINEAR:
					GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_MIN_FILTER, this.useMipmaps ? GlConst.GL_LINEAR_MIPMAP_LINEAR : GlConst.GL_LINEAR);
			}

			switch (this.magFilter) {
				case NEAREST:
					GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_MAG_FILTER, GlConst.GL_NEAREST);
					break;
				case LINEAR:
					GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_MAG_FILTER, GlConst.GL_LINEAR);
			}

			this.needsReinit = false;
		}
	}

	public int getGlId() {
		return this.glId;
	}

	@Override
	public void setAddressMode(AddressMode addressMode, AddressMode addressMode2) {
		super.setAddressMode(addressMode, addressMode2);
		this.needsReinit = true;
	}

	@Override
	public void setTextureFilter(FilterMode filterMode, FilterMode filterMode2, boolean bl) {
		super.setTextureFilter(filterMode, filterMode2, bl);
		this.needsReinit = true;
	}
}
