package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;

/** Hide or shrink boss bars (and the server announcements many networks put in them). */
public class BossBarModule extends Module {
    public final BoolSetting hide = add(new BoolSetting("hide", "Hide Boss Bars", false));
    public final NumberSetting scale = add(new NumberSetting("scale", "Scale", 1, 0.5, 1.5, 0.05).unit("x").visibleIf(() -> !hide.isOn()));
    public final NumberSetting opacity = add(new NumberSetting("opacity", "Opacity", 100, 10, 100, 5).unit("%").visibleIf(() -> !hide.isOn()));

    {
        group("Position");
    }

    public final NumberSetting offsetY = add(new NumberSetting("offsetY", "Vertical Offset", 0, 0, 200, 2).unit("px").visibleIf(() -> !hide.isOn()));
    public final NumberSetting offsetX = add(new NumberSetting("offsetX", "Horizontal Offset", 0, -300, 300, 2).unit("px").visibleIf(() -> !hide.isOn()));
    public final BoolSetting hideInDebug = add(new BoolSetting("hideInDebug", "Hide on F3 Screen", false));

    public BossBarModule() {
        super("bossbar", "Boss Bar", "Hide, move, fade or resize boss bars.", Category.VISUAL, false);
        tags("wither", "dragon", "announcements");
    }

    public boolean hidden() {
        return hide.isOn() || (hideInDebug.isOn() && mc().getDebugOverlay().showDebugScreen());
    }
}
