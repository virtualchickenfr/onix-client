package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;

import java.util.List;

public class ExperienceModule extends TextHudModule {
    private final BoolSetting progress = add(new BoolSetting("progress", "Progress Percent", true));
    private final BoolSetting toNext = add(new BoolSetting("toNext", "Points to Next Level", false));
    private final BoolSetting total = add(new BoolSetting("total", "Total Points", false));

    public ExperienceModule() {
        super("experience", "Experience", "Level, progress and points.", Category.HUD, false, Anchor.BOTTOM_LEFT, 4, -76);
        tags("xp", "level", "enchanting");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (mc().player == null) return;
        var p = mc().player;
        String value = "Level " + p.experienceLevel;
        if (progress.isOn()) value += " · " + Math.round(p.experienceProgress * 100) + "%";
        out.add(new Line("XP", value));
        if (toNext.isOn()) {
            int needed = Math.round(p.getNextLevelExperience() * (1 - p.experienceProgress));
            out.add(new Line("Next", needed + " points"));
        }
        if (total.isOn()) out.add(new Line("Total", String.valueOf(p.totalExperience)));
    }
}
