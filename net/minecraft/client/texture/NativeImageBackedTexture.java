package net.minecraft.client.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class NativeImageBackedTexture extends AbstractTexture implements DynamicTexture {
	private static final Logger LOGGER = LogUtils.getLogger();
	@Nullable
	private NativeImage image;

	public NativeImageBackedTexture(Supplier<String> nameSupplier, NativeImage image) {
		this.image = image;
		this.glTexture = RenderSystem.getDevice().createTexture(nameSupplier, TextureFormat.RGBA8, this.image.getWidth(), this.image.getHeight(), 1);
		this.upload();
	}

	public NativeImageBackedTexture(String name, int width, int height, boolean useStb) {
		this.image = new NativeImage(width, height, useStb);
		this.glTexture = RenderSystem.getDevice().createTexture(name, TextureFormat.RGBA8, this.image.getWidth(), this.image.getHeight(), 1);
	}

	public NativeImageBackedTexture(Supplier<String> nameSupplier, int width, int height, boolean useStb) {
		this.image = new NativeImage(width, height, useStb);
		this.glTexture = RenderSystem.getDevice().createTexture(nameSupplier, TextureFormat.RGBA8, this.image.getWidth(), this.image.getHeight(), 1);
	}

	public void upload() {
		if (this.image != null && this.glTexture != null) {
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(this.glTexture, this.image);
		} else {
			LOGGER.warn("Trying to upload disposed texture {}", this.getGlTexture().getLabel());
		}
	}

	@Nullable
	public NativeImage getImage() {
		return this.image;
	}

	public void setImage(NativeImage image) {
		if (this.image != null) {
			this.image.close();
		}

		this.image = image;
	}

	@Override
	public void close() {
		if (this.image != null) {
			this.image.close();
			this.image = null;
		}

		super.close();
	}

	@Override
	public void save(Identifier id, Path path) throws IOException {
		if (this.image != null) {
			String string = id.toUnderscoreSeparatedString() + ".png";
			Path path2 = path.resolve(string);
			this.image.writeTo(path2);
		}
	}
}
