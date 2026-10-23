package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.render.SmallCaps;
import dev.onix.client.setting.ModeSetting;

/** Every text in the game in small capitals (ᴛᴇxᴛ ʟɪᴋᴇ ᴛʜɪꜱ): chat, menus, HUD, signs, name tags. */
public class SmallCapsModule extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Small Caps", "All Small")
            .describe("Small Caps keeps capital letters big; All Small turns them into small capitals too."));

    public SmallCapsModule() {
        super("smallcaps", "Small Caps", "All text in small capital letters.", Category.VISUAL, false);
        tags("font", "small caps", "text", "letters", "smallcaps");
        style.onChange(this::apply);
    }

    private void apply() {
        SmallCaps.mode = !isEnabled() ? 0 : style.is("All Small") ? 2 : 1;
    }

    @Override
    protected void onEnable() {
        apply();
    }

    @Override
    protected void onDisable() {
        SmallCaps.mode = 0;
    }

    @Override
    public void onTick() {
        apply(); // also covers a profile switch that turns it on without onEnable
    }
}
