package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.FrameStats;

import java.util.List;

public class FpsModule extends TextHudModule {
    private final ModeSetting display = add(new ModeSetting("display", "Display", "Current", "Average", "Current + Min/Max", "Min / Avg / Max"));
    private final NumberSetting window = add(new NumberSetting("window", "Statistics Window", 5, 1, 30, 1).unit("s")
            .describe("Time span used for average, min and max."));
    private final BoolSetting colorize = add(new BoolSetting("colorize", "Color by FPS", false));
    private final NumberSetting good = add(new NumberSetting("good", "Good From", 60, 10, 240, 5).visibleIf(colorize::isOn));
    private final NumberSetting bad = add(new NumberSetting("bad", "Bad Below", 30, 5, 120, 5).visibleIf(colorize::isOn));
    private final ColorSetting goodColor = add(new ColorSetting("goodColor", "Good Color", 0xFF34D399).visibleIf(colorize::isOn));
    private final ColorSetting okColor = add(new ColorSetting("okColor", "Medium Color", 0xFFFACC15).visibleIf(colorize::isOn));
    private final ColorSetting badColor = add(new ColorSetting("badColor", "Bad Color", 0xFFFF5A5F).visibleIf(colorize::isOn));

    public FpsModule() {
        super("fps", "FPS", "Frames per second with optional min, max and average.", Category.HUD, true, Anchor.TOP_LEFT, 4, 4);
        tags("frames", "framerate", "fps counter");
    }

    private int color(float fps) {
        if (!colorize.isOn()) return 0;
        return fps >= good.get() ? goodColor.color() : fps < bad.get() ? badColor.color() : okColor.color();
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        int current = mc().getFps();
        float[] s = FrameStats.fps(window.get());
        String value = switch (display.get()) {
            case "Average" -> String.valueOf(Math.round(s[2]));
            case "Current + Min/Max" -> current + " (" + Math.round(s[0]) + "–" + Math.round(s[1]) + ")";
            case "Min / Avg / Max" -> Math.round(s[0]) + " / " + Math.round(s[2]) + " / " + Math.round(s[1]);
            default -> String.valueOf(current);
        };
        out.add(new Line("FPS", value, color(display.is("Average") ? s[2] : current)));
    }

    @Override
    protected String sampleValue() {
        return switch (display.get()) {
            case "Current + Min/Max" -> "000 (000–000)";
            case "Min / Avg / Max" -> "000 / 000 / 000";
            default -> "000";
        };
    }
}
