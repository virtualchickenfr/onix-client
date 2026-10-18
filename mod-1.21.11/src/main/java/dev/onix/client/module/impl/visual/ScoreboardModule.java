package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;

/** Hide, move or resize the sidebar scoreboard. Applied by InGameHudMixin. */
public class ScoreboardModule extends Module {
    public final BoolSetting hide = add(new BoolSetting("hide", "Hide Scoreboard", false));
    public final NumberSetting scale = add(new NumberSetting("scale", "Scale", 1, 0.5, 1.5, 0.05).unit("x").visibleIf(() -> !hide.isOn()));
    public final NumberSetting opacity = add(new NumberSetting("opacity", "Opacity", 100, 10, 100, 5).unit("%").visibleIf(() -> !hide.isOn()));

    {
        group("Position");
    }

    public final NumberSetting offsetY = add(new NumberSetting("offsetY", "Vertical Offset", 0, -200, 200, 2).unit("px").visibleIf(() -> !hide.isOn()));
    public final NumberSetting offsetX = add(new NumberSetting("offsetX", "Horizontal Offset", 0, -300, 0, 2).unit("px").visibleIf(() -> !hide.isOn()));
    public final BoolSetting hideInDebug = add(new BoolSetting("hideInDebug", "Hide on F3 Screen", true));
    public final BoolSetting hideInTab = add(new BoolSetting("hideInTab", "Hide While Tab List Is Open", false));

    public ScoreboardModule() {
        super("scoreboard", "Scoreboard", "Hide, move, fade or shrink the sidebar scoreboard.", Category.VISUAL, false);
        tags("sidebar", "minigame");
    }

    public boolean hidden() {
        if (hide.isOn()) return true;
        if (hideInDebug.isOn() && mc().getDebugOverlay().showDebugScreen()) return true;
        return hideInTab.isOn() && mc().options.keyPlayerList.isDown();
    }
}
