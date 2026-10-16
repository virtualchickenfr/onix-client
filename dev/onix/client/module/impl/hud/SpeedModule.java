package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;

import java.util.List;
import java.util.Locale;

public class SpeedModule extends TextHudModule {
    private final ModeSetting unit = add(new ModeSetting("unit", "Unit", "Blocks/s", "km/h", "mph"));
    private final BoolSetting horizontal = add(new BoolSetting("horizontal", "Horizontal Only", true));
    private final NumberSetting decimals = add(new NumberSetting("decimals", "Decimals", 1, 0, 2, 1));
    private final NumberSetting smoothing = add(new NumberSetting("smoothing", "Smoothing", 60, 0, 95, 5).unit("%"));

    private double lastX, lastY, lastZ, speed;
    private boolean hasLast;

    public SpeedModule() {
        super("speed", "Speed", "How fast you are moving.", Category.HUD, false, Anchor.TOP_LEFT, 4, 166);
        tags("velocity", "speedometer", "bps");
    }

    @Override
    public void onTick() {
        if (mc().player == null) {
            hasLast = false;
            return;
        }
        double x = mc().player.getX(), y = mc().player.getY(), z = mc().player.getZ();
        if (hasLast) {
            double dx = x - lastX, dy = horizontal.isOn() ? 0 : y - lastY, dz = z - lastZ;
            double current = Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
            double s = smoothing.get() / 100.0;
            speed = speed * s + current * (1 - s);
        }
        lastX = x;
        lastY = y;
        lastZ = z;
        hasLast = true;
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        double v = switch (unit.get()) {
            case "km/h" -> speed * 3.6;
            case "mph" -> speed * 2.23694;
            default -> speed;
        };
        String suffix = switch (unit.get()) {
            case "km/h" -> " km/h";
            case "mph" -> " mph";
            default -> " b/s";
        };
        out.add(new Line("Speed", String.format(Locale.ROOT, "%." + decimals.getInt() + "f", v) + suffix));
    }

    @Override
    protected String sampleValue() {
        return "00.0 km/h";
    }
}
