package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.render.SmallCaps;

/**
 * Every text in the game in small capitals (ᴛᴇxᴛ ʟɪᴋᴇ ᴛʜɪꜱ): chat, menus, HUD, signs, name tags. Capital and small
 * letters both become small capitals, so everything has the same height; numbers and other characters stay.
 */
public class SmallCapsModule extends Module {
    public SmallCapsModule() {
        super("smallcaps", "Small Caps", "All text in small capital letters.", Category.VISUAL, false);
        tags("font", "small caps", "text", "letters", "smallcaps");
    }

    @Override
    protected void onEnable() {
        SmallCaps.mode = 2;
    }

    @Override
    protected void onDisable() {
        SmallCaps.mode = 0;
    }

    @Override
    public void onTick() {
        SmallCaps.mode = 2; // also covers a profile switch that turns it on without onEnable
    }
}
