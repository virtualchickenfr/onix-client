package com.mojang.blaze3d.textures;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.annotation.DeobfuscateClass;

@Environment(EnvType.CLIENT)
@DeobfuscateClass
public abstract class GpuTexture implements AutoCloseable {
	private final TextureFormat format;
	private final int width;
	private final int height;
	private final int mipLevels;
	private final String label;
	protected AddressMode addressModeU = AddressMode.REPEAT;
	protected AddressMode addressModeV = AddressMode.REPEAT;
	protected FilterMode minFilter = FilterMode.NEAREST;
	protected FilterMode magFilter = FilterMode.LINEAR;
	protected boolean useMipmaps = true;

	public GpuTexture(String label, TextureFormat format, int width, int height, int mipLevels) {
		this.label = label;
		this.format = format;
		this.width = width;
		this.height = height;
		this.mipLevels = mipLevels;
	}

	public int getWidth(int mipLevel) {
		return this.width >> mipLevel;
	}

	public int getHeight(int mipLevel) {
		return this.height >> mipLevel;
	}

	public int getMipLevels() {
		return this.mipLevels;
	}

	public TextureFormat getFormat() {
		return this.format;
	}

	public void setAddressMode(AddressMode addressMode) {
		this.setAddressMode(addressMode, addressMode);
	}

	public void setAddressMode(AddressMode addressModeU, AddressMode addressModeV) {
		this.addressModeU = addressModeU;
		this.addressModeV = addressModeV;
	}

	public void setTextureFilter(FilterMode filter, boolean useMipmaps) {
		this.setTextureFilter(filter, filter, useMipmaps);
	}

	public void setTextureFilter(FilterMode minFilter, FilterMode magFilter, boolean useMipmaps) {
		this.minFilter = minFilter;
		this.magFilter = magFilter;
		this.useMipmaps = useMipmaps;
	}

	public String getLabel() {
		return this.label;
	}

	public abstract void close();

	public abstract boolean isClosed();
}
