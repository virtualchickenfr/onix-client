package dev.onix.client.util;

import net.minecraft.client.Minecraft;

/**
 * Whether the F3 screen is really open. Since 1.21.9 DebugScreenOverlay.showDebugScreen() is also true while any
 * debug entry is set to "always shown" (F3 + F6), even with F3 closed – Onix used that and hid the whole HUD.
 */
public final class DebugScreen {
    private DebugScreen() {}

    public static boolean open() {
        Minecraft mc = Minecraft.getInstance();
        return mc.debugEntries.isOverlayVisible() && (!mc.options.hideGui || mc.screen != null);
    }
}
