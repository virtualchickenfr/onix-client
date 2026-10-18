package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.TextSetting;
import dev.onix.client.util.CpsTracker;

import java.util.List;

public class CpsModule extends TextHudModule {
    private final ModeSetting buttons = add(new ModeSetting("buttons", "Buttons", "Left", "Left | Right", "Combined"));
    private final TextSetting separator = add(new TextSetting("separator", "Separator", " | ", 5).visibleIf(() -> buttons.is("Left | Right")));
    private final BoolSetting showPeak = add(new BoolSetting("peak", "Show Peak", false)
            .describe("Adds the highest CPS of the last 10 seconds."));

    private int peak;
    private long peakTime;

    public CpsModule() {
        super("cps", "CPS", "Clicks per second for left and right mouse button.", Category.HUD, true, Anchor.TOP_LEFT, 4, 22);
        tags("clicks", "click counter");
    }

    @Override
    public void onTick() {
        int now = CpsTracker.left();
        long time = System.currentTimeMillis();
        if (now >= peak || time - peakTime > 10_000) {
            peak = now;
            peakTime = time;
        }
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        int l = CpsTracker.left();
        int r = CpsTracker.right();
        String value = switch (buttons.get()) {
            case "Left | Right" -> l + separator.get() + r;
            case "Combined" -> String.valueOf(l + r);
            default -> String.valueOf(l);
        };
        if (showPeak.isOn()) value += " (" + peak + ")";
        out.add(new Line("CPS", value));
    }

    @Override
    protected String sampleValue() {
        return (buttons.is("Left | Right") ? "00" + separator.get() + "00" : "00") + (showPeak.isOn() ? " (00)" : "");
    }
}
