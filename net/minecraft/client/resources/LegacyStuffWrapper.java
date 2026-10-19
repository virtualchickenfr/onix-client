package net.minecraft.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

@Environment(EnvType.CLIENT)
public class LegacyStuffWrapper {
	@Deprecated
	public static int[] getPixels(ResourceManager resourceManager, ResourceLocation resourceLocation) throws IOException {
		try (
			InputStream inputStream = resourceManager.open(resourceLocation);
			NativeImage nativeImage = NativeImage.read(inputStream);
		) {
			return nativeImage.makePixelArray();
		}
	}
}
