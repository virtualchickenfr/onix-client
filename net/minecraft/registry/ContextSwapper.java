package net.minecraft.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public interface ContextSwapper {
	/**
	 * Recreates a value based on a new registry context (for example, for use in a
	 * different world).
	 * 
	 * This is done by encoding the value using the old registry context and immediately
	 * decoding it with the new one.
	 */
	<T> DataResult<T> swapContext(Codec<T> codec, T value, RegistryWrapper.WrapperLookup registries);
}
