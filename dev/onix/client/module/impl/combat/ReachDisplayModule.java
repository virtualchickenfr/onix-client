package dev.onix.client.module.impl.combat;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.CombatTracker;

import java.util.List;
import java.util.Locale;

public class ReachDisplayModule extends TextHudModule {
    private final NumberSetting decimals = add(new NumberSetting("decimals", "Decimals", 2, 0, 3, 1));
    private final NumberSetting timeout = add(new NumberSetting("timeout", "Reset After", 3, 1, 15, 1).unit("s"));
    private final BoolSetting unit = add(new BoolSetting("unit", "Show \"blocks\"", false));
    private final BoolSetting colorize = add(new BoolSetting("colorize", "Color by Distance", false));
    private final ColorSetting closeColor = add(new ColorSetting("closeColor", "Close (< 2.5)", 0xFFFACC15).visibleIf(colorize::isOn));
    private final ColorSetting farColor = add(new ColorSetting("farColor", "Far (≥ 2.5)", 0xFF34D399).visibleIf(colorize::isOn));

    public ReachDisplayModule() {
        super("reach", "Reach Display", "Distance of your last hit.", Category.COMBAT, false, Anchor.TOP_LEFT, 4, 202);
        tags("distance", "hit range");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        double r = CombatTracker.lastReach();
        boolean fresh = r >= 0 && System.currentTimeMillis() - CombatTracker.lastHitTime() < timeout.get() * 1000;
        if (!fresh && preview) r = 2.87;
        String value = !fresh && !preview ? "–" : String.format(Locale.ROOT, "%." + decimals.getInt() + "f", r) + (unit.isOn() ? " blocks" : "");
        int color = colorize.isOn() && (fresh || preview) ? (r < 2.5 ? closeColor.color() : farColor.color()) : 0;
        out.add(new Line("Reach", value, color));
    }

    @Override
    protected String sampleValue() {
        return "0.00" + (unit.isOn() ? " blocks" : "");
    }
}
