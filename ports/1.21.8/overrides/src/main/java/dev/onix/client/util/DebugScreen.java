package dev.onix.client.util;

import net.minecraft.client.Minecraft;

/** Whether the F3 screen is open (before 1.21.9 showDebugScreen() means exactly that). */
public final class DebugScreen {
    private DebugScreen() {}

    public static boolean open() {
        return Minecraft.getInstance().getDebugOverlay().showDebugScreen();
    }
}
