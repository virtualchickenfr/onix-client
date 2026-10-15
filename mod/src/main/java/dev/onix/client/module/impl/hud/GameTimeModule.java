package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;

import java.util.List;

public class GameTimeModule extends TextHudModule {
    private final ModeSetting format = add(new ModeSetting("format", "Format", "24-hour", "12-hour", "Ticks"));
    private final BoolSetting phase = add(new BoolSetting("phase", "Show Day/Night", true));

    public GameTimeModule() {
        super("gametime", "Game Time", "Minecraft time of day as a clock.", Category.HUD, false, Anchor.TOP_RIGHT, -4, 40);
        tags("world time", "night", "day");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        long t = mc().world == null ? 6000 : Math.floorMod(mc().world.getTimeOfDay(), 24000L);
        String value;
        if (format.is("Ticks")) {
            value = String.valueOf(t);
        } else {
            int minutes = (int) (((t + 6000) % 24000) * 1440 / 24000);
            int h = minutes / 60;
            int m = minutes % 60;
            if (format.is("12-hour")) {
                value = String.format("%d:%02d %s", h % 12 == 0 ? 12 : h % 12, m, h < 12 ? "AM" : "PM");
            } else {
                value = String.format("%02d:%02d", h, m);
            }
        }
        if (phase.isOn()) {
            String p = t < 12000 ? "Day" : t < 13000 ? "Sunset" : t < 23000 ? "Night" : "Sunrise";
            value += " · " + p;
        }
        out.add(new Line("Time", value));
    }
}
