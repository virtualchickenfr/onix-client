package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;

import java.util.List;

public class PlayerCountModule extends TextHudModule {
    private final BoolSetting hideSingleplayer = add(new BoolSetting("hideSp", "Hide in Singleplayer", true));

    public PlayerCountModule() {
        super("playercount", "Player Count", "Number of players in the tab list.", Category.HUD, false, Anchor.TOP_RIGHT, -4, 148);
        tags("online", "players");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (mc().isInSingleplayer() && hideSingleplayer.isOn() && !preview) return;
        int n = mc().getNetworkHandler() == null ? 0 : mc().getNetworkHandler().getPlayerList().size();
        out.add(new Line("Players", String.valueOf(n)));
    }
}
