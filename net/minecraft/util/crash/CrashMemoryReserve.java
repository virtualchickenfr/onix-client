package net.minecraft.util.crash;

import org.jetbrains.annotations.Nullable;

public class CrashMemoryReserve {
	@Nullable
	private static byte[] reservedMemory = null;

	public static void reserveMemory() {
		reservedMemory = new byte[10485760];
	}

	public static void releaseMemory() {
		reservedMemory = new byte[0];
	}
}
