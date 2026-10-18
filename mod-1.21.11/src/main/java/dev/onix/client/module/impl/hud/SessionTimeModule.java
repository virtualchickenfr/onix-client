package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.util.SessionTracker;

import java.util.List;

public class SessionTimeModule extends TextHudModule {
    private final ModeSetting since = add(new ModeSetting("since", "Count Since", "Joined World", "Game Start"));
    private final BoolSetting hours = add(new BoolSetting("hours", "Always Show Hours", false));

    public SessionTimeModule() {
        super("sessiontime", "Session Time", "How long you have been playing.", Category.HUD, false, Anchor.TOP_RIGHT, -4, 166);
        tags("playtime", "timer");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        long start = since.is("Game Start") ? SessionTracker.GAME_START : SessionTracker.joinTime();
        out.add(new Line("Session", SessionTracker.formatDuration(System.currentTimeMillis() - start, hours.isOn())));
    }

    @Override
    protected String sampleValue() {
        return "0:00:00";
    }
}
