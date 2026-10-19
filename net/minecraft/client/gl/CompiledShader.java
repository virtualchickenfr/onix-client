package net.minecraft.client.gl;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import org.apache.commons.lang3.StringUtils;

@Environment(EnvType.CLIENT)
public class CompiledShader implements AutoCloseable {
	private static final int CLOSED = -1;
	public static final CompiledShader INVALID_SHADER = new CompiledShader(-1, Identifier.ofVanilla("invalid"), ShaderType.VERTEX);
	private final Identifier id;
	private int handle;
	private final ShaderType shaderType;

	public CompiledShader(int handle, Identifier id, ShaderType shaderType) {
		this.id = id;
		this.handle = handle;
		this.shaderType = shaderType;
	}

	public static CompiledShader compile(Identifier id, ShaderType type, String source) throws ShaderLoader.LoadException {
		RenderSystem.assertOnRenderThread();
		int i = GlStateManager.glCreateShader(GlConst.toGl(type));
		GlStateManager.glShaderSource(i, source);
		GlStateManager.glCompileShader(i);
		if (GlStateManager.glGetShaderi(i, GlConst.GL_COMPILE_STATUS) == 0) {
			String string = StringUtils.trim(GlStateManager.glGetShaderInfoLog(i, 32768));
			throw new ShaderLoader.LoadException("Couldn't compile " + type.getName() + " shader (" + id + ") : " + string);
		} else {
			return new CompiledShader(i, id, type);
		}
	}

	public void close() {
		if (this.handle == -1) {
			throw new IllegalStateException("Already closed");
		} else {
			RenderSystem.assertOnRenderThread();
			GlStateManager.glDeleteShader(this.handle);
			this.handle = -1;
		}
	}

	public Identifier getId() {
		return this.id;
	}

	public int getHandle() {
		return this.handle;
	}

	public String getDebugLabel() {
		return this.shaderType.idConverter().toResourcePath(this.id).toString();
	}
}
