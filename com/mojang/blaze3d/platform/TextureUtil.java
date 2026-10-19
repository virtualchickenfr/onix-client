package com.mojang.blaze3d.platform;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.annotation.DeobfuscateClass;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
@DeobfuscateClass
public class TextureUtil {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static final int MIN_MIPMAP_LEVEL = 0;
	private static final int DEFAULT_IMAGE_BUFFER_SIZE = 8192;

	public static ByteBuffer readResource(InputStream inputStream) throws IOException {
		ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
		return readableByteChannel instanceof SeekableByteChannel seekableByteChannel
			? readResource(readableByteChannel, (int)seekableByteChannel.size() + 1)
			: readResource(readableByteChannel, 8192);
	}

	private static ByteBuffer readResource(ReadableByteChannel channel, int bufSize) throws IOException {
		ByteBuffer byteBuffer = MemoryUtil.memAlloc(bufSize);

		try {
			while (channel.read(byteBuffer) != -1) {
				if (!byteBuffer.hasRemaining()) {
					byteBuffer = MemoryUtil.memRealloc(byteBuffer, byteBuffer.capacity() * 2);
				}
			}

			return byteBuffer;
		} catch (IOException var4) {
			MemoryUtil.memFree(byteBuffer);
			throw var4;
		}
	}

	public static void writeAsPNG(Path directory, String prefix, GpuTexture texture, int scales, IntUnaryOperator colorFunction) {
		RenderSystem.assertOnRenderThread();
		int i = 0;

		for (int j = 0; j <= scales; j++) {
			i += texture.getFormat().pixelSize() * texture.getWidth(j) * texture.getHeight(j);
		}

		GpuBuffer gpuBuffer = RenderSystem.getDevice().createBuffer(() -> "Texture output buffer", BufferType.PIXEL_PACK, BufferUsage.STATIC_READ, i);
		CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
		Runnable runnable = () -> {
			try (GpuBuffer.ReadView readView = commandEncoder.readBuffer(gpuBuffer)) {
				int j = 0;

				for (int kx = 0; kx <= scales; kx++) {
					int lx = texture.getWidth(kx);
					int m = texture.getHeight(kx);

					try (NativeImage nativeImage = new NativeImage(lx, m, false)) {
						for (int n = 0; n < m; n++) {
							for (int o = 0; o < lx; o++) {
								int p = readView.data().getInt(j + (o + n * lx) * texture.getFormat().pixelSize());
								nativeImage.setColor(o, n, colorFunction.applyAsInt(p));
							}
						}

						Path path2 = directory.resolve(prefix + "_" + kx + ".png");
						nativeImage.writeTo(path2);
						LOGGER.debug("Exported png to: {}", path2.toAbsolutePath());
					} catch (IOException var19) {
						LOGGER.debug("Unable to write: ", (Throwable)var19);
					}

					j += texture.getFormat().pixelSize() * lx * m;
				}
			}

			gpuBuffer.close();
		};
		AtomicInteger atomicInteger = new AtomicInteger();
		int k = 0;

		for (int l = 0; l <= scales; l++) {
			commandEncoder.copyTextureToBuffer(texture, gpuBuffer, k, () -> {
				if (atomicInteger.getAndIncrement() == scales) {
					runnable.run();
				}
			}, l);
			k += texture.getFormat().pixelSize() * texture.getWidth(l) * texture.getHeight(l);
		}
	}

	public static Path getDebugTexturePath(Path path) {
		return path.resolve("screenshots").resolve("debug");
	}

	public static Path getDebugTexturePath() {
		return getDebugTexturePath(Path.of("."));
	}
}
