package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.StringUtils;

@Environment(EnvType.CLIENT)
public class GlShaderModule implements AutoCloseable {
	private static final int NOT_ALLOCATED = -1;
	public static final GlShaderModule INVALID_SHADER = new GlShaderModule(-1, ResourceLocation.withDefaultNamespace("invalid"), ShaderType.VERTEX);
	private final ResourceLocation id;
	private int shaderId;
	private final ShaderType type;

	public GlShaderModule(int i, ResourceLocation resourceLocation, ShaderType shaderType) {
		this.id = resourceLocation;
		this.shaderId = i;
		this.type = shaderType;
	}

	public static GlShaderModule compile(ResourceLocation resourceLocation, ShaderType shaderType, String string) throws ShaderManager.CompilationException {
		RenderSystem.assertOnRenderThread();
		int i = GlStateManager.glCreateShader(GlConst.toGl(shaderType));
		GlStateManager.glShaderSource(i, string);
		GlStateManager.glCompileShader(i);
		if (GlStateManager.glGetShaderi(i, 35713) == 0) {
			String string2 = StringUtils.trim(GlStateManager.glGetShaderInfoLog(i, 32768));
			throw new ShaderManager.CompilationException("Couldn't compile " + shaderType.getName() + " shader (" + resourceLocation + ") : " + string2);
		} else {
			return new GlShaderModule(i, resourceLocation, shaderType);
		}
	}

	@Override
	public void close() {
		if (this.shaderId == -1) {
			throw new IllegalStateException("Already closed");
		}

		RenderSystem.assertOnRenderThread();
		GlStateManager.glDeleteShader(this.shaderId);
		this.shaderId = -1;
	}

	public ResourceLocation getId() {
		return this.id;
	}

	public int getShaderId() {
		return this.shaderId;
	}

	public String getDebugLabel() {
		return this.type.idConverter().idToFile(this.id).toString();
	}
}
