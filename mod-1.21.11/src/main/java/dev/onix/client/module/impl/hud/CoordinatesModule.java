package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.setting.TextSetting;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.Direction;

public class CoordinatesModule extends TextHudModule {
    private final ModeSetting layout = add(new ModeSetting("layout", "Layout", "Single Line", "Stacked"));
    private final NumberSetting decimals = add(new NumberSetting("decimals", "Decimals", 0, 0, 3, 1));
    private final BoolSetting showY = add(new BoolSetting("showY", "Show Y", true));
    private final BoolSetting direction = add(new BoolSetting("direction", "Show Direction", true));
    private final BoolSetting axisHint = add(new BoolSetting("axisHint", "Axis Hint (+X/-Z)", true).visibleIf(direction::isOn));
    private final BoolSetting otherDimension = add(new BoolSetting("otherDim", "Nether/Overworld Coordinates", false)
            .describe("Shows where you would come out on the other side of a nether portal."));
    private final TextSetting separator = add(new TextSetting("separator", "Separator", ", ", 4).visibleIf(() -> layout.is("Single Line")));

    public CoordinatesModule() {
        super("coords", "Coordinates", "Your position, direction and nether coordinates.", Category.HUD, true, Anchor.TOP_LEFT, 4, 58);
        tags("xyz", "position", "location");
    }

    private String num(double v) {
        return decimals.getInt() == 0 ? String.valueOf((int) Math.floor(v)) : String.format(Locale.ROOT, "%." + decimals.getInt() + "f", v);
    }

    private static String directionName(Direction d, boolean hint) {
        String name = switch (d) {
            case NORTH -> "North";
            case SOUTH -> "South";
            case WEST -> "West";
            case EAST -> "East";
            default -> d.getSerializedName();
        };
        if (!hint) return name;
        return name + switch (d) {
            case NORTH -> " (-Z)";
            case SOUTH -> " (+Z)";
            case WEST -> " (-X)";
            case EAST -> " (+X)";
            default -> "";
        };
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        double x = 0, y = 64, z = 0;
        Direction facing = Direction.NORTH;
        boolean nether = false;
        if (mc().player != null) {
            x = mc().player.getX();
            y = mc().player.getY();
            z = mc().player.getZ();
            facing = mc().player.getDirection();
            nether = mc().level != null && mc().level.dimension().identifier().getPath().equals("the_nether");
        }
        if (layout.is("Stacked")) {
            out.add(new Line("X", num(x)));
            if (showY.isOn()) out.add(new Line("Y", num(y)));
            out.add(new Line("Z", num(z)));
        } else {
            String sep = separator.get();
            out.add(new Line("XYZ", num(x) + sep + (showY.isOn() ? num(y) + sep : "") + num(z)));
        }
        if (direction.isOn()) out.add(new Line("Facing", directionName(facing, axisHint.isOn())));
        if (otherDimension.isOn()) {
            double f = nether ? 8 : 1 / 8.0;
            out.add(new Line(nether ? "Overworld" : "Nether", num(x * f) + ", " + num(z * f)));
        }
    }

    @Override
    protected String sampleValue() {
        return "-00000, 000, -00000";
    }
}
