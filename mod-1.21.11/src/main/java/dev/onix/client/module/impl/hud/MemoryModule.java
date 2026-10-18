package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;

import java.util.List;

public class MemoryModule extends TextHudModule {
    private final ModeSetting format = add(new ModeSetting("format", "Format", "Percent", "Used / Max", "Both"));
    private final BoolSetting warn = add(new BoolSetting("warn", "Warn When High", true));
    private final NumberSetting warnAt = add(new NumberSetting("warnAt", "Warn From", 85, 50, 99, 1).unit("%").visibleIf(warn::isOn));
    private final ColorSetting warnColor = add(new ColorSetting("warnColor", "Warning Color", 0xFFFF5A5F).visibleIf(warn::isOn));

    public MemoryModule() {
        super("memory", "Memory", "Java memory (RAM) used by the game.", Category.HUD, false, Anchor.TOP_RIGHT, -4, 112);
        tags("ram", "heap");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        Runtime rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long max = rt.maxMemory();
        int pct = (int) (used * 100 / max);
        String mb = (used >> 20) + " / " + (max >> 20) + " MB";
        String value = switch (format.get()) {
            case "Used / Max" -> mb;
            case "Both" -> pct + "% · " + mb;
            default -> pct + "%";
        };
        out.add(new Line("Memory", value, warn.isOn() && pct >= warnAt.get() ? warnColor.color() : 0));
    }

    @Override
    protected String sampleValue() {
        return format.is("Percent") ? "100%" : format.is("Both") ? "100% · 0000 / 0000 MB" : "0000 / 0000 MB";
    }
}
