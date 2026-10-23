package dev.onix.client.util;

import net.minecraft.client.MinecraftClient;

/** Whether the F3 screen is open (before 1.21.9 shouldShowDebugHud() means exactly that). */
public final class DebugScreen {
    private DebugScreen() {}

    public static boolean open() {
        return MinecraftClient.getInstance().getDebugHud().shouldShowDebugHud();
    }
}
