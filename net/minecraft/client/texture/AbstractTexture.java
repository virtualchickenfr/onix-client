package net.minecraft.client.texture;

import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.TriState;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class AbstractTexture implements AutoCloseable {
	@Nullable
	protected GpuTexture glTexture;
	protected boolean bilinear;

	public void setClamp(boolean clamp) {
		if (this.glTexture == null) {
			throw new IllegalStateException("Texture does not exist, can't change its clamp before something initializes it");
		} else {
			this.glTexture.setAddressMode(clamp ? AddressMode.CLAMP_TO_EDGE : AddressMode.REPEAT);
		}
	}

	public void setFilter(TriState bilinear, boolean mipmap) {
		this.setFilter(bilinear.asBoolean(this.bilinear), mipmap);
	}

	public void setFilter(boolean bilinear, boolean mipmap) {
		if (this.glTexture == null) {
			throw new IllegalStateException("Texture does not exist, can't get change its filter before something initializes it");
		} else {
			this.glTexture.setTextureFilter(bilinear ? FilterMode.LINEAR : FilterMode.NEAREST, mipmap);
		}
	}

	public void close() {
		if (this.glTexture != null) {
			this.glTexture.close();
			this.glTexture = null;
		}
	}

	public GpuTexture getGlTexture() {
		if (this.glTexture == null) {
			throw new IllegalStateException("Texture does not exist, can't get it before something initializes it");
		} else {
			return this.glTexture;
		}
	}
}
