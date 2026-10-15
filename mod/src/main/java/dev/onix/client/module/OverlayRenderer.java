package dev.onix.client.module;

import net.minecraft.client.gui.DrawContext;

/** Modules that draw full-screen effects (flashes, vignettes, filters) below the HUD elements. */
public interface OverlayRenderer {
    void renderOverlay(DrawContext ctx);
}
