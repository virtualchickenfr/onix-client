package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;

/** From Carbon: smaller or hidden on-screen titles (the big text servers show). */
public class TitleTweakerModule extends Module {
    public final BoolSetting hide = add(new BoolSetting("hide", "Hide Titles", false));
    public final NumberSetting scale = add(new NumberSetting("scale", "Scale", 0.7, 0.3, 1.5, 0.05).unit("x")
            .visibleIf(() -> !hide.isOn()));

    public TitleTweakerModule() {
        super("titletweaker", "Title Tweaker", "Scale or hide on-screen titles (from Carbon).", Category.VISUAL, false);
        tags("title", "subtitle", "carbon");
    }
}
