package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;

import java.util.List;

public class AirModule extends TextHudModule {
    private final ModeSetting unit = add(new ModeSetting("unit", "Unit", "Seconds", "Bubbles", "Percent"));
    private final BoolSetting onlyUnderwater = add(new BoolSetting("onlyUnderwater", "Only When Losing Air", true));
    private final ColorSetting lowColor = add(new ColorSetting("lowColor", "Low Air Color", 0xFFFF5A5F));

    public AirModule() {
        super("air", "Air", "Remaining breath underwater.", Category.HUD, false, Anchor.BOTTOM_CENTER, 0, -70);
        tags("oxygen", "breath", "underwater");
    }

    @Override
    public boolean hasContent() {
        return mc().player != null && (!onlyUnderwater.isOn() || mc().player.getAir() < mc().player.getMaxAir());
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (mc().player == null) return;
        int air = Math.max(0, mc().player.getAir());
        int max = mc().player.getMaxAir();
        String value = switch (unit.get()) {
            case "Bubbles" -> (int) Math.ceil(air / 30.0) + " / 10";
            case "Percent" -> (air * 100 / max) + "%";
            default -> (air / 20) + " s";
        };
        out.add(new Line("Air", value, air < max / 4 ? lowColor.color() : 0));
    }
}
