package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;

import java.util.List;

public class DayCounterModule extends TextHudModule {
    private final BoolSetting startAtOne = add(new BoolSetting("startOne", "Start at Day 1", true));

    public DayCounterModule() {
        super("day", "Day Counter", "How many Minecraft days have passed in this world.", Category.HUD, false, Anchor.TOP_RIGHT, -4, 58);
        tags("days");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        long day = mc().level == null ? 0 : mc().level.getDayTime() / 24000L;
        out.add(new Line("Day", String.valueOf(day + (startAtOne.isOn() ? 1 : 0))));
    }
}
