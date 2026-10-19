package net.minecraft.client.texture;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resource.metadata.TextureResourceMetadata;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public record TextureContents(NativeImage image, @Nullable TextureResourceMetadata metadata) implements Closeable {
	public static TextureContents load(ResourceManager resourceManager, Identifier textureId) throws IOException {
		Resource resource = resourceManager.getResourceOrThrow(textureId);
		InputStream inputStream = resource.getInputStream();

		NativeImage nativeImage;
		try {
			nativeImage = NativeImage.read(inputStream);
		} catch (Throwable var8) {
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (Throwable var7) {
					var8.addSuppressed(var7);
				}
			}

			throw var8;
		}

		if (inputStream != null) {
			inputStream.close();
		}

		TextureResourceMetadata textureResourceMetadata = (TextureResourceMetadata)resource.getMetadata().decode(TextureResourceMetadata.SERIALIZER).orElse(null);
		return new TextureContents(nativeImage, textureResourceMetadata);
	}

	public static TextureContents createMissing() {
		return new TextureContents(MissingSprite.createImage(), null);
	}

	public boolean blur() {
		return this.metadata != null ? this.metadata.blur() : false;
	}

	public boolean clamp() {
		return this.metadata != null ? this.metadata.clamp() : false;
	}

	public void close() {
		this.image.close();
	}
}
