package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;

import java.time.LocalTime;

/** Client-side time of day and moon phase. */
public class TimeChangerModule extends Module {
    private final ModeSetting time = add(new ModeSetting("time", "Time", "Noon", "Sunrise", "Sunset", "Midnight", "Custom", "Cycle", "Real Time"));
    private final NumberSetting custom = add(new NumberSetting("custom", "Custom Time", 6000, 0, 23999, 100).unit("ticks")
            .describe("0 = sunrise, 6000 = noon, 12000 = sunset, 18000 = midnight.").visibleIf(() -> time.is("Custom")));
    private final NumberSetting cycleSpeed = add(new NumberSetting("cycleSpeed", "Cycle Speed", 10, 1, 100, 1).unit("x")
            .describe("How much faster than normal the day passes.").visibleIf(() -> time.is("Cycle")));
    private final BoolSetting smooth = add(new BoolSetting("smooth", "Smooth Transitions", true)
            .describe("Glide to a new time instead of jumping."));

    {
        group("Moon");
    }

    private final ModeSetting moon = add(new ModeSetting("moon", "Moon Phase", "Server", "Full Moon", "Waning Gibbous", "Last Quarter",
            "Waning Crescent", "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous"));

    private double cycleTime;
    private double shown = -1;

    public TimeChangerModule() {
        super("timechanger", "Time Changer", "Set your own time of day and moon – client-side only.", Category.VISUAL, false);
        tags("day", "night", "sunset", "moon");
    }

    private double target() {
        return switch (time.get()) {
            case "Sunrise" -> 23500;
            case "Sunset" -> 12500;
            case "Midnight" -> 18000;
            case "Custom" -> custom.get();
            case "Cycle" -> cycleTime;
            case "Real Time" -> {
                LocalTime now = LocalTime.now();
                // Minecraft's day starts at 06:00
                yield ((now.getHour() * 1000 + now.getMinute() * 1000 / 60.0) + 18000) % 24000;
            }
            default -> 6000;
        };
    }

    @Override
    public void onTick() {
        if (mc().level == null) return;
        cycleTime = (cycleTime + cycleSpeed.get()) % 24000;
        double target = target();
        if (shown < 0 || !smooth.isOn() || time.is("Cycle")) {
            shown = target;
        } else {
            // move the short way around the clock
            double diff = ((target - shown) % 24000 + 36000) % 24000 - 12000;
            shown = Math.abs(diff) < 20 ? target : (shown + diff * 0.15 + 24000) % 24000;
        }
        long day = mc().level.getDayTime() / 24000L;
        int phase = moon.index() - 1;
        if (phase >= 0) day = Math.floorDiv(day, 8) * 8 + phase;
        mc().level.getLevelData().setDayTime(day * 24000L + (long) shown);
    }

    @Override
    protected void onDisable() {
        shown = -1;
    }
}
