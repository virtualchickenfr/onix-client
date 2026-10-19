package dev.onix.compat;

import net.minecraft.client.Minecraft;

/** Minecraft 1.21.8: vanilla chunk borders (F3+G) are a toggle without a getter, so the state is tracked here. */
public final class ChunkBorderState {
	public static boolean on;

	private ChunkBorderState() {}

	public static void toggle() {
		on = Minecraft.getInstance().debugRenderer.switchRenderChunkborder();
	}
}
