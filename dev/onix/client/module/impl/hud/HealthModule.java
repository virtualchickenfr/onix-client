package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;

import java.util.List;
import java.util.Locale;

public class HealthModule extends TextHudModule {
    private final ModeSetting unit = add(new ModeSetting("unit", "Unit", "Health Points", "Hearts"));
    private final BoolSetting absorption = add(new BoolSetting("absorption", "Include Absorption", true));
    private final BoolSetting max = add(new BoolSetting("max", "Show Maximum", true));
    private final BoolSetting colorize = add(new BoolSetting("colorize", "Color by Health", true));
    private final ColorSetting highColor = add(new ColorSetting("highColor", "Healthy Color", 0xFF34D399).visibleIf(colorize::isOn));
    private final ColorSetting midColor = add(new ColorSetting("midColor", "Hurt Color", 0xFFFACC15).visibleIf(colorize::isOn));
    private final ColorSetting lowColor = add(new ColorSetting("lowColor", "Critical Color", 0xFFFF5A5F).visibleIf(colorize::isOn));

    public HealthModule() {
        super("health", "Health", "Your exact health as a number.", Category.HUD, false, Anchor.BOTTOM_LEFT, 4, -58);
        tags("hp", "hearts");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (mc().player == null) return;
        float hp = mc().player.getHealth();
        float abs = absorption.isOn() ? mc().player.getAbsorptionAmount() : 0;
        float maxHp = mc().player.getMaxHealth();
        float div = unit.is("Hearts") ? 2f : 1f;
        String value = String.format(Locale.ROOT, "%.1f", (hp + abs) / div);
        if (max.isOn()) value += " / " + String.format(Locale.ROOT, "%.0f", maxHp / div);
        float ratio = hp / maxHp;
        int color = !colorize.isOn() ? 0 : ratio > 0.6f ? highColor.color() : ratio > 0.3f ? midColor.color() : lowColor.color();
        out.add(new Line(unit.is("Hearts") ? "Hearts" : "Health", value, color));
    }
}
