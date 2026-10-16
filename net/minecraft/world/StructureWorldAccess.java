package net.minecraft.world;

import java.util.function.Supplier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public interface StructureWorldAccess extends ServerWorldAccess {
	long getSeed();

	/**
	 * {@return {@code true} if the given position is an accessible position
	 * for the {@code setBlockState} function}
	 */
	default boolean isValidForSetBlock(BlockPos pos) {
		return true;
	}

	default void setCurrentlyGeneratingStructureName(@Nullable Supplier<String> structureName) {
	}
}
