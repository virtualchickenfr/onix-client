package net.minecraft.client.gl;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.logging.LogUtils;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * Represents a uniform variable.
 * 
 * @see <a href="https://www.khronos.org/opengl/wiki/Uniform_(GLSL)">
 * Uniform (GLSL) - OpenGL Wiki</a>
 */
@Environment(EnvType.CLIENT)
public class GlUniform extends Uniform implements AutoCloseable {
	private static final Logger LOGGER = LogUtils.getLogger();
	private int location;
	private final UniformType type;
	private final IntBuffer intData;
	private final FloatBuffer floatData;
	private final String name;
	private boolean dirty;

	public GlUniform(String name, UniformType type) {
		this.name = name;
		this.type = type;
		if (type.isIntegerData()) {
			this.intData = MemoryUtil.memAllocInt(type.size());
			this.floatData = null;
		} else {
			this.intData = null;
			this.floatData = MemoryUtil.memAllocFloat(type.size());
		}

		this.location = -1;
	}

	public static int getUniformLocation(int program, CharSequence name) {
		return GlStateManager._glGetUniformLocation(program, name);
	}

	public static void setUniform(int location, int value) {
		GlStateManager._glUniform1i(location, value);
	}

	public void close() {
		if (this.intData != null) {
			MemoryUtil.memFree(this.intData);
		}

		if (this.floatData != null) {
			MemoryUtil.memFree(this.floatData);
		}
	}

	public void setLocation(int location) {
		this.location = location;
	}

	public String getName() {
		return this.name;
	}

	public UniformType getType() {
		return this.type;
	}

	@Override
	public final void set(float value1) {
		this.floatData.position(0);
		this.floatData.put(0, value1);
		this.dirty = true;
	}

	@Override
	public final void set(float value1, float value2) {
		this.floatData.position(0);
		this.floatData.put(0, value1);
		this.floatData.put(1, value2);
		this.dirty = true;
	}

	@Override
	public final void set(float value1, float value2, float value3) {
		this.floatData.position(0);
		this.floatData.put(0, value1);
		this.floatData.put(1, value2);
		this.floatData.put(2, value3);
		this.dirty = true;
	}

	@Override
	public final void set(Vector3f vector) {
		this.floatData.position(0);
		vector.get(this.floatData);
		this.dirty = true;
	}

	@Override
	public final void setAndFlip(float value1, float value2, float value3, float value4) {
		this.floatData.position(0);
		this.floatData.put(value1);
		this.floatData.put(value2);
		this.floatData.put(value3);
		this.floatData.put(value4);
		this.floatData.flip();
		this.dirty = true;
	}

	@Override
	public final void set(int value) {
		this.intData.position(0);
		this.intData.put(0, value);
		this.dirty = true;
	}

	@Override
	public final void set(int value1, int value2, int value3) {
		this.intData.position(0);
		this.intData.put(0, value1);
		this.intData.put(1, value2);
		this.intData.put(2, value3);
		this.dirty = true;
	}

	@Override
	public final void set(float[] values) {
		if (values.length < this.type.size()) {
			LOGGER.warn("Uniform.set called with a too-small value array (expected {}, got {}). Ignoring.", this.type.size(), values.length);
		} else {
			this.floatData.position(0);
			this.floatData.put(values);
			this.floatData.position(0);
			this.dirty = true;
		}
	}

	@Override
	public final void set(int[] values) {
		if (values.length < this.type.size()) {
			LOGGER.warn("Uniform.set called with a too-small value array (expected {}, got {}). Ignoring.", this.type.size(), values.length);
		} else {
			this.intData.position(0);
			this.intData.put(values);
			this.intData.position(0);
			this.dirty = true;
		}
	}

	@Override
	public final void set(Matrix4f values) {
		this.floatData.position(0);
		values.get(this.floatData);
		this.dirty = true;
	}

	public void upload() {
		if (this.dirty) {
			if (this.type.isIntegerData()) {
				switch (this.type) {
					case INT:
						GlStateManager._glUniform1(this.location, this.intData);
						break;
					case IVEC3:
						GlStateManager._glUniform3(this.location, this.intData);
				}
			} else {
				switch (this.type) {
					case FLOAT:
						GlStateManager._glUniform1(this.location, this.floatData);
						break;
					case VEC2:
						GlStateManager._glUniform2(this.location, this.floatData);
						break;
					case VEC3:
						GlStateManager._glUniform3(this.location, this.floatData);
						break;
					case VEC4:
						GlStateManager._glUniform4(this.location, this.floatData);
						break;
					case MATRIX4X4:
						GlStateManager._glUniformMatrix4(this.location, this.floatData);
				}
			}

			this.dirty = false;
		}
	}
}
