package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;

import java.util.List;

public class MoonPhaseModule extends TextHudModule {
    private static final String[] NAMES = {"Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent",
            "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous"};
    private static final int[] FULLNESS = {100, 75, 50, 25, 0, 25, 50, 75};

    private final BoolSetting fullness = add(new BoolSetting("fullness", "Show Brightness", true)
            .describe("Slime spawning in swamps depends on the moon's brightness."));

    public MoonPhaseModule() {
        super("moonphase", "Moon Phase", "Current moon phase.", Category.HUD, false, Anchor.TOP_RIGHT, -4, 76);
        tags("moon", "slime");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        int phase = mc().level == null ? 0 : Math.floorMod(mc().level.getMoonPhase(), 8);
        out.add(new Line("Moon", NAMES[phase] + (fullness.isOn() ? " (" + FULLNESS[phase] + "%)" : "")));
    }
}
