package net.minecraft.client.gl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Defines methods that set a value of a uniform variable. The actual
 * implementation is in {@link GlUniform}.
 */
@Environment(EnvType.CLIENT)
public class Uniform {
	public void set(float value1) {
	}

	public void set(float value1, float value2) {
	}

	public void set(float value1, float value2, float value3) {
	}

	public void setAndFlip(float value1, float value2, float value3, float value4) {
	}

	public void set(int value) {
	}

	public void set(int value1, int value2) {
	}

	public void set(int value1, int value2, int value3) {
	}

	public void set(int value1, int value2, int value3, int value4) {
	}

	public void set(float[] values) {
	}

	public void set(int[] values) {
	}

	public void set(Vector3f vector) {
	}

	public void set(Vector4f vec) {
	}

	public void set(Matrix4f values) {
	}
}
