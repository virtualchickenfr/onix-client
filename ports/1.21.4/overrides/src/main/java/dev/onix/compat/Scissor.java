package dev.onix.compat;

import net.minecraft.client.gui.DrawContext;

/** Minecraft 1.21.2+: enableScissor applies the current transform; Onix passes screen coordinates, so it is undone here. */
public final class Scissor {
	private Scissor() {}

	public static void screen(DrawContext ctx, int x1, int y1, int x2, int y2) {
		ctx.getMatrices().push();
		ctx.getMatrices().loadIdentity();
		ctx.enableScissor(x1, y1, x2, y2);
		ctx.getMatrices().pop();
	}
}
