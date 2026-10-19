package net.minecraft.client.util;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * A screenshot recorder takes screenshots and saves them into tga file format. It also
 * holds a few utility methods for other types of screenshots.
 */
@Environment(EnvType.CLIENT)
public class ScreenshotRecorder {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final String SCREENSHOTS_DIRECTORY = "screenshots";

	public static void saveScreenshot(File gameDirectory, Framebuffer framebuffer, Consumer<Text> messageReceiver) {
		saveScreenshot(gameDirectory, null, framebuffer, messageReceiver);
	}

	public static void saveScreenshot(File gameDirectory, @Nullable String fileName, Framebuffer framebuffer, Consumer<Text> messageReceiver) {
		takeScreenshot(
			framebuffer,
			image -> {
				File file2 = new File(gameDirectory, "screenshots");
				file2.mkdir();
				File file3;
				if (fileName == null) {
					file3 = getScreenshotFilename(file2);
				} else {
					file3 = new File(file2, fileName);
				}

				Util.getIoWorkerExecutor()
					.execute(
						() -> {
							try {
								NativeImage exception = image;

								try {
									image.writeTo(file3);
									Text text = Text.literal(file3.getName())
										.formatted(Formatting.UNDERLINE)
										.styled(style -> style.withClickEvent(new ClickEvent.OpenFile(file3.getAbsoluteFile())));
									messageReceiver.accept(Text.translatable("screenshot.success", text));
								} catch (Throwable var7) {
									if (image != null) {
										try {
											exception.close();
										} catch (Throwable var6) {
											var7.addSuppressed(var6);
										}
									}

									throw var7;
								}

								if (image != null) {
									image.close();
								}
							} catch (Exception var8) {
								LOGGER.warn("Couldn't save screenshot", (Throwable)var8);
								messageReceiver.accept(Text.translatable("screenshot.failure", var8.getMessage()));
							}
						}
					);
			}
		);
	}

	public static void takeScreenshot(Framebuffer framebuffer, Consumer<NativeImage> callback) {
		int i = framebuffer.textureWidth;
		int j = framebuffer.textureHeight;
		GpuTexture gpuTexture = framebuffer.getColorAttachment();
		if (gpuTexture == null) {
			throw new IllegalStateException("Tried to capture screenshot of an incomplete framebuffer");
		} else {
			GpuBuffer gpuBuffer = RenderSystem.getDevice()
				.createBuffer(() -> "Screenshot buffer", BufferType.PIXEL_PACK, BufferUsage.STATIC_READ, i * j * gpuTexture.getFormat().pixelSize());
			CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
			RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(gpuTexture, gpuBuffer, 0, () -> {
				try (GpuBuffer.ReadView readView = commandEncoder.readBuffer(gpuBuffer)) {
					NativeImage nativeImage = new NativeImage(i, j, false);

					for (int k = 0; k < j; k++) {
						for (int l = 0; l < i; l++) {
							int m = readView.data().getInt((l + k * i) * gpuTexture.getFormat().pixelSize());
							nativeImage.setColor(l, j - k - 1, m | 0xFF000000);
						}
					}

					callback.accept(nativeImage);
				}

				gpuBuffer.close();
			}, 0);
		}
	}

	private static File getScreenshotFilename(File directory) {
		String string = Util.getFormattedCurrentTime();
		int i = 1;

		while (true) {
			File file = new File(directory, string + (i == 1 ? "" : "_" + i) + ".png");
			if (!file.exists()) {
				return file;
			}

			i++;
		}
	}
}
