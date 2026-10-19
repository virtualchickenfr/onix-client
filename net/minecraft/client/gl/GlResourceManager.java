package net.minecraft.client.gl;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntList;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Collection;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.ColorHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GlResourceManager implements CommandEncoder {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final GlBackend backend;
	private final int temporaryFb1;
	private final int temporaryFb2;
	@Nullable
	private RenderPipeline currentPipeline;
	private boolean renderPassOpen;
	@Nullable
	private ShaderProgram currentProgram;

	protected GlResourceManager(GlBackend backend) {
		this.backend = backend;
		this.temporaryFb1 = backend.getFramebufferManager().createFramebuffer();
		this.temporaryFb2 = backend.getFramebufferManager().createFramebuffer();
	}

	@Override
	public RenderPass createRenderPass(GpuTexture gpuTexture, OptionalInt optionalInt) {
		return this.createRenderPass(gpuTexture, optionalInt, null, OptionalDouble.empty());
	}

	@Override
	public RenderPass createRenderPass(GpuTexture gpuTexture, OptionalInt optionalInt, @Nullable GpuTexture gpuTexture2, OptionalDouble optionalDouble) {
		if (this.renderPassOpen) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		} else {
			if (optionalDouble.isPresent() && gpuTexture2 == null) {
				LOGGER.warn("Depth clear value was provided but no depth texture is being used");
			}

			if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Color texture is closed");
			} else if (gpuTexture2 != null && gpuTexture2.isClosed()) {
				throw new IllegalStateException("Depth texture is closed");
			} else {
				this.renderPassOpen = true;
				int i = ((GlTexture)gpuTexture).getOrCreateFramebuffer(this.backend.getFramebufferManager(), gpuTexture2);
				GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, i);
				int j = 0;
				if (optionalInt.isPresent()) {
					int k = optionalInt.getAsInt();
					GL11.glClearColor(ColorHelper.getRedFloat(k), ColorHelper.getGreenFloat(k), ColorHelper.getBlueFloat(k), ColorHelper.getAlphaFloat(k));
					j |= GlConst.GL_COLOR_BUFFER_BIT;
				}

				if (gpuTexture2 != null && optionalDouble.isPresent()) {
					GL11.glClearDepth(optionalDouble.getAsDouble());
					j |= GlConst.GL_DEPTH_BUFFER_BIT;
				}

				if (j != 0) {
					GlStateManager._disableScissorTest();
					GlStateManager._depthMask(true);
					GlStateManager._colorMask(true, true, true, true);
					GlStateManager._clear(j);
				}

				GlStateManager._viewport(0, 0, gpuTexture.getWidth(0), gpuTexture.getHeight(0));
				this.currentPipeline = null;
				return new RenderPassImpl(this, gpuTexture2 != null);
			}
		}
	}

	@Override
	public void clearColorTexture(GpuTexture gpuTexture, int i) {
		if (this.renderPassOpen) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		} else if (!gpuTexture.getFormat().hasColorAspect()) {
			throw new IllegalStateException("Trying to clear a non-color texture as color");
		} else if (gpuTexture.isClosed()) {
			throw new IllegalStateException("Color texture is closed");
		} else {
			this.backend.getFramebufferManager().setupFramebuffer(this.temporaryFb2, ((GlTexture)gpuTexture).glId, 0, 0, 36160);
			GL11.glClearColor(ColorHelper.getRedFloat(i), ColorHelper.getGreenFloat(i), ColorHelper.getBlueFloat(i), ColorHelper.getAlphaFloat(i));
			GlStateManager._disableScissorTest();
			GlStateManager._colorMask(true, true, true, true);
			GlStateManager._clear(GlConst.GL_COLOR_BUFFER_BIT);
			GlStateManager._glFramebufferTexture2D(GlConst.GL_FRAMEBUFFER, GlConst.GL_COLOR_ATTACHMENT0, GlConst.GL_TEXTURE_2D, 0, 0);
			GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, 0);
		}
	}

	@Override
	public void clearColorAndDepthTextures(GpuTexture gpuTexture, int i, GpuTexture gpuTexture2, double d) {
		if (this.renderPassOpen) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		} else if (!gpuTexture.getFormat().hasColorAspect()) {
			throw new IllegalStateException("Trying to clear a non-color texture as color");
		} else if (!gpuTexture2.getFormat().hasDepthAspect()) {
			throw new IllegalStateException("Trying to clear a non-depth texture as depth");
		} else if (gpuTexture.isClosed()) {
			throw new IllegalStateException("Color texture is closed");
		} else if (gpuTexture2.isClosed()) {
			throw new IllegalStateException("Depth texture is closed");
		} else {
			int j = ((GlTexture)gpuTexture).getOrCreateFramebuffer(this.backend.getFramebufferManager(), gpuTexture2);
			GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, j);
			GlStateManager._disableScissorTest();
			GL11.glClearDepth(d);
			GL11.glClearColor(ColorHelper.getRedFloat(i), ColorHelper.getGreenFloat(i), ColorHelper.getBlueFloat(i), ColorHelper.getAlphaFloat(i));
			GlStateManager._depthMask(true);
			GlStateManager._colorMask(true, true, true, true);
			GlStateManager._clear(GlConst.GL_DEPTH_BUFFER_BIT | GlConst.GL_COLOR_BUFFER_BIT);
			GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, 0);
		}
	}

	@Override
	public void clearDepthTexture(GpuTexture gpuTexture, double d) {
		if (this.renderPassOpen) {
			throw new IllegalStateException("Close the existing render pass before creating a new one!");
		} else if (!gpuTexture.getFormat().hasDepthAspect()) {
			throw new IllegalStateException("Trying to clear a non-depth texture as depth");
		} else if (gpuTexture.isClosed()) {
			throw new IllegalStateException("Depth texture is closed");
		} else {
			this.backend.getFramebufferManager().setupFramebuffer(this.temporaryFb2, 0, ((GlTexture)gpuTexture).glId, 0, 36160);
			GL11.glDrawBuffer(0);
			GL11.glClearDepth(d);
			GlStateManager._depthMask(true);
			GlStateManager._disableScissorTest();
			GlStateManager._clear(GlConst.GL_DEPTH_BUFFER_BIT);
			GL11.glDrawBuffer(GlConst.GL_COLOR_ATTACHMENT0);
			GlStateManager._glFramebufferTexture2D(GlConst.GL_FRAMEBUFFER, GlConst.GL_DEPTH_ATTACHMENT, GlConst.GL_TEXTURE_2D, 0, 0);
			GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, 0);
		}
	}

	@Override
	public void writeToBuffer(GpuBuffer gpuBuffer, ByteBuffer byteBuffer, int i) {
		if (this.renderPassOpen) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			GlGpuBuffer glGpuBuffer = (GlGpuBuffer)gpuBuffer;
			if (glGpuBuffer.closed) {
				throw new IllegalStateException("Buffer already closed");
			} else if (!glGpuBuffer.usage().isWritable()) {
				throw new IllegalStateException("Buffer is not writable");
			} else {
				int j = byteBuffer.remaining();
				if (j + i > glGpuBuffer.size) {
					throw new IllegalArgumentException(
						"Cannot write more data than this buffer can hold (attempting to write " + j + " bytes at offset " + i + " to " + glGpuBuffer.size + " size buffer)"
					);
				} else {
					GlStateManager._glBindBuffer(GlConst.toGl(glGpuBuffer.type()), glGpuBuffer.id);
					if (glGpuBuffer.hasData) {
						GlStateManager._glBufferSubData(GlConst.toGl(glGpuBuffer.type()), i, byteBuffer);
					} else if (i == 0 && j == glGpuBuffer.size) {
						GlStateManager._glBufferData(GlConst.toGl(glGpuBuffer.type()), byteBuffer, GlConst.toGl(glGpuBuffer.usage()));
						GlGpuBuffer.POOL.malloc(glGpuBuffer.id, glGpuBuffer.size);
						glGpuBuffer.hasData = true;
						this.backend.getDebugLabelManager().labelGlGpuBuffer(glGpuBuffer);
					} else {
						GlStateManager._glBufferData(GlConst.toGl(glGpuBuffer.type()), glGpuBuffer.size, GlConst.toGl(glGpuBuffer.usage()));
						GlStateManager._glBufferSubData(GlConst.toGl(glGpuBuffer.type()), i, byteBuffer);
						GlGpuBuffer.POOL.malloc(glGpuBuffer.id, glGpuBuffer.size);
						glGpuBuffer.hasData = true;
						this.backend.getDebugLabelManager().labelGlGpuBuffer(glGpuBuffer);
					}
				}
			}
		}
	}

	@Override
	public GpuBuffer.ReadView readBuffer(GpuBuffer gpuBuffer) {
		return this.readBuffer(gpuBuffer, 0, gpuBuffer.size());
	}

	@Override
	public GpuBuffer.ReadView readBuffer(GpuBuffer gpuBuffer, int i, int j) {
		if (this.renderPassOpen) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			GlGpuBuffer glGpuBuffer = (GlGpuBuffer)gpuBuffer;
			if (glGpuBuffer.closed) {
				throw new IllegalStateException("Buffer already closed");
			} else if (!glGpuBuffer.usage().isReadable()) {
				throw new IllegalStateException("Buffer is not readable");
			} else if (i + j > glGpuBuffer.size) {
				throw new IllegalArgumentException(
					"Cannot read more data than this buffer can hold (attempting to read " + j + " bytes at offset " + i + " from " + glGpuBuffer.size + " size buffer)"
				);
			} else {
				GlStateManager.clearGlErrors();
				GlStateManager._glBindBuffer(GlConst.toGl(glGpuBuffer.type()), glGpuBuffer.id);
				ByteBuffer byteBuffer = GlStateManager._glMapBufferRange(GlConst.toGl(glGpuBuffer.type()), i, j, 1);
				if (byteBuffer == null) {
					throw new IllegalStateException("Can't read buffer, opengl error " + GlStateManager._getError());
				} else {
					return new GlGpuBuffer.ReadResultImpl(GlConst.toGl(glGpuBuffer.type()), byteBuffer);
				}
			}
		}
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage) {
		int i = gpuTexture.getWidth(0);
		int j = gpuTexture.getHeight(0);
		if (nativeImage.getWidth() != i || nativeImage.getHeight() != j) {
			throw new IllegalArgumentException(
				"Cannot replace texture of size " + i + "x" + j + " with image of size " + nativeImage.getWidth() + "x" + nativeImage.getHeight()
			);
		} else if (gpuTexture.isClosed()) {
			throw new IllegalStateException("Destination texture is closed");
		} else {
			this.writeToTexture(gpuTexture, nativeImage, 0, 0, 0, i, j, 0, 0);
		}
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, NativeImage nativeImage, int i, int j, int k, int l, int m, int n, int o) {
		if (this.renderPassOpen) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (i >= 0 && i < gpuTexture.getMipLevels()) {
			if (n + l > nativeImage.getWidth() || o + m > nativeImage.getHeight()) {
				throw new IllegalArgumentException(
					"Copy source ("
						+ nativeImage.getWidth()
						+ "x"
						+ nativeImage.getHeight()
						+ ") is not large enough to read a rectangle of "
						+ l
						+ "x"
						+ m
						+ " from "
						+ n
						+ "x"
						+ o
				);
			} else if (j + l > gpuTexture.getWidth(i) || k + m > gpuTexture.getHeight(i)) {
				throw new IllegalArgumentException(
					"Dest texture (" + l + "x" + m + ") is not large enough to write a rectangle of " + l + "x" + m + " at " + j + "x" + k + " (at mip level " + i + ")"
				);
			} else if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Destination texture is closed");
			} else {
				GlStateManager._bindTexture(((GlTexture)gpuTexture).glId);
				GlStateManager._pixelStore(GlConst.GL_UNPACK_ROW_LENGTH, nativeImage.getWidth());
				GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_PIXELS, n);
				GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_ROWS, o);
				GlStateManager._pixelStore(GlConst.GL_UNPACK_ALIGNMENT, nativeImage.getFormat().getChannelCount());
				GlStateManager._texSubImage2D(GlConst.GL_TEXTURE_2D, i, j, k, l, m, GlConst.toGl(nativeImage.getFormat()), GlConst.GL_UNSIGNED_BYTE, nativeImage.imageId());
			}
		} else {
			throw new IllegalArgumentException("Invalid mipLevel " + i + ", must be >= 0 and < " + gpuTexture.getMipLevels());
		}
	}

	@Override
	public void writeToTexture(GpuTexture gpuTexture, IntBuffer intBuffer, NativeImage.Format format, int i, int j, int k, int l, int m) {
		if (this.renderPassOpen) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (i >= 0 && i < gpuTexture.getMipLevels()) {
			if (l * m > intBuffer.remaining()) {
				throw new IllegalArgumentException(
					"Copy would overrun the source buffer (remaining length of " + intBuffer.remaining() + ", but copy is " + l + "x" + m + ")"
				);
			} else if (j + l > gpuTexture.getWidth(i) || k + m > gpuTexture.getHeight(i)) {
				throw new IllegalArgumentException(
					"Dest texture ("
						+ gpuTexture.getWidth(i)
						+ "x"
						+ gpuTexture.getHeight(i)
						+ ") is not large enough to write a rectangle of "
						+ l
						+ "x"
						+ m
						+ " at "
						+ j
						+ "x"
						+ k
				);
			} else if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Destination texture is closed");
			} else {
				GlStateManager._bindTexture(((GlTexture)gpuTexture).glId);
				GlStateManager._pixelStore(GlConst.GL_UNPACK_ROW_LENGTH, l);
				GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_PIXELS, 0);
				GlStateManager._pixelStore(GlConst.GL_UNPACK_SKIP_ROWS, 0);
				GlStateManager._pixelStore(GlConst.GL_UNPACK_ALIGNMENT, format.getChannelCount());
				GlStateManager._texSubImage2D(GlConst.GL_TEXTURE_2D, i, j, k, l, m, GlConst.toGl(format), GlConst.GL_UNSIGNED_BYTE, intBuffer);
			}
		} else {
			throw new IllegalArgumentException("Invalid mipLevel, must be >= 0 and < " + gpuTexture.getMipLevels());
		}
	}

	@Override
	public void copyTextureToBuffer(GpuTexture gpuTexture, GpuBuffer gpuBuffer, int i, Runnable runnable, int j) {
		if (this.renderPassOpen) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else {
			this.copyTextureToBuffer(gpuTexture, gpuBuffer, i, runnable, j, 0, 0, gpuTexture.getWidth(j), gpuTexture.getHeight(j));
		}
	}

	@Override
	public void copyTextureToBuffer(GpuTexture gpuTexture, GpuBuffer gpuBuffer, int i, Runnable runnable, int j, int k, int l, int m, int n) {
		if (this.renderPassOpen) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (j >= 0 && j < gpuTexture.getMipLevels()) {
			if (gpuTexture.getWidth(j) * gpuTexture.getHeight(j) * gpuTexture.getFormat().pixelSize() + i > gpuBuffer.size()) {
				throw new IllegalArgumentException(
					"Buffer of size "
						+ gpuBuffer.size()
						+ " is not large enough to hold "
						+ m
						+ "x"
						+ n
						+ " pixels ("
						+ gpuTexture.getFormat().pixelSize()
						+ " bytes each) starting from offset "
						+ i
				);
			} else if (gpuBuffer.type() != BufferType.PIXEL_PACK) {
				throw new IllegalArgumentException("Buffer of type " + gpuBuffer.type() + " cannot be used to retrieve a texture");
			} else if (k + m > gpuTexture.getWidth(j) || l + n > gpuTexture.getHeight(j)) {
				throw new IllegalArgumentException(
					"Copy source texture ("
						+ gpuTexture.getWidth(j)
						+ "x"
						+ gpuTexture.getHeight(j)
						+ ") is not large enough to read a rectangle of "
						+ m
						+ "x"
						+ n
						+ " from "
						+ k
						+ ","
						+ l
				);
			} else if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Source texture is closed");
			} else if (gpuBuffer.isClosed()) {
				throw new IllegalStateException("Destination buffer is closed");
			} else {
				GlStateManager.clearGlErrors();
				this.backend.getFramebufferManager().setupFramebuffer(this.temporaryFb1, ((GlTexture)gpuTexture).getGlId(), 0, j, 36008);
				GlStateManager._glBindBuffer(GlConst.toGl(gpuBuffer.type()), ((GlGpuBuffer)gpuBuffer).id);
				GlStateManager._pixelStore(GlConst.GL_PACK_ROW_LENGTH, m);
				GlStateManager._readPixels(k, l, m, n, GlConst.toGlExternalId(gpuTexture.getFormat()), GlConst.toGlType(gpuTexture.getFormat()), i);
				RenderSystem.queueFencedTask(runnable);
				GlStateManager._glFramebufferTexture2D(GlConst.GL_READ_FRAMEBUFFER, GlConst.GL_COLOR_ATTACHMENT0, GlConst.GL_TEXTURE_2D, 0, j);
				GlStateManager._glBindFramebuffer(GlConst.GL_READ_FRAMEBUFFER, 0);
				GlStateManager._glBindBuffer(GlConst.toGl(gpuBuffer.type()), 0);
				int o = GlStateManager._getError();
				if (o != 0) {
					throw new IllegalStateException("Couldn't perform copyTobuffer for texture " + gpuTexture.getLabel() + ": GL error " + o);
				}
			}
		} else {
			throw new IllegalArgumentException("Invalid mipLevel " + j + ", must be >= 0 and < " + gpuTexture.getMipLevels());
		}
	}

	@Override
	public void copyTextureToTexture(GpuTexture gpuTexture, GpuTexture gpuTexture2, int i, int j, int k, int l, int m, int n, int o) {
		if (this.renderPassOpen) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (i >= 0 && i < gpuTexture.getMipLevels() && i < gpuTexture2.getMipLevels()) {
			if (j + n > gpuTexture2.getWidth(i) || k + o > gpuTexture2.getHeight(i)) {
				throw new IllegalArgumentException(
					"Dest texture ("
						+ gpuTexture2.getWidth(i)
						+ "x"
						+ gpuTexture2.getHeight(i)
						+ ") is not large enough to write a rectangle of "
						+ n
						+ "x"
						+ o
						+ " at "
						+ j
						+ "x"
						+ k
				);
			} else if (l + n > gpuTexture.getWidth(i) || m + o > gpuTexture.getHeight(i)) {
				throw new IllegalArgumentException(
					"Source texture ("
						+ gpuTexture.getWidth(i)
						+ "x"
						+ gpuTexture.getHeight(i)
						+ ") is not large enough to read a rectangle of "
						+ n
						+ "x"
						+ o
						+ " at "
						+ l
						+ "x"
						+ m
				);
			} else if (gpuTexture.isClosed()) {
				throw new IllegalStateException("Source texture is closed");
			} else if (gpuTexture2.isClosed()) {
				throw new IllegalStateException("Destination texture is closed");
			} else {
				GlStateManager.clearGlErrors();
				GlStateManager._disableScissorTest();
				boolean bl = gpuTexture.getFormat().hasDepthAspect();
				int p = ((GlTexture)gpuTexture).getGlId();
				int q = ((GlTexture)gpuTexture2).getGlId();
				this.backend.getFramebufferManager().setupFramebuffer(this.temporaryFb1, bl ? 0 : p, bl ? p : 0, 0, 0);
				this.backend.getFramebufferManager().setupFramebuffer(this.temporaryFb2, bl ? 0 : q, bl ? q : 0, 0, 0);
				this.backend.getFramebufferManager().setupBlitFramebuffer(this.temporaryFb1, this.temporaryFb2, l, m, n, o, j, k, n, o, bl ? 256 : 16384, 9728);
				int r = GlStateManager._getError();
				if (r != 0) {
					throw new IllegalStateException(
						"Couldn't perform copyToTexture for texture " + gpuTexture.getLabel() + " to " + gpuTexture2.getLabel() + ": GL error " + r
					);
				}
			}
		} else {
			throw new IllegalArgumentException("Invalid mipLevel " + i + ", must be >= 0 and < " + gpuTexture.getMipLevels() + " and < " + gpuTexture2.getMipLevels());
		}
	}

	@Override
	public void presentTexture(GpuTexture gpuTexture) {
		if (this.renderPassOpen) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		} else if (!gpuTexture.getFormat().hasColorAspect()) {
			throw new IllegalStateException("Cannot present a non-color texture!");
		} else {
			GlStateManager._disableScissorTest();
			GlStateManager._viewport(0, 0, gpuTexture.getWidth(0), gpuTexture.getHeight(0));
			GlStateManager._depthMask(true);
			GlStateManager._colorMask(true, true, true, true);
			this.backend.getFramebufferManager().setupFramebuffer(this.temporaryFb2, ((GlTexture)gpuTexture).getGlId(), 0, 0, 0);
			this.backend
				.getFramebufferManager()
				.setupBlitFramebuffer(
					this.temporaryFb2, 0, 0, 0, gpuTexture.getWidth(0), gpuTexture.getHeight(0), 0, 0, gpuTexture.getWidth(0), gpuTexture.getHeight(0), 16384, 9728
				);
		}
	}

	protected void drawObjectsWithRenderPass(
		RenderPassImpl pass, Collection<RenderPass.RenderObject> objects, @Nullable GpuBuffer indexBuffer, @Nullable VertexFormat.IndexType indexType
	) {
		if (this.setupRenderPass(pass)) {
			if (indexType == null) {
				indexType = VertexFormat.IndexType.SHORT;
			}

			for (RenderPass.RenderObject renderObject : objects) {
				VertexFormat.IndexType indexType2 = renderObject.indexType() == null ? indexType : renderObject.indexType();
				pass.setIndexBuffer(renderObject.indexBuffer() == null ? indexBuffer : renderObject.indexBuffer(), indexType2);
				pass.setVertexBuffer(renderObject.slot(), renderObject.vertexBuffer());
				if (RenderPassImpl.IS_DEVELOPMENT) {
					if (pass.indexBuffer == null) {
						throw new IllegalStateException("Missing index buffer");
					}

					if (pass.indexBuffer.isClosed()) {
						throw new IllegalStateException("Index buffer has been closed!");
					}

					if (pass.vertexBuffers[0] == null) {
						throw new IllegalStateException("Missing vertex buffer at slot 0");
					}

					if (pass.vertexBuffers[0].isClosed()) {
						throw new IllegalStateException("Vertex buffer at slot 0 has been closed!");
					}
				}

				Consumer<RenderPass.UniformUploader> consumer = renderObject.uniformUploaderConsumer();
				if (consumer != null) {
					consumer.accept((RenderPass.UniformUploader)(name, values) -> {
						GlUniform glUniform = pass.pipeline.program().getUniform(name);
						if (glUniform != null) {
							glUniform.set(values);
							glUniform.upload();
						}
					});
				}

				this.drawObjectWithRenderPass(pass, renderObject.firstIndex(), renderObject.indexCount(), indexType2, pass.pipeline);
			}
		}
	}

	protected void drawBoundObjectWithRenderPass(RenderPassImpl pass, int first, int count, @Nullable VertexFormat.IndexType indexType) {
		if (this.setupRenderPass(pass)) {
			if (RenderPassImpl.IS_DEVELOPMENT) {
				if (indexType != null) {
					if (pass.indexBuffer == null) {
						throw new IllegalStateException("Missing index buffer");
					}

					if (pass.indexBuffer.isClosed()) {
						throw new IllegalStateException("Index buffer has been closed!");
					}
				}

				if (pass.vertexBuffers[0] == null) {
					throw new IllegalStateException("Missing vertex buffer at slot 0");
				}

				if (pass.vertexBuffers[0].isClosed()) {
					throw new IllegalStateException("Vertex buffer at slot 0 has been closed!");
				}
			}

			this.drawObjectWithRenderPass(pass, first, count, indexType, pass.pipeline);
		}
	}

	private void drawObjectWithRenderPass(RenderPassImpl pass, int first, int count, @Nullable VertexFormat.IndexType indexType, CompiledShaderPipeline pipeline) {
		this.backend.getBufferManager().setupBuffer(pipeline.info().getVertexFormat(), (GlGpuBuffer)pass.vertexBuffers[0]);
		if (indexType != null) {
			GlStateManager._glBindBuffer(GlConst.GL_ELEMENT_ARRAY_BUFFER, ((GlGpuBuffer)pass.indexBuffer).id);
			GlStateManager._drawElements(GlConst.toGl(pipeline.info().getVertexFormatMode()), count, GlConst.toGl(indexType), (long)first * indexType.size);
		} else {
			GlStateManager._drawArrays(GlConst.toGl(pipeline.info().getVertexFormatMode()), first, count);
		}
	}

	private boolean setupRenderPass(RenderPassImpl pass) {
		if (RenderPassImpl.IS_DEVELOPMENT) {
			if (pass.pipeline == null) {
				throw new IllegalStateException("Can't draw without a render pipeline");
			}

			if (pass.pipeline.program() == ShaderProgram.INVALID) {
				throw new IllegalStateException("Pipeline contains invalid shader program");
			}

			for (RenderPipeline.UniformDescription uniformDescription : pass.pipeline.info().getUniforms()) {
				Object object = pass.simpleUniforms.get(uniformDescription.name());
				if (object == null && !ShaderProgram.PREDEFINED_UNIFORMS.contains(uniformDescription.name())) {
					throw new IllegalStateException("Missing uniform " + uniformDescription.name() + " (should be " + uniformDescription.type() + ")");
				}
			}

			for (String string : pass.pipeline.program().getSamplers()) {
				if (!pass.samplerUniforms.containsKey(string)) {
					throw new IllegalStateException("Missing sampler " + string);
				}

				if (((GpuTexture)pass.samplerUniforms.get(string)).isClosed()) {
					throw new IllegalStateException("Sampler " + string + " has been closed!");
				}
			}

			if (pass.pipeline.info().wantsDepthTexture() && !pass.hasDepth()) {
				LOGGER.warn("Render pipeline {} wants a depth texture but none was provided - this is probably a bug", pass.pipeline.info().getLocation());
			}
		} else if (pass.pipeline == null || pass.pipeline.program() == ShaderProgram.INVALID) {
			return false;
		}

		RenderPipeline renderPipeline = pass.pipeline.info();
		ShaderProgram shaderProgram = pass.pipeline.program();

		for (GlUniform glUniform : shaderProgram.getUniforms()) {
			if (pass.setSimpleUniforms.contains(glUniform.getName())) {
				Object object2 = pass.simpleUniforms.get(glUniform.getName());
				if (object2 instanceof int[]) {
					shaderProgram.getUniformOrDefault(glUniform.getName()).set((int[])object2);
				} else if (object2 instanceof float[]) {
					shaderProgram.getUniformOrDefault(glUniform.getName()).set((float[])object2);
				} else if (object2 != null) {
					throw new IllegalStateException("Unknown uniform type - expected " + glUniform.getType() + ", found " + object2);
				}
			}
		}

		pass.setSimpleUniforms.clear();
		this.setPipelineAndApplyState(renderPipeline);
		boolean bl = this.currentProgram != shaderProgram;
		if (bl) {
			GlStateManager._glUseProgram(shaderProgram.getGlRef());
			this.currentProgram = shaderProgram;
		}

		IntList intList = shaderProgram.getSamplerLocations();

		for (int i = 0; i < shaderProgram.getSamplers().size(); i++) {
			String string2 = (String)shaderProgram.getSamplers().get(i);
			GlTexture glTexture = (GlTexture)pass.samplerUniforms.get(string2);
			if (glTexture != null) {
				if (bl || pass.setSamplers.contains(string2)) {
					int j = intList.getInt(i);
					GlUniform.setUniform(j, i);
					GlStateManager._activeTexture(GlConst.GL_TEXTURE0 + i);
				}

				GlStateManager._bindTexture(glTexture.getGlId());
				glTexture.checkDirty();
			}
		}

		Window window = MinecraftClient.getInstance() == null ? null : MinecraftClient.getInstance().getWindow();
		shaderProgram.initializeUniforms(
			renderPipeline.getVertexFormatMode(),
			RenderSystem.getModelViewMatrix(),
			RenderSystem.getProjectionMatrix(),
			window == null ? 0.0F : window.getFramebufferWidth(),
			window == null ? 0.0F : window.getFramebufferHeight()
		);

		for (GlUniform glUniform2 : shaderProgram.getUniforms()) {
			glUniform2.upload();
		}

		if (pass.scissorState.isEnabled()) {
			GlStateManager._enableScissorTest();
			GlStateManager._scissorBox(pass.scissorState.getX(), pass.scissorState.getY(), pass.scissorState.getWidth(), pass.scissorState.getHeight());
		} else {
			GlStateManager._disableScissorTest();
		}

		return true;
	}

	private void setPipelineAndApplyState(RenderPipeline renderPipeline) {
		if (this.currentPipeline != renderPipeline) {
			this.currentPipeline = renderPipeline;
			if (renderPipeline.getDepthTestFunction() != DepthTestFunction.NO_DEPTH_TEST) {
				GlStateManager._enableDepthTest();
				GlStateManager._depthFunc(GlConst.toGl(renderPipeline.getDepthTestFunction()));
			} else {
				GlStateManager._disableDepthTest();
			}

			if (renderPipeline.isCull()) {
				GlStateManager._enableCull();
			} else {
				GlStateManager._disableCull();
			}

			if (renderPipeline.getBlendFunction().isPresent()) {
				GlStateManager._enableBlend();
				BlendFunction blendFunction = (BlendFunction)renderPipeline.getBlendFunction().get();
				GlStateManager._blendFuncSeparate(
					GlConst.toGl(blendFunction.sourceColor()),
					GlConst.toGl(blendFunction.destColor()),
					GlConst.toGl(blendFunction.sourceAlpha()),
					GlConst.toGl(blendFunction.destAlpha())
				);
			} else {
				GlStateManager._disableBlend();
			}

			GlStateManager._polygonMode(GlConst.GL_FRONT_AND_BACK, GlConst.toGl(renderPipeline.getPolygonMode()));
			GlStateManager._depthMask(renderPipeline.isWriteDepth());
			GlStateManager._colorMask(renderPipeline.isWriteColor(), renderPipeline.isWriteColor(), renderPipeline.isWriteColor(), renderPipeline.isWriteAlpha());
			if (renderPipeline.getDepthBiasConstant() == 0.0F && renderPipeline.getDepthBiasScaleFactor() == 0.0F) {
				GlStateManager._disablePolygonOffset();
			} else {
				GlStateManager._polygonOffset(renderPipeline.getDepthBiasScaleFactor(), renderPipeline.getDepthBiasConstant());
				GlStateManager._enablePolygonOffset();
			}

			switch (renderPipeline.getColorLogic()) {
				case NONE:
					GlStateManager._disableColorLogicOp();
					break;
				case OR_REVERSE:
					GlStateManager._enableColorLogicOp();
					GlStateManager._logicOp(GL11.GL_OR_REVERSE);
			}
		}
	}

	public void closePass() {
		this.renderPassOpen = false;
		GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, 0);
	}

	protected GlBackend getBackend() {
		return this.backend;
	}
}
