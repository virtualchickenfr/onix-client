package dev.onix.client.module;

import net.minecraft.client.gui.GuiGraphics;

/** Modules that draw full-screen effects (flashes, vignettes, filters) below the HUD elements. */
public interface OverlayRenderer {
    void renderOverlay(GuiGraphics ctx);
}
