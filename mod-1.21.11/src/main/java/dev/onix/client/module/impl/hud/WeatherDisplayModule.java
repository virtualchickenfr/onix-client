package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;

import java.util.List;

public class WeatherDisplayModule extends TextHudModule {
    private final BoolSetting hideClear = add(new BoolSetting("hideClear", "Hide When Clear", false));
    private final ColorSetting rainColor = add(new ColorSetting("rainColor", "Rain Color", 0xFF38BDF8));
    private final ColorSetting thunderColor = add(new ColorSetting("thunderColor", "Thunder Color", 0xFFFACC15));

    public WeatherDisplayModule() {
        super("weatherdisplay", "Weather", "Current weather in the world.", Category.HUD, false, Anchor.TOP_RIGHT, -4, 94);
        tags("rain", "thunder", "storm");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        boolean rain = mc().level != null && mc().level.isRaining();
        boolean thunder = mc().level != null && mc().level.isThundering();
        if (!rain && !thunder && hideClear.isOn() && !preview) return;
        if (thunder) out.add(new Line("Weather", "Thunder", thunderColor.color()));
        else if (rain) out.add(new Line("Weather", "Rain", rainColor.color()));
        else out.add(new Line("Weather", "Clear"));
    }
}
