package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.TpsTracker;

import java.util.List;
import java.util.Locale;

public class TpsModule extends TextHudModule {
    private final NumberSetting decimals = add(new NumberSetting("decimals", "Decimals", 1, 0, 2, 1));
    private final BoolSetting colorize = add(new BoolSetting("colorize", "Color by TPS", true));
    private final ColorSetting goodColor = add(new ColorSetting("goodColor", "Good Color (18+)", 0xFF34D399).visibleIf(colorize::isOn));
    private final ColorSetting okColor = add(new ColorSetting("okColor", "Medium Color (12+)", 0xFFFACC15).visibleIf(colorize::isOn));
    private final ColorSetting badColor = add(new ColorSetting("badColor", "Bad Color", 0xFFFF5A5F).visibleIf(colorize::isOn));

    public TpsModule() {
        super("tps", "TPS", "Estimated server ticks per second (20 = no lag).", Category.HUD, false, Anchor.TOP_LEFT, 4, 76);
        tags("server lag", "ticks");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        double tps = TpsTracker.tps();
        int color = 0;
        if (colorize.isOn()) color = tps >= 18 ? goodColor.color() : tps >= 12 ? okColor.color() : badColor.color();
        out.add(new Line("TPS", String.format(Locale.ROOT, "%." + decimals.getInt() + "f", tps), color));
    }

    @Override
    protected String sampleValue() {
        return decimals.getInt() == 0 ? "20" : "20." + "0".repeat(decimals.getInt());
    }
}
