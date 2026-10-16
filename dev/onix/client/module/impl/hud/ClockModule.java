package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.TextSetting;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class ClockModule extends TextHudModule {
    private final ModeSetting format = add(new ModeSetting("format", "Format", "24-hour", "12-hour", "Custom"));
    private final BoolSetting seconds = add(new BoolSetting("seconds", "Show Seconds", false).visibleIf(() -> !format.is("Custom")));
    private final TextSetting pattern = add(new TextSetting("pattern", "Custom Pattern", "HH:mm", 32)
            .describe("Java time pattern, e.g. HH:mm:ss or h:mm a.").visibleIf(() -> format.is("Custom")));

    private String cachedPattern;
    private DateTimeFormatter formatter;

    public ClockModule() {
        super("clock", "Clock", "Real-world time.", Category.HUD, false, Anchor.TOP_RIGHT, -4, 4);
        tags("time", "real time");
    }

    private DateTimeFormatter formatter() {
        String p = switch (format.get()) {
            case "12-hour" -> seconds.isOn() ? "h:mm:ss a" : "h:mm a";
            case "Custom" -> pattern.get();
            default -> seconds.isOn() ? "HH:mm:ss" : "HH:mm";
        };
        if (!p.equals(cachedPattern)) {
            cachedPattern = p;
            try {
                formatter = DateTimeFormatter.ofPattern(p, Locale.ENGLISH);
            } catch (IllegalArgumentException e) {
                formatter = DateTimeFormatter.ofPattern("HH:mm");
            }
        }
        return formatter;
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        String text;
        try {
            text = LocalTime.now().format(formatter());
        } catch (Exception e) {
            text = "Invalid pattern";
        }
        out.add(new Line("Time", text));
    }
}
