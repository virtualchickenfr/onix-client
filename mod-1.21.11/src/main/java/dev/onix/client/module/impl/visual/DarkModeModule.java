package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

/** From Carbon: darkens every container screen (inventory, chests, furnaces …) while keeping its shading. */
public class DarkModeModule extends Module {
    public final NumberSetting brightness = add(new NumberSetting("brightness", "Brightness", 30, 12, 60, 2).unit("%")
            .describe("How light the darkened screens stay."));
    public final BoolSetting lightText = add(new BoolSetting("lightText", "Light Titles", true)
            .describe("Container titles become light so they stay readable."));
    public final BoolSetting creative = add(new BoolSetting("creative", "Creative Inventory", true));

    public DarkModeModule() {
        super("darkmode", "Dark Mode", "A dark coat of paint for every container screen (from Carbon).", Category.VISUAL, false);
        tags("dark", "inventory", "chest", "gui", "carbon");
    }

    /** True while a container screen that should be darkened is open. */
    public boolean appliesNow() {
        if (!isEnabled()) return false;
        var screen = Minecraft.getInstance().screen;
        if (!(screen instanceof AbstractContainerScreen<?>)) return false;
        return !(screen instanceof CreativeModeInventoryScreen) || creative.isOn();
    }

    /** Multiplies a texture tint with the dark-mode factor. */
    public int apply(int color) {
        float f = brightness.getFloat() / 100f;
        int a = (color >>> 24) & 0xFF, r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return (a << 24) | (Math.round(r * f) << 16) | (Math.round(g * f) << 8) | Math.round(b * f);
    }
}
