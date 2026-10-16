package dev.onix.client.module.impl.combat;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.CombatTracker;

import java.util.List;

public class ComboCounterModule extends TextHudModule {
    private final NumberSetting showFrom = add(new NumberSetting("showFrom", "Show From", 0, 0, 10, 1)
            .describe("Hide until your combo reaches this number. 0 = always visible."));
    private final BoolSetting suffix = add(new BoolSetting("suffix", "Write \"hits\"", false));
    private final BoolSetting highlight = add(new BoolSetting("highlight", "Highlight Big Combos", true));
    private final NumberSetting bigFrom = add(new NumberSetting("bigFrom", "Big From", 5, 2, 20, 1).visibleIf(highlight::isOn));
    private final ColorSetting bigColor = add(new ColorSetting("bigColor", "Big Combo Color", 0xFFFB923C).visibleIf(highlight::isOn));

    public ComboCounterModule() {
        super("combo", "Combo Counter", "Hits in a row without getting hit back.", Category.COMBAT, false, Anchor.TOP_LEFT, 4, 220);
        tags("hits", "streak");
    }

    @Override
    public boolean hasContent() {
        return CombatTracker.combo() >= showFrom.getInt();
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        int c = CombatTracker.combo();
        if (preview && c == 0) c = 3;
        String value = c == 0 ? "–" : c + (suffix.isOn() ? " hits" : "");
        out.add(new Line("Combo", value, highlight.isOn() && c >= bigFrom.getInt() ? bigColor.color() : 0));
    }

    @Override
    protected String sampleValue() {
        return suffix.isOn() ? "00 hits" : "00";
    }
}
