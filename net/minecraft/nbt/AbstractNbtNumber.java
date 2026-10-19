package net.minecraft.nbt;

import java.util.Optional;

/**
 * Represents an NBT number.
 */
public sealed interface AbstractNbtNumber extends NbtPrimitive permits NbtByte, NbtShort, NbtInt, NbtLong, NbtFloat, NbtDouble {
	/**
	 * Gets the value as an 8-bit integer.
	 * 
	 * @return the value as a byte
	 */
	byte byteValue();

	/**
	 * Gets the value as a 16-bit integer.
	 * 
	 * @return the value as a short
	 */
	short shortValue();

	/**
	 * Gets the value as a 32-bit integer.
	 * 
	 * @return the value as an int
	 */
	int intValue();

	/**
	 * Gets the value as a 64-bit integer.
	 * 
	 * @return the value as a long
	 */
	long longValue();

	/**
	 * Gets the value as a 32-bit floating-point number.
	 * 
	 * @return the value as a float
	 */
	float floatValue();

	/**
	 * Gets the value as a 64-bit floating-point number.
	 * 
	 * @return the value as a double
	 */
	double doubleValue();

	/**
	 * Gets the value as a generic number.
	 * 
	 * @return the value as a {@link Number}
	 */
	Number numberValue();

	@Override
	default Optional<Number> asNumber() {
		return Optional.of(this.numberValue());
	}

	@Override
	default Optional<Byte> asByte() {
		return Optional.of(this.byteValue());
	}

	@Override
	default Optional<Short> asShort() {
		return Optional.of(this.shortValue());
	}

	@Override
	default Optional<Integer> asInt() {
		return Optional.of(this.intValue());
	}

	@Override
	default Optional<Long> asLong() {
		return Optional.of(this.longValue());
	}

	@Override
	default Optional<Float> asFloat() {
		return Optional.of(this.floatValue());
	}

	@Override
	default Optional<Double> asDouble() {
		return Optional.of(this.doubleValue());
	}

	@Override
	default Optional<Boolean> asBoolean() {
		return Optional.of(this.byteValue() != 0);
	}
}
