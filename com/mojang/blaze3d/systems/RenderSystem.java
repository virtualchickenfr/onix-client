package com.mojang.blaze3d.systems;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuFence;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.tracy.TracyFrameCapturer;
import net.minecraft.util.Identifier;
import net.minecraft.util.TimeSupplier;
import net.minecraft.util.Util;
import net.minecraft.util.annotation.DeobfuscateClass;
import net.minecraft.util.collection.ArrayListDeque;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallbackI;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
@DeobfuscateClass
public class RenderSystem {
	public static final ScissorState SCISSOR_STATE = new ScissorState();
	static final Logger LOGGER = LogUtils.getLogger();
	public static final int MINIMUM_ATLAS_TEXTURE_SIZE = 1024;
	@Nullable
	private static Thread renderThread;
	@Nullable
	private static GpuDevice DEVICE;
	private static double lastDrawTime = Double.MIN_VALUE;
	private static final RenderSystem.ShapeIndexBuffer sharedSequential = new RenderSystem.ShapeIndexBuffer(1, 1, IntConsumer::accept);
	private static final RenderSystem.ShapeIndexBuffer sharedSequentialQuad = new RenderSystem.ShapeIndexBuffer(4, 6, (indexConsumer, firstVertexIndex) -> {
		indexConsumer.accept(firstVertexIndex);
		indexConsumer.accept(firstVertexIndex + 1);
		indexConsumer.accept(firstVertexIndex + 2);
		indexConsumer.accept(firstVertexIndex + 2);
		indexConsumer.accept(firstVertexIndex + 3);
		indexConsumer.accept(firstVertexIndex);
	});
	private static final RenderSystem.ShapeIndexBuffer sharedSequentialLines = new RenderSystem.ShapeIndexBuffer(4, 6, (indexConsumer, firstVertexIndex) -> {
		indexConsumer.accept(firstVertexIndex);
		indexConsumer.accept(firstVertexIndex + 1);
		indexConsumer.accept(firstVertexIndex + 2);
		indexConsumer.accept(firstVertexIndex + 3);
		indexConsumer.accept(firstVertexIndex + 2);
		indexConsumer.accept(firstVertexIndex + 1);
	});
	private static Matrix4f projectionMatrix = new Matrix4f();
	private static Matrix4f savedProjectionMatrix = new Matrix4f();
	private static ProjectionType projectionType = ProjectionType.PERSPECTIVE;
	private static ProjectionType savedProjectionType = ProjectionType.PERSPECTIVE;
	private static final Matrix4fStack modelViewStack = new Matrix4fStack(16);
	private static Matrix4f textureMatrix = new Matrix4f();
	public static final int TEXTURE_COUNT = 12;
	private static final GpuTexture[] shaderTextures = new GpuTexture[12];
	private static final float[] shaderColor = new float[]{1.0F, 1.0F, 1.0F, 1.0F};
	private static float shaderGlintAlpha = 1.0F;
	private static Fog shaderFog = Fog.DUMMY;
	private static final Vector3f[] shaderLightDirections = new Vector3f[2];
	private static float shaderGameTime;
	private static final Vector3f modelOffset = new Vector3f();
	private static float shaderLineWidth = 1.0F;
	private static String apiDescription = "Unknown";
	private static final AtomicLong pollEventsWaitStart = new AtomicLong();
	private static final AtomicBoolean pollingEvents = new AtomicBoolean(false);
	@Nullable
	private static GpuBuffer QUAD_VERTEX_BUFFER;
	private static final ArrayListDeque<RenderSystem.Task> PENDING_FENCES = new ArrayListDeque<>();

	public static void initRenderThread() {
		if (renderThread != null) {
			throw new IllegalStateException("Could not initialize render thread");
		} else {
			renderThread = Thread.currentThread();
		}
	}

	public static boolean isOnRenderThread() {
		return Thread.currentThread() == renderThread;
	}

	public static void assertOnRenderThread() {
		if (!isOnRenderThread()) {
			throw constructThreadException();
		}
	}

	private static IllegalStateException constructThreadException() {
		return new IllegalStateException("Rendersystem called from wrong thread");
	}

	private static void pollEvents() {
		pollEventsWaitStart.set(Util.getMeasuringTimeMs());
		pollingEvents.set(true);
		GLFW.glfwPollEvents();
		pollingEvents.set(false);
	}

	public static boolean isFrozenAtPollEvents() {
		return pollingEvents.get() && Util.getMeasuringTimeMs() - pollEventsWaitStart.get() > 200L;
	}

	public static void flipFrame(long window, @Nullable TracyFrameCapturer capturer) {
		pollEvents();
		Tessellator.getInstance().clear();
		GLFW.glfwSwapBuffers(window);
		if (capturer != null) {
			capturer.markFrame();
		}

		pollEvents();
	}

	public static void limitDisplayFPS(int fps) {
		double d = lastDrawTime + 1.0 / fps;

		double e;
		for (e = GLFW.glfwGetTime(); e < d; e = GLFW.glfwGetTime()) {
			GLFW.glfwWaitEventsTimeout(d - e);
		}

		lastDrawTime = e;
	}

	public static void enableScissor(int x, int y, int width, int height) {
		SCISSOR_STATE.enable(x, y, width, height);
	}

	public static void disableScissor() {
		SCISSOR_STATE.disable();
	}

	public static void setShaderFog(Fog shaderFog) {
		assertOnRenderThread();
		RenderSystem.shaderFog = shaderFog;
	}

	public static Fog getShaderFog() {
		assertOnRenderThread();
		return shaderFog;
	}

	public static void setShaderGlintAlpha(double shaderGlintAlpha) {
		setShaderGlintAlpha((float)shaderGlintAlpha);
	}

	public static void setShaderGlintAlpha(float shaderGlintAlpha) {
		assertOnRenderThread();
		RenderSystem.shaderGlintAlpha = shaderGlintAlpha;
	}

	public static float getShaderGlintAlpha() {
		assertOnRenderThread();
		return shaderGlintAlpha;
	}

	public static void setShaderLights(Vector3f vector3f, Vector3f vector3f2) {
		assertOnRenderThread();
		shaderLightDirections[0] = vector3f;
		shaderLightDirections[1] = vector3f2;
	}

	public static Vector3f[] getShaderLights() {
		return shaderLightDirections;
	}

	public static void setShaderColor(float red, float green, float blue, float alpha) {
		assertOnRenderThread();
		shaderColor[0] = red;
		shaderColor[1] = green;
		shaderColor[2] = blue;
		shaderColor[3] = alpha;
	}

	public static float[] getShaderColor() {
		assertOnRenderThread();
		return shaderColor;
	}

	public static void lineWidth(float width) {
		assertOnRenderThread();
		shaderLineWidth = width;
	}

	public static float getShaderLineWidth() {
		assertOnRenderThread();
		return shaderLineWidth;
	}

	public static String getBackendDescription() {
		return String.format(Locale.ROOT, "LWJGL version %s", GLX._getLWJGLVersion());
	}

	public static String getApiDescription() {
		return apiDescription;
	}

	public static TimeSupplier.Nanoseconds initBackendSystem() {
		return GLX._initGlfw()::getAsLong;
	}

	public static void initRenderer(
		long windowHandle, int debugVerbosity, boolean sync, BiFunction<Identifier, ShaderType, String> shaderSourceGetter, boolean renderDebugLabels
	) {
		DEVICE = new GlBackend(windowHandle, debugVerbosity, sync, shaderSourceGetter, renderDebugLabels);
		apiDescription = getDevice().getImplementationInformation();

		try (BufferAllocator bufferAllocator = new BufferAllocator(VertexFormats.POSITION.getVertexSize() * 4)) {
			BufferBuilder bufferBuilder = new BufferBuilder(bufferAllocator, VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
			bufferBuilder.vertex(0.0F, 0.0F, 0.0F);
			bufferBuilder.vertex(1.0F, 0.0F, 0.0F);
			bufferBuilder.vertex(1.0F, 1.0F, 0.0F);
			bufferBuilder.vertex(0.0F, 1.0F, 0.0F);

			try (BuiltBuffer builtBuffer = bufferBuilder.end()) {
				QUAD_VERTEX_BUFFER = getDevice().createBuffer(() -> "Quad", BufferType.VERTICES, BufferUsage.STATIC_WRITE, builtBuffer.getBuffer());
			}
		}
	}

	public static void setErrorCallback(GLFWErrorCallbackI callback) {
		GLX._setGlfwErrorCallback(callback);
	}

	public static void setupDefaultState() {
		projectionMatrix.identity();
		savedProjectionMatrix.identity();
		modelViewStack.clear();
		textureMatrix.identity();
	}

	public static void setupOverlayColor(@Nullable GpuTexture texture) {
		assertOnRenderThread();
		setShaderTexture(1, texture);
	}

	public static void teardownOverlayColor() {
		assertOnRenderThread();
		setShaderTexture(1, null);
	}

	public static void setupLevelDiffuseLighting(Vector3f vector3f, Vector3f vector3f2) {
		assertOnRenderThread();
		setShaderLights(vector3f, vector3f2);
	}

	public static void setupGuiFlatDiffuseLighting(Vector3f vector3f, Vector3f vector3f2) {
		assertOnRenderThread();
		Matrix4f matrix4f = new Matrix4f().rotationY((float) (-Math.PI / 8)).rotateX((float) (Math.PI * 3.0 / 4.0));
		setShaderLights(matrix4f.transformDirection(vector3f, new Vector3f()), matrix4f.transformDirection(vector3f2, new Vector3f()));
	}

	public static void setupGui3DDiffuseLighting(Vector3f vector3f, Vector3f vector3f2) {
		assertOnRenderThread();
		Matrix4f matrix4f = new Matrix4f()
			.scaling(1.0F, -1.0F, 1.0F)
			.rotateYXZ(1.0821041F, 3.2375858F, 0.0F)
			.rotateYXZ((float) (-Math.PI / 8), (float) (Math.PI * 3.0 / 4.0), 0.0F);
		setShaderLights(matrix4f.transformDirection(vector3f, new Vector3f()), matrix4f.transformDirection(vector3f2, new Vector3f()));
	}

	public static void setShaderTexture(int index, @Nullable GpuTexture texture) {
		assertOnRenderThread();
		if (index >= 0 && index < shaderTextures.length) {
			shaderTextures[index] = texture;
		}
	}

	@Nullable
	public static GpuTexture getShaderTexture(int index) {
		assertOnRenderThread();
		return index >= 0 && index < shaderTextures.length ? shaderTextures[index] : null;
	}

	public static void setProjectionMatrix(Matrix4f projectionMatrix, ProjectionType projectionType) {
		assertOnRenderThread();
		RenderSystem.projectionMatrix = new Matrix4f(projectionMatrix);
		RenderSystem.projectionType = projectionType;
	}

	public static void setTextureMatrix(Matrix4f textureMatrix) {
		assertOnRenderThread();
		RenderSystem.textureMatrix = new Matrix4f(textureMatrix);
	}

	public static void resetTextureMatrix() {
		assertOnRenderThread();
		textureMatrix.identity();
	}

	public static void backupProjectionMatrix() {
		assertOnRenderThread();
		savedProjectionMatrix = projectionMatrix;
		savedProjectionType = projectionType;
	}

	public static void restoreProjectionMatrix() {
		assertOnRenderThread();
		projectionMatrix = savedProjectionMatrix;
		projectionType = savedProjectionType;
	}

	public static Matrix4f getProjectionMatrix() {
		assertOnRenderThread();
		return projectionMatrix;
	}

	public static Matrix4f getModelViewMatrix() {
		assertOnRenderThread();
		return modelViewStack;
	}

	public static Matrix4fStack getModelViewStack() {
		assertOnRenderThread();
		return modelViewStack;
	}

	public static Matrix4f getTextureMatrix() {
		assertOnRenderThread();
		return textureMatrix;
	}

	public static RenderSystem.ShapeIndexBuffer getSequentialBuffer(VertexFormat.DrawMode drawMode) {
		assertOnRenderThread();

		return switch (drawMode) {
			case QUADS -> sharedSequentialQuad;
			case LINES -> sharedSequentialLines;
			default -> sharedSequential;
		};
	}

	public static void setShaderGameTime(long time, float tickProgress) {
		assertOnRenderThread();
		shaderGameTime = ((float)(time % 24000L) + tickProgress) / 24000.0F;
	}

	public static float getShaderGameTime() {
		assertOnRenderThread();
		return shaderGameTime;
	}

	public static ProjectionType getProjectionType() {
		assertOnRenderThread();
		return projectionType;
	}

	public static GpuBuffer getQuadVertexBuffer() {
		if (QUAD_VERTEX_BUFFER == null) {
			throw new IllegalStateException("Can't getQuadVertexBuffer() before renderer was initialized");
		} else {
			return QUAD_VERTEX_BUFFER;
		}
	}

	public static void setModelOffset(float offsetX, float offsetY, float offsetZ) {
		assertOnRenderThread();
		modelOffset.set(offsetX, offsetY, offsetZ);
	}

	public static void resetModelOffset() {
		assertOnRenderThread();
		modelOffset.set(0.0F, 0.0F, 0.0F);
	}

	public static Vector3f getModelOffset() {
		assertOnRenderThread();
		return modelOffset;
	}

	public static void queueFencedTask(Runnable task) {
		PENDING_FENCES.addLast(new RenderSystem.Task(task, new GpuFence()));
	}

	public static void executePendingTasks() {
		for (RenderSystem.Task task = PENDING_FENCES.peekFirst(); task != null; task = PENDING_FENCES.peekFirst()) {
			if (!task.fence.awaitCompletion(0L)) {
				return;
			}

			try {
				task.callback.run();
			} finally {
				task.fence.close();
			}

			PENDING_FENCES.removeFirst();
		}
	}

	public static GpuDevice getDevice() {
		if (DEVICE == null) {
			throw new IllegalStateException("Can't getDevice() before it was initialized");
		} else {
			return DEVICE;
		}
	}

	@Nullable
	public static GpuDevice tryGetDevice() {
		return DEVICE;
	}

	/**
	 * An index buffer that holds a pre-made indices for a specific shape. If
	 * this buffer is not large enough for the required number of indices when
	 * this buffer is bound, it automatically grows and fills indices using a
	 * given {@code triangulator}.
	 */
	@Environment(EnvType.CLIENT)
	public static final class ShapeIndexBuffer {
		private final int vertexCountInShape;
		private final int vertexCountInTriangulated;
		private final RenderSystem.ShapeIndexBuffer.Triangulator triangulator;
		@Nullable
		private GpuBuffer indexBuffer;
		private VertexFormat.IndexType indexType = VertexFormat.IndexType.SHORT;
		private int size;

		/**
		 * @param vertexCountInShape the number of vertices in a shape
		 * @param vertexCountInTriangulated the number of vertices in the triangles decomposed from the shape
		 * @param triangulator a function that decomposes a shape into triangles
		 */
		ShapeIndexBuffer(int vertexCountInShape, int vertexCountInTriangulated, RenderSystem.ShapeIndexBuffer.Triangulator triangulator) {
			this.vertexCountInShape = vertexCountInShape;
			this.vertexCountInTriangulated = vertexCountInTriangulated;
			this.triangulator = triangulator;
		}

		public boolean isLargeEnough(int requiredSize) {
			return requiredSize <= this.size;
		}

		public GpuBuffer getIndexBuffer(int requiredSize) {
			this.grow(requiredSize);
			return this.indexBuffer;
		}

		private void grow(int requiredSize) {
			if (!this.isLargeEnough(requiredSize)) {
				requiredSize = MathHelper.roundUpToMultiple(requiredSize * 2, this.vertexCountInTriangulated);
				RenderSystem.LOGGER.debug("Growing IndexBuffer: Old limit {}, new limit {}.", this.size, requiredSize);
				int i = requiredSize / this.vertexCountInTriangulated;
				int j = i * this.vertexCountInShape;
				VertexFormat.IndexType indexType = VertexFormat.IndexType.smallestFor(j);
				int k = MathHelper.roundUpToMultiple(requiredSize * indexType.size, 4);
				ByteBuffer byteBuffer = MemoryUtil.memAlloc(k);

				try {
					this.indexType = indexType;
					it.unimi.dsi.fastutil.ints.IntConsumer intConsumer = this.getIndexConsumer(byteBuffer);

					for (int l = 0; l < requiredSize; l += this.vertexCountInTriangulated) {
						this.triangulator.accept(intConsumer, l * this.vertexCountInShape / this.vertexCountInTriangulated);
					}

					byteBuffer.flip();
					if (this.indexBuffer != null) {
						this.indexBuffer.close();
					}

					this.indexBuffer = RenderSystem.getDevice().createBuffer(() -> "Auto Storage index buffer", BufferType.INDICES, BufferUsage.DYNAMIC_WRITE, byteBuffer);
				} finally {
					MemoryUtil.memFree(byteBuffer);
				}

				this.size = requiredSize;
			}
		}

		private it.unimi.dsi.fastutil.ints.IntConsumer getIndexConsumer(ByteBuffer indexBuffer) {
			switch (this.indexType) {
				case SHORT:
					return index -> indexBuffer.putShort((short)index);
				case INT:
				default:
					return indexBuffer::putInt;
			}
		}

		public VertexFormat.IndexType getIndexType() {
			return this.indexType;
		}

		/**
		 * A functional interface that decomposes a shape into triangles.
		 * 
		 * <p>The input shape is represented by the index of the first vertex in
		 * the shape. An output triangle is represented by the indices of the
		 * vertices in the triangle.
		 * 
		 * @see <a href="https://en.wikipedia.org/wiki/Polygon_triangulation">Polygon triangulation - Wikipedia</a>
		 */
		@Environment(EnvType.CLIENT)
		interface Triangulator {
			/**
			 * Decomposes a shape into triangles.
			 * 
			 * @param indexConsumer the consumer that accepts triangles
			 * @param firstVertexIndex the index of the first vertex in the input shape
			 */
			void accept(it.unimi.dsi.fastutil.ints.IntConsumer indexConsumer, int firstVertexIndex);
		}
	}

	@Environment(EnvType.CLIENT)
	record Task(Runnable callback, GpuFence fence) {
	}
}
