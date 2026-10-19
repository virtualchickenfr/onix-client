package net.minecraft.client.gl;

import com.mojang.logging.LogUtils;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.util.StringHelper;
import org.lwjgl.opengl.EXTDebugLabel;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.KHRDebug;
import org.slf4j.Logger;

@Environment(EnvType.CLIENT)
public abstract class DebugLabelManager {
	private static final Logger LOGGER = LogUtils.getLogger();

	public void labelGlGpuBuffer(GlGpuBuffer buffer) {
	}

	public void labelGlTexture(GlTexture texture) {
	}

	public void labelCompiledShader(CompiledShader shader) {
	}

	public void labelShaderProgram(ShaderProgram program) {
	}

	public void labelAllocatedBuffer(BufferManager.AllocatedBuffer buffer) {
	}

	public static DebugLabelManager create(GLCapabilities capabilities, boolean debugEnabled, Set<String> usedCapabilities) {
		if (debugEnabled) {
			if (capabilities.GL_KHR_debug && GlBackend.allowGlKhrDebug) {
				usedCapabilities.add("GL_KHR_debug");
				return new DebugLabelManager.KHRDebugLabelManager();
			}

			if (capabilities.GL_EXT_debug_label && GlBackend.allowExtDebugLabel) {
				usedCapabilities.add("GL_EXT_debug_label");
				return new DebugLabelManager.EXTDebugLabelManager();
			}

			LOGGER.warn("Debug labels unavailable: neither KHR_debug nor EXT_debug_label are supported");
		}

		return new DebugLabelManager.NoOpDebugLabelManager();
	}

	public boolean isUsable() {
		return false;
	}

	@Environment(EnvType.CLIENT)
	static class EXTDebugLabelManager extends DebugLabelManager {
		@Override
		public void labelGlGpuBuffer(GlGpuBuffer buffer) {
			buffer.ensureAllocated();
			Supplier<String> supplier = buffer.debugLabelSupplier;
			if (supplier != null) {
				EXTDebugLabel.glLabelObjectEXT(EXTDebugLabel.GL_BUFFER_OBJECT_EXT, buffer.id, StringHelper.truncate((String)supplier.get(), 256, true));
			}
		}

		@Override
		public void labelGlTexture(GlTexture texture) {
			EXTDebugLabel.glLabelObjectEXT(GL11.GL_TEXTURE, texture.glId, StringHelper.truncate(texture.getLabel(), 256, true));
		}

		@Override
		public void labelCompiledShader(CompiledShader shader) {
			EXTDebugLabel.glLabelObjectEXT(EXTDebugLabel.GL_SHADER_OBJECT_EXT, shader.getHandle(), StringHelper.truncate(shader.getDebugLabel(), 256, true));
		}

		@Override
		public void labelShaderProgram(ShaderProgram program) {
			EXTDebugLabel.glLabelObjectEXT(EXTDebugLabel.GL_PROGRAM_OBJECT_EXT, program.getGlRef(), StringHelper.truncate(program.getDebugLabel(), 256, true));
		}

		@Override
		public void labelAllocatedBuffer(BufferManager.AllocatedBuffer buffer) {
			EXTDebugLabel.glLabelObjectEXT(GL11.GL_VERTEX_ARRAY, buffer.glId, StringHelper.truncate(buffer.vertexFormat.toString(), 256, true));
		}

		@Override
		public boolean isUsable() {
			return true;
		}
	}

	@Environment(EnvType.CLIENT)
	static class KHRDebugLabelManager extends DebugLabelManager {
		private final int maxLabelLength = GL11.glGetInteger(KHRDebug.GL_MAX_LABEL_LENGTH);

		@Override
		public void labelGlGpuBuffer(GlGpuBuffer buffer) {
			buffer.ensureAllocated();
			Supplier<String> supplier = buffer.debugLabelSupplier;
			if (supplier != null) {
				KHRDebug.glObjectLabel(KHRDebug.GL_BUFFER, buffer.id, StringHelper.truncate((String)supplier.get(), this.maxLabelLength, true));
			}
		}

		@Override
		public void labelGlTexture(GlTexture texture) {
			KHRDebug.glObjectLabel(GL11.GL_TEXTURE, texture.glId, StringHelper.truncate(texture.getLabel(), this.maxLabelLength, true));
		}

		@Override
		public void labelCompiledShader(CompiledShader shader) {
			KHRDebug.glObjectLabel(KHRDebug.GL_SHADER, shader.getHandle(), StringHelper.truncate(shader.getDebugLabel(), this.maxLabelLength, true));
		}

		@Override
		public void labelShaderProgram(ShaderProgram program) {
			KHRDebug.glObjectLabel(KHRDebug.GL_PROGRAM, program.getGlRef(), StringHelper.truncate(program.getDebugLabel(), this.maxLabelLength, true));
		}

		@Override
		public void labelAllocatedBuffer(BufferManager.AllocatedBuffer buffer) {
			KHRDebug.glObjectLabel(GL11.GL_VERTEX_ARRAY, buffer.glId, StringHelper.truncate(buffer.vertexFormat.toString(), this.maxLabelLength, true));
		}

		@Override
		public boolean isUsable() {
			return true;
		}
	}

	@Environment(EnvType.CLIENT)
	static class NoOpDebugLabelManager extends DebugLabelManager {
	}
}
