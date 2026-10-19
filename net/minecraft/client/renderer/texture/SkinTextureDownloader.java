package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class SkinTextureDownloader {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int SKIN_WIDTH = 64;
	private static final int SKIN_HEIGHT = 64;
	private static final int LEGACY_SKIN_HEIGHT = 32;

	public static CompletableFuture<ResourceLocation> downloadAndRegisterSkin(ResourceLocation resourceLocation, Path path, String string, boolean bl) {
		return CompletableFuture.<NativeImage>supplyAsync(() -> {
			NativeImage nativeImage;
			try {
				nativeImage = downloadSkin(path, string);
			} catch (IOException iOException) {
				throw new UncheckedIOException(iOException);
			}

			return bl ? processLegacySkin(nativeImage, string) : nativeImage;
		}, Util.nonCriticalIoPool().forName("downloadTexture")).thenCompose(nativeImage -> registerTextureInManager(resourceLocation, nativeImage));
	}

	private static NativeImage downloadSkin(Path path, String string) throws IOException {
		if (Files.isRegularFile(path)) {
			LOGGER.debug("Loading HTTP texture from local cache ({})", path);

			try (InputStream inputStream = Files.newInputStream(path)) {
				return NativeImage.read(inputStream);
			}
		} else {
			HttpURLConnection httpURLConnection = null;
			LOGGER.debug("Downloading HTTP texture from {} to {}", string, path);
			URI uRI = URI.create(string);

			try {
				httpURLConnection = (HttpURLConnection)uRI.toURL().openConnection(Minecraft.getInstance().getProxy());
				httpURLConnection.setDoInput(true);
				httpURLConnection.setDoOutput(false);
				httpURLConnection.connect();
				int i = httpURLConnection.getResponseCode();
				if (i / 100 != 2) {
					throw new IOException("Failed to open " + uRI + ", HTTP error code: " + i);
				}

				byte[] bs = httpURLConnection.getInputStream().readAllBytes();

				try {
					FileUtil.createDirectoriesSafe(path.getParent());
					Files.write(path, bs);
				} catch (IOException iOException) {
					LOGGER.warn("Failed to cache texture {} in {}", string, path);
				}

				return NativeImage.read(bs);
			} finally {
				if (httpURLConnection != null) {
					httpURLConnection.disconnect();
				}
			}
		}
	}

	private static CompletableFuture<ResourceLocation> registerTextureInManager(ResourceLocation resourceLocation, NativeImage nativeImage) {
		Minecraft minecraft = Minecraft.getInstance();
		return CompletableFuture.supplyAsync(() -> {
			DynamicTexture dynamicTexture = new DynamicTexture(resourceLocation::toString, nativeImage);
			minecraft.getTextureManager().register(resourceLocation, dynamicTexture);
			return resourceLocation;
		}, minecraft);
	}

	private static NativeImage processLegacySkin(NativeImage nativeImage, String string) {
		int i = nativeImage.getHeight();
		int j = nativeImage.getWidth();
		if (j == 64 && (i == 32 || i == 64)) {
			boolean bl = i == 32;
			if (bl) {
				NativeImage nativeImage2 = new NativeImage(64, 64, true);
				nativeImage2.copyFrom(nativeImage);
				nativeImage.close();
				nativeImage = nativeImage2;
				nativeImage.fillRect(0, 32, 64, 32, 0);
				nativeImage.copyRect(4, 16, 16, 32, 4, 4, true, false);
				nativeImage.copyRect(8, 16, 16, 32, 4, 4, true, false);
				nativeImage.copyRect(0, 20, 24, 32, 4, 12, true, false);
				nativeImage.copyRect(4, 20, 16, 32, 4, 12, true, false);
				nativeImage.copyRect(8, 20, 8, 32, 4, 12, true, false);
				nativeImage.copyRect(12, 20, 16, 32, 4, 12, true, false);
				nativeImage.copyRect(44, 16, -8, 32, 4, 4, true, false);
				nativeImage.copyRect(48, 16, -8, 32, 4, 4, true, false);
				nativeImage.copyRect(40, 20, 0, 32, 4, 12, true, false);
				nativeImage.copyRect(44, 20, -8, 32, 4, 12, true, false);
				nativeImage.copyRect(48, 20, -16, 32, 4, 12, true, false);
				nativeImage.copyRect(52, 20, -8, 32, 4, 12, true, false);
			}

			setNoAlpha(nativeImage, 0, 0, 32, 16);
			if (bl) {
				doNotchTransparencyHack(nativeImage, 32, 0, 64, 32);
			}

			setNoAlpha(nativeImage, 0, 16, 64, 32);
			setNoAlpha(nativeImage, 16, 48, 48, 64);
			return nativeImage;
		} else {
			nativeImage.close();
			throw new IllegalStateException("Discarding incorrectly sized (" + j + "x" + i + ") skin texture from " + string);
		}
	}

	private static void doNotchTransparencyHack(NativeImage nativeImage, int i, int j, int k, int l) {
		for (int m = i; m < k; m++) {
			for (int n = j; n < l; n++) {
				int o = nativeImage.getPixel(m, n);
				if (ARGB.alpha(o) < 128) {
					return;
				}
			}
		}

		for (int m = i; m < k; m++) {
			for (int n = j; n < l; n++) {
				nativeImage.setPixel(m, n, nativeImage.getPixel(m, n) & 16777215);
			}
		}
	}

	private static void setNoAlpha(NativeImage nativeImage, int i, int j, int k, int l) {
		for (int m = i; m < k; m++) {
			for (int n = j; n < l; n++) {
				nativeImage.setPixel(m, n, ARGB.opaque(nativeImage.getPixel(m, n)));
			}
		}
	}
}
