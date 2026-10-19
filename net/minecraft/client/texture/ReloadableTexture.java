package net.minecraft.client.texture;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;
import java.io.IOException;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public abstract class ReloadableTexture extends AbstractTexture {
	private final Identifier textureId;

	public ReloadableTexture(Identifier textureId) {
		this.textureId = textureId;
	}

	public Identifier getId() {
		return this.textureId;
	}

	public void reload(TextureContents contents) {
		boolean bl = contents.clamp();
		boolean bl2 = contents.blur();
		this.bilinear = bl2;

		try (NativeImage nativeImage = contents.image()) {
			this.load(nativeImage, bl2, bl);
		}
	}

	private void load(NativeImage image, boolean blur, boolean clamp) {
		GpuDevice gpuDevice = RenderSystem.getDevice();
		this.glTexture = gpuDevice.createTexture(this.textureId::toString, TextureFormat.RGBA8, image.getWidth(), image.getHeight(), 1);
		this.setFilter(blur, false);
		this.setClamp(clamp);
		gpuDevice.createCommandEncoder().writeToTexture(this.glTexture, image);
	}

	public abstract TextureContents loadContents(ResourceManager resourceManager) throws IOException;
}
