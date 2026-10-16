package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.Mth;

public class DirectionModule extends TextHudModule {
    private static final String[] EIGHT = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
    private static final String[] EIGHT_LONG = {"South", "South-West", "West", "North-West", "North", "North-East", "East", "South-East"};

    private final ModeSetting points = add(new ModeSetting("points", "Compass Points", "8 Points", "4 Points"));
    private final BoolSetting longNames = add(new BoolSetting("long", "Full Names", true));
    private final BoolSetting yaw = add(new BoolSetting("yaw", "Show Yaw", true));
    private final BoolSetting pitch = add(new BoolSetting("pitch", "Show Pitch", true));
    private final NumberSetting decimals = add(new NumberSetting("decimals", "Decimals", 1, 0, 2, 1).visibleIf(() -> yaw.isOn() || pitch.isOn()));

    public DirectionModule() {
        super("direction", "Direction", "Cardinal direction plus exact yaw and pitch.", Category.HUD, false, Anchor.TOP_LEFT, 4, 94);
        tags("yaw", "pitch", "rotation", "facing");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        float y = mc().player == null ? 180 : Mth.wrapDegrees(mc().player.getYRot());
        float p = mc().player == null ? 0 : mc().player.getXRot();
        int idx;
        String name;
        if (points.is("8 Points")) {
            idx = Math.floorMod(Math.round(y / 45f), 8);
            name = longNames.isOn() ? EIGHT_LONG[idx] : EIGHT[idx];
        } else {
            idx = Math.floorMod(Math.round(y / 90f), 4) * 2;
            name = longNames.isOn() ? EIGHT_LONG[idx] : EIGHT[idx];
        }
        out.add(new Line("Facing", name));
        String fmt = "%." + decimals.getInt() + "f°";
        if (yaw.isOn()) out.add(new Line("Yaw", String.format(Locale.ROOT, fmt, y)));
        if (pitch.isOn()) out.add(new Line("Pitch", String.format(Locale.ROOT, fmt, p)));
    }

    @Override
    protected String sampleValue() {
        return "North-West";
    }
}
