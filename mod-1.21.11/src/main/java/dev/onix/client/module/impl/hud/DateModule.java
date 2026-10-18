package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.TextSetting;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class DateModule extends TextHudModule {
    private final ModeSetting format = add(new ModeSetting("format", "Format", "Mon, 13 Oct", "13.10.2026", "10/13/2026", "2026-10-13", "Custom"));
    private final TextSetting pattern = add(new TextSetting("pattern", "Custom Pattern", "EEEE, d MMMM", 32).visibleIf(() -> format.is("Custom")));

    public DateModule() {
        super("date", "Date", "Today's date.", Category.HUD, false, Anchor.TOP_RIGHT, -4, 22);
        tags("calendar", "day");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        String p = switch (format.get()) {
            case "13.10.2026" -> "dd.MM.yyyy";
            case "10/13/2026" -> "MM/dd/yyyy";
            case "2026-10-13" -> "yyyy-MM-dd";
            case "Custom" -> pattern.get();
            default -> "EEE, d MMM";
        };
        String text;
        try {
            text = LocalDate.now().format(DateTimeFormatter.ofPattern(p, Locale.ENGLISH));
        } catch (Exception e) {
            text = "Invalid pattern";
        }
        out.add(new Line("Date", text));
    }
}
