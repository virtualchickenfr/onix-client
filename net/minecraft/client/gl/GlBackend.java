package net.minecraft.client.gl;

import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.logging.LogUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.TextureAllocationException;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public class GlBackend implements GpuDevice {
	private static final Logger LOGGER = LogUtils.getLogger();
	protected static boolean allowGlArbVABinding = true;
	protected static boolean allowGlKhrDebug = true;
	protected static boolean allowExtDebugLabel = true;
	protected static boolean allowGlArbDebugOutput = true;
	protected static boolean allowGlArbDirectAccess = true;
	private final CommandEncoder commandEncoder;
	@Nullable
	private final GlDebug glDebug;
	private final DebugLabelManager debugLabelManager;
	private final int maxTextureSize;
	private final FramebufferManager framebufferManager;
	private final BiFunction<Identifier, ShaderType, String> defaultShaderSourceGetter;
	private final Map<RenderPipeline, CompiledShaderPipeline> pipelineCompileCache = new IdentityHashMap();
	private final Map<GlBackend.ShaderKey, CompiledShader> shaderCompileCache = new HashMap();
	private final BufferManager bufferManager;
	private final Set<String> usedGlCapabilities = new HashSet();

	public GlBackend(long contextId, int debugVerbosity, boolean sync, BiFunction<Identifier, ShaderType, String> shaderSourceGetter, boolean renderDebugLabels) {
		GLFW.glfwMakeContextCurrent(contextId);
		GLCapabilities gLCapabilities = GL.createCapabilities();
		int i = determineMaxTextureSize();
		GLFW.glfwSetWindowSizeLimits(contextId, -1, -1, i, i);
		this.glDebug = GlDebug.enableDebug(debugVerbosity, sync, this.usedGlCapabilities);
		this.debugLabelManager = DebugLabelManager.create(gLCapabilities, renderDebugLabels, this.usedGlCapabilities);
		this.bufferManager = BufferManager.create(gLCapabilities, this.debugLabelManager, this.usedGlCapabilities);
		this.framebufferManager = FramebufferManager.createFramebuffer(gLCapabilities, this.usedGlCapabilities);
		this.maxTextureSize = i;
		this.defaultShaderSourceGetter = shaderSourceGetter;
		this.commandEncoder = new GlResourceManager(this);
	}

	public DebugLabelManager getDebugLabelManager() {
		return this.debugLabelManager;
	}

	@Override
	public CommandEncoder createCommandEncoder() {
		return this.commandEncoder;
	}

	@Override
	public GpuTexture createTexture(@Nullable Supplier<String> supplier, TextureFormat textureFormat, int i, int j, int k) {
		return this.createTexture(this.debugLabelManager.isUsable() && supplier != null ? (String)supplier.get() : null, textureFormat, i, j, k);
	}

	@Override
	public GpuTexture createTexture(@Nullable String string, TextureFormat textureFormat, int i, int j, int k) {
		if (k < 1) {
			throw new IllegalArgumentException("mipLevels must be at least 1");
		} else {
			GlStateManager.clearGlErrors();
			int l = GlStateManager._genTexture();
			if (string == null) {
				string = String.valueOf(l);
			}

			GlStateManager._bindTexture(l);
			GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, k - 1);
			GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GL12.GL_TEXTURE_MIN_LOD, 0);
			GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LOD, k - 1);
			if (textureFormat.hasDepthAspect()) {
				GlStateManager._texParameter(GlConst.GL_TEXTURE_2D, GlConst.GL_TEXTURE_COMPARE_MODE, 0);
			}

			for (int m = 0; m < k; m++) {
				GlStateManager._texImage2D(
					GlConst.GL_TEXTURE_2D,
					m,
					GlConst.toGlInternalId(textureFormat),
					i >> m,
					j >> m,
					0,
					GlConst.toGlExternalId(textureFormat),
					GlConst.toGlType(textureFormat),
					null
				);
			}

			int m = GlStateManager._getError();
			if (m == GlConst.GL_OUT_OF_MEMORY) {
				throw new TextureAllocationException("Could not allocate texture of " + i + "x" + j + " for " + string);
			} else if (m != 0) {
				throw new IllegalStateException("OpenGL error " + m);
			} else {
				GlTexture glTexture = new GlTexture(string, textureFormat, i, j, k, l);
				this.debugLabelManager.labelGlTexture(glTexture);
				return glTexture;
			}
		}
	}

	@Override
	public GpuBuffer createBuffer(@Nullable Supplier<String> supplier, BufferType bufferType, BufferUsage bufferUsage, int i) {
		if (i <= 0) {
			throw new IllegalArgumentException("Buffer size must be greater than zero");
		} else {
			return new GlGpuBuffer(this.debugLabelManager, supplier, bufferType, bufferUsage, i, GlStateManager._glGenBuffers());
		}
	}

	@Override
	public GpuBuffer createBuffer(@Nullable Supplier<String> supplier, BufferType bufferType, BufferUsage bufferUsage, ByteBuffer byteBuffer) {
		if (!byteBuffer.hasRemaining()) {
			throw new IllegalArgumentException("Buffer source must not be empty");
		} else {
			GlGpuBuffer glGpuBuffer = new GlGpuBuffer(this.debugLabelManager, supplier, bufferType, bufferUsage, byteBuffer.remaining(), GlStateManager._glGenBuffers());
			this.commandEncoder.writeToBuffer(glGpuBuffer, byteBuffer, 0);
			return glGpuBuffer;
		}
	}

	@Override
	public String getImplementationInformation() {
		return GLFW.glfwGetCurrentContext() == 0L
			? "NO CONTEXT"
			: GlStateManager._getString(GL11.GL_RENDERER)
				+ " GL version "
				+ GlStateManager._getString(GL11.GL_VERSION)
				+ ", "
				+ GlStateManager._getString(GL11.GL_VENDOR);
	}

	@Override
	public List<String> getLastDebugMessages() {
		return this.glDebug == null ? Collections.emptyList() : this.glDebug.collectDebugMessages();
	}

	@Override
	public boolean isDebuggingEnabled() {
		return this.glDebug != null;
	}

	@Override
	public String getRenderer() {
		return GlStateManager._getString(GL11.GL_RENDERER);
	}

	@Override
	public String getVendor() {
		return GlStateManager._getString(GL11.GL_VENDOR);
	}

	@Override
	public String getBackendName() {
		return "OpenGL";
	}

	@Override
	public String getVersion() {
		return GlStateManager._getString(GL11.GL_VERSION);
	}

	private static int determineMaxTextureSize() {
		int i = GlStateManager._getInteger(GL11.GL_MAX_TEXTURE_SIZE);

		for (int j = Math.max(32768, i); j >= 1024; j >>= 1) {
			GlStateManager._texImage2D(GlConst.GL_PROXY_TEXTURE_2D, 0, GlConst.GL_RGBA, j, j, 0, GlConst.GL_RGBA, GlConst.GL_UNSIGNED_BYTE, null);
			int k = GlStateManager._getTexLevelParameter(GlConst.GL_PROXY_TEXTURE_2D, 0, GlConst.GL_TEXTURE_WIDTH);
			if (k != 0) {
				return j;
			}
		}

		int jx = Math.max(i, 1024);
		LOGGER.info("Failed to determine maximum texture size by probing, trying GL_MAX_TEXTURE_SIZE = {}", jx);
		return jx;
	}

	@Override
	public int getMaxTextureSize() {
		return this.maxTextureSize;
	}

	@Override
	public void clearPipelineCache() {
		for (CompiledShaderPipeline compiledShaderPipeline : this.pipelineCompileCache.values()) {
			if (compiledShaderPipeline.program() != ShaderProgram.INVALID) {
				compiledShaderPipeline.program().close();
			}
		}

		this.pipelineCompileCache.clear();

		for (CompiledShader compiledShader : this.shaderCompileCache.values()) {
			if (compiledShader != CompiledShader.INVALID_SHADER) {
				compiledShader.close();
			}
		}

		this.shaderCompileCache.clear();
	}

	@Override
	public List<String> getEnabledExtensions() {
		return new ArrayList(this.usedGlCapabilities);
	}

	@Override
	public void close() {
		this.clearPipelineCache();
	}

	public FramebufferManager getFramebufferManager() {
		return this.framebufferManager;
	}

	protected CompiledShaderPipeline compilePipelineCached(RenderPipeline pipeline) {
		return (CompiledShaderPipeline)this.pipelineCompileCache.computeIfAbsent(pipeline, p -> this.compileRenderPipeline(pipeline, this.defaultShaderSourceGetter));
	}

	protected CompiledShader compileShader(Identifier id, ShaderType type, Defines defines, BiFunction<Identifier, ShaderType, String> sourceRetriever) {
		GlBackend.ShaderKey shaderKey = new GlBackend.ShaderKey(id, type, defines);
		return (CompiledShader)this.shaderCompileCache.computeIfAbsent(shaderKey, key -> this.compileShader(shaderKey, sourceRetriever));
	}

	public CompiledShaderPipeline precompilePipeline(RenderPipeline renderPipeline, @Nullable BiFunction<Identifier, ShaderType, String> biFunction) {
		BiFunction<Identifier, ShaderType, String> biFunction2 = biFunction == null ? this.defaultShaderSourceGetter : biFunction;
		return (CompiledShaderPipeline)this.pipelineCompileCache
			.computeIfAbsent(renderPipeline, renderPipeline2 -> this.compileRenderPipeline(renderPipeline, biFunction2));
	}

	private CompiledShader compileShader(GlBackend.ShaderKey key, BiFunction<Identifier, ShaderType, String> sourceRetriever) {
		String string = (String)sourceRetriever.apply(key.id, key.type);
		if (string == null) {
			LOGGER.error("Couldn't find source for {} shader ({})", key.type, key.id);
			return CompiledShader.INVALID_SHADER;
		} else {
			String string2 = GlImportProcessor.addDefines(string, key.defines);
			int i = GlStateManager.glCreateShader(GlConst.toGl(key.type));
			GlStateManager.glShaderSource(i, string2);
			GlStateManager.glCompileShader(i);
			if (GlStateManager.glGetShaderi(i, GlConst.GL_COMPILE_STATUS) == 0) {
				String string3 = StringUtils.trim(GlStateManager.glGetShaderInfoLog(i, 32768));
				LOGGER.error("Couldn't compile {} shader ({}): {}", key.type.getName(), key.id, string3);
				return CompiledShader.INVALID_SHADER;
			} else {
				CompiledShader compiledShader = new CompiledShader(i, key.id, key.type);
				this.debugLabelManager.labelCompiledShader(compiledShader);
				return compiledShader;
			}
		}
	}

	private CompiledShaderPipeline compileRenderPipeline(RenderPipeline pipeline, BiFunction<Identifier, ShaderType, String> sourceRetriever) {
		CompiledShader compiledShader = this.compileShader(pipeline.getVertexShader(), ShaderType.VERTEX, pipeline.getShaderDefines(), sourceRetriever);
		CompiledShader compiledShader2 = this.compileShader(pipeline.getFragmentShader(), ShaderType.FRAGMENT, pipeline.getShaderDefines(), sourceRetriever);
		if (compiledShader == CompiledShader.INVALID_SHADER) {
			LOGGER.error("Couldn't compile pipeline {}: vertex shader {} was invalid", pipeline.getLocation(), pipeline.getVertexShader());
			return new CompiledShaderPipeline(pipeline, ShaderProgram.INVALID);
		} else if (compiledShader2 == CompiledShader.INVALID_SHADER) {
			LOGGER.error("Couldn't compile pipeline {}: fragment shader {} was invalid", pipeline.getLocation(), pipeline.getFragmentShader());
			return new CompiledShaderPipeline(pipeline, ShaderProgram.INVALID);
		} else {
			ShaderProgram shaderProgram;
			try {
				shaderProgram = ShaderProgram.create(compiledShader, compiledShader2, pipeline.getVertexFormat(), pipeline.getLocation().toString());
			} catch (ShaderLoader.LoadException var7) {
				LOGGER.error("Couldn't compile program for pipeline {}: {}", pipeline.getLocation(), var7);
				return new CompiledShaderPipeline(pipeline, ShaderProgram.INVALID);
			}

			shaderProgram.set(pipeline.getUniforms(), pipeline.getSamplers());
			this.debugLabelManager.labelShaderProgram(shaderProgram);
			return new CompiledShaderPipeline(pipeline, shaderProgram);
		}
	}

	public BufferManager getBufferManager() {
		return this.bufferManager;
	}

	@Environment(EnvType.CLIENT)
	record ShaderKey(Identifier id, ShaderType type, Defines defines) {

		public String toString() {
			String string = this.id + " (" + this.type + ")";
			return !this.defines.isEmpty() ? string + " with " + this.defines : string;
		}
	}
}
