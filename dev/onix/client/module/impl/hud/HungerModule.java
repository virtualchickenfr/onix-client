package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;

import java.util.List;
import java.util.Locale;

public class HungerModule extends TextHudModule {
    private final BoolSetting food = add(new BoolSetting("food", "Food Level", true));
    private final BoolSetting saturation = add(new BoolSetting("saturation", "Saturation", true));
    private final BoolSetting exhaustion = add(new BoolSetting("exhaustion", "Exhaustion", false));
    private final NumberSetting warnBelow = add(new NumberSetting("warnBelow", "Warn Below", 6, 0, 20, 1));
    private final ColorSetting warnColor = add(new ColorSetting("warnColor", "Warning Color", 0xFFFB923C));

    public HungerModule() {
        super("hunger", "Hunger", "Food level, hidden saturation and exhaustion.", Category.HUD, false, Anchor.BOTTOM_LEFT, 4, -40);
        tags("food", "saturation");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (mc().player == null) return;
        var h = mc().player.getFoodData();
        if (food.isOn()) out.add(new Line("Food", h.getFoodLevel() + " / 20", h.getFoodLevel() <= warnBelow.getInt() ? warnColor.color() : 0));
        if (saturation.isOn()) out.add(new Line("Saturation", String.format(Locale.ROOT, "%.1f", h.getSaturationLevel())));
        if (exhaustion.isOn()) out.add(new Line("Exhaustion", String.format(Locale.ROOT, "%.2f", h.getExhaustionLevel())));
    }
}
