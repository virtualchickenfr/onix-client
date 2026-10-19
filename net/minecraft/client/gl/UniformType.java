package net.minecraft.client.gl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.StringIdentifiable;

@Environment(EnvType.CLIENT)
public enum UniformType implements StringIdentifiable {
	INT(1, "int"),
	IVEC3(3, "ivec3"),
	FLOAT(1, "float"),
	VEC2(2, "vec2"),
	VEC3(3, "vec3"),
	VEC4(4, "vec4"),
	MATRIX4X4(16, "matrix4x4");

	public static final StringIdentifiable.EnumCodec<UniformType> CODEC = StringIdentifiable.createCodec(UniformType::values);
	final int size;
	final String name;

	private UniformType(final int size, final String name) {
		this.size = size;
		this.name = name;
	}

	public int size() {
		return this.size;
	}

	public boolean isIntegerData() {
		return this == INT || this == IVEC3;
	}

	@Override
	public String asString() {
		return this.name;
	}

	public int count() {
		return this.size;
	}
}
