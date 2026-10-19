package net.minecraft.client.gl;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Fog;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

/**
 * Represents a shader program. Also known as a program object that can be
 * created with {@code glCreateProgram}.
 * 
 * <p><strong>Warning:</strong> This class is referred to as a shader in
 * strings. However, this does NOT represent a shader object that can be
 * created with {@code glCreateShader}. {@link CompiledShader} is what
 * represents a shader object.
 * 
 * @see <a href="https://www.khronos.org/opengl/wiki/GLSL_Object#Program_objects">
 * GLSL Object - OpenGL Wiki (Program objects)</a>
 */
@Environment(EnvType.CLIENT)
public class ShaderProgram implements AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	public static Set<String> PREDEFINED_UNIFORMS = Sets.<String>newHashSet(
		"ModelViewMat",
		"ProjMat",
		"TextureMat",
		"ScreenSize",
		"ColorModulator",
		"Light0_Direction",
		"Light1_Direction",
		"GlintAlpha",
		"FogStart",
		"FogEnd",
		"FogColor",
		"FogShape",
		"LineWidth",
		"GameTime",
		"ModelOffset"
	);
	public static ShaderProgram INVALID = new ShaderProgram(-1, "invalid");
	private static final Uniform DEFAULT_UNIFORM = new Uniform();
	private final List<String> samplers = new ArrayList();
	private final Object2ObjectMap<String, GpuTexture> samplerTextures = new Object2ObjectOpenHashMap<>();
	private final IntList samplerLocations = new IntArrayList();
	private final List<GlUniform> uniforms = new ArrayList();
	private final Map<String, GlUniform> uniformsByName = new HashMap();
	private final int glRef;
	private final String debugLabel;
	@Nullable
	public GlUniform modelViewMat;
	@Nullable
	public GlUniform projectionMat;
	@Nullable
	public GlUniform textureMat;
	@Nullable
	public GlUniform screenSize;
	@Nullable
	public GlUniform colorModulator;
	@Nullable
	public GlUniform light0Direction;
	@Nullable
	public GlUniform light1Direction;
	@Nullable
	public GlUniform glintAlpha;
	@Nullable
	public GlUniform fogStart;
	@Nullable
	public GlUniform fogEnd;
	@Nullable
	public GlUniform fogColor;
	@Nullable
	public GlUniform fogShape;
	@Nullable
	public GlUniform lineWidth;
	@Nullable
	public GlUniform gameTime;
	@Nullable
	public GlUniform modelOffset;

	private ShaderProgram(int glRef, String debugLabel) {
		this.glRef = glRef;
		this.debugLabel = debugLabel;
	}

	public static ShaderProgram create(CompiledShader vertexShader, CompiledShader fragmentShader, VertexFormat format, String name) throws ShaderLoader.LoadException {
		int i = GlStateManager.glCreateProgram();
		if (i <= 0) {
			throw new ShaderLoader.LoadException("Could not create shader program (returned program ID " + i + ")");
		} else {
			int j = 0;

			for (String string : format.getElementAttributeNames()) {
				GlStateManager._glBindAttribLocation(i, j, string);
				j++;
			}

			GlStateManager.glAttachShader(i, vertexShader.getHandle());
			GlStateManager.glAttachShader(i, fragmentShader.getHandle());
			GlStateManager.glLinkProgram(i);
			int k = GlStateManager.glGetProgrami(i, GlConst.GL_LINK_STATUS);
			if (k == 0) {
				String string = GlStateManager.glGetProgramInfoLog(i, 32768);
				throw new ShaderLoader.LoadException(
					"Error encountered when linking program containing VS " + vertexShader.getId() + " and FS " + fragmentShader.getId() + ". Log output: " + string
				);
			} else {
				return new ShaderProgram(i, name);
			}
		}
	}

	public void set(List<RenderPipeline.UniformDescription> uniforms, List<String> samplers) {
		RenderSystem.assertOnRenderThread();

		for (RenderPipeline.UniformDescription uniformDescription : uniforms) {
			String string = uniformDescription.name();
			int i = GlUniform.getUniformLocation(this.glRef, string);
			if (i != -1) {
				GlUniform glUniform = this.createUniform(uniformDescription);
				glUniform.setLocation(i);
				this.uniforms.add(glUniform);
				this.uniformsByName.put(string, glUniform);
			}
		}

		for (String string2 : samplers) {
			int j = GlUniform.getUniformLocation(this.glRef, string2);
			if (j == -1) {
				LOGGER.warn("{} shader program does not use sampler {} defined in the pipeline. This might be a bug.", this.debugLabel, string2);
			} else {
				this.samplers.add(string2);
				this.samplerLocations.add(j);
			}
		}

		int k = GlStateManager.glGetProgrami(this.glRef, GL20.GL_ACTIVE_UNIFORMS);

		try (MemoryStack memoryStack = MemoryStack.stackPush()) {
			IntBuffer intBuffer = memoryStack.mallocInt(1);
			IntBuffer intBuffer2 = memoryStack.mallocInt(1);

			for (int l = 0; l < k; l++) {
				String string3 = GL20.glGetActiveUniform(this.glRef, l, intBuffer, intBuffer2);
				UniformType uniformType = getType(intBuffer2.get(0));
				if (!this.uniformsByName.containsKey(string3) && !samplers.contains(string3)) {
					if (uniformType != null) {
						LOGGER.info("Found unknown but potentially supported uniform {} in {}", string3, this.debugLabel);
						GlUniform glUniform2 = new GlUniform(string3, uniformType);
						glUniform2.setLocation(l);
						this.uniforms.add(glUniform2);
						this.uniformsByName.put(string3, glUniform2);
					} else {
						LOGGER.warn("Found unknown and unsupported uniform {} in {}", string3, this.debugLabel);
					}
				}
			}
		}

		this.modelViewMat = this.getUniform("ModelViewMat");
		this.projectionMat = this.getUniform("ProjMat");
		this.textureMat = this.getUniform("TextureMat");
		this.screenSize = this.getUniform("ScreenSize");
		this.colorModulator = this.getUniform("ColorModulator");
		this.light0Direction = this.getUniform("Light0_Direction");
		this.light1Direction = this.getUniform("Light1_Direction");
		this.glintAlpha = this.getUniform("GlintAlpha");
		this.fogStart = this.getUniform("FogStart");
		this.fogEnd = this.getUniform("FogEnd");
		this.fogColor = this.getUniform("FogColor");
		this.fogShape = this.getUniform("FogShape");
		this.lineWidth = this.getUniform("LineWidth");
		this.gameTime = this.getUniform("GameTime");
		this.modelOffset = this.getUniform("ModelOffset");
	}

	private GlUniform createUniform(RenderPipeline.UniformDescription description) {
		return new GlUniform(description.name(), description.type());
	}

	public void close() {
		this.uniforms.forEach(GlUniform::close);
		GlStateManager.glDeleteProgram(this.glRef);
	}

	public void unbind() {
		RenderSystem.assertOnRenderThread();
		GlStateManager._glUseProgram(0);
		int i = GlStateManager._getActiveTexture();

		for (int j = 0; j < this.samplerLocations.size(); j++) {
			String string = (String)this.samplers.get(j);
			if (!this.samplerTextures.containsKey(string)) {
				GlStateManager._activeTexture(GlConst.GL_TEXTURE0 + j);
				GlStateManager._bindTexture(0);
			}
		}

		GlStateManager._activeTexture(i);
	}

	@Nullable
	public GlUniform getUniform(String name) {
		RenderSystem.assertOnRenderThread();
		return (GlUniform)this.uniformsByName.get(name);
	}

	public Uniform getUniformOrDefault(String name) {
		GlUniform glUniform = this.getUniform(name);
		return (Uniform)(glUniform == null ? DEFAULT_UNIFORM : glUniform);
	}

	public void addSamplerTexture(String name, @Nullable GpuTexture texture) {
		this.samplerTextures.put(name, texture);
	}

	public void initializeUniforms(VertexFormat.DrawMode drawMode, Matrix4f viewMatrix, Matrix4f projectionMatrix, float screenWidth, float screenHeight) {
		for (int i = 0; i < 12; i++) {
			GpuTexture gpuTexture = RenderSystem.getShaderTexture(i);
			this.addSamplerTexture("Sampler" + i, gpuTexture);
		}

		if (this.modelViewMat != null) {
			this.modelViewMat.set(viewMatrix);
		}

		if (this.projectionMat != null) {
			this.projectionMat.set(projectionMatrix);
		}

		if (this.colorModulator != null) {
			this.colorModulator.set(RenderSystem.getShaderColor());
		}

		if (this.glintAlpha != null) {
			this.glintAlpha.set(RenderSystem.getShaderGlintAlpha());
		}

		Fog fog = RenderSystem.getShaderFog();
		if (this.fogStart != null) {
			this.fogStart.set(fog.start());
		}

		if (this.fogEnd != null) {
			this.fogEnd.set(fog.end());
		}

		if (this.fogColor != null) {
			this.fogColor.setAndFlip(fog.red(), fog.green(), fog.blue(), fog.alpha());
		}

		if (this.fogShape != null) {
			this.fogShape.set(fog.shape().getId());
		}

		if (this.textureMat != null) {
			this.textureMat.set(RenderSystem.getTextureMatrix());
		}

		if (this.gameTime != null) {
			this.gameTime.set(RenderSystem.getShaderGameTime());
		}

		if (this.modelOffset != null) {
			this.modelOffset.set(RenderSystem.getModelOffset());
		}

		if (this.screenSize != null) {
			this.screenSize.set(screenWidth, screenHeight);
		}

		if (this.lineWidth != null && (drawMode == VertexFormat.DrawMode.LINES || drawMode == VertexFormat.DrawMode.LINE_STRIP)) {
			this.lineWidth.set(RenderSystem.getShaderLineWidth());
		}

		Vector3f[] vector3fs = RenderSystem.getShaderLights();
		if (this.light0Direction != null) {
			this.light0Direction.set(vector3fs[0]);
		}

		if (this.light1Direction != null) {
			this.light1Direction.set(vector3fs[1]);
		}
	}

	@VisibleForTesting
	public int getGlRef() {
		return this.glRef;
	}

	public String toString() {
		return this.debugLabel;
	}

	public String getDebugLabel() {
		return this.debugLabel;
	}

	public IntList getSamplerLocations() {
		return this.samplerLocations;
	}

	public List<String> getSamplers() {
		return this.samplers;
	}

	public List<GlUniform> getUniforms() {
		return this.uniforms;
	}

	@Nullable
	private static UniformType getType(int id) {
		return switch (id) {
			case 5124 -> UniformType.INT;
			case 5126 -> UniformType.FLOAT;
			case 35664 -> UniformType.VEC2;
			case 35665 -> UniformType.VEC3;
			case 35666 -> UniformType.VEC4;
			case 35668 -> UniformType.IVEC3;
			case 35676 -> UniformType.MATRIX4X4;
			default -> null;
		};
	}
}
