package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;

/** Numeric ping in the tab list instead of the signal bars. Applied by PlayerListHudMixin. */
public class TabListModule extends Module {
    public final BoolSetting numericPing = add(new BoolSetting("numeric", "Numeric Ping", true));
    public final BoolSetting colorPing = add(new BoolSetting("colorPing", "Color by Ping", true).visibleIf(numericPing::isOn));
    public final ColorSetting goodColor = add(new ColorSetting("good", "Good (< 80 ms)", 0xFF34D399).visibleIf(colorPing::isOn));
    public final ColorSetting okColor = add(new ColorSetting("ok", "Okay (< 180 ms)", 0xFFFACC15).visibleIf(colorPing::isOn));
    public final ColorSetting badColor = add(new ColorSetting("bad", "Bad", 0xFFFF5A5F).visibleIf(colorPing::isOn));

    public TabListModule() {
        super("tablist", "Tab List", "Numeric, colored ping in the player list.", Category.VISUAL, false);
        tags("tab", "player list", "ping");
    }

    public int pingColor(int ms) {
        if (!colorPing.isOn()) return 0xFFFFFFFF;
        return ms < 80 ? goodColor.color() : ms < 180 ? okColor.color() : badColor.color();
    }
}
