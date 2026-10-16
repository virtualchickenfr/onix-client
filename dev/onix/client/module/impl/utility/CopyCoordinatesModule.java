package dev.onix.client.module.impl.utility;

import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.setting.TextSetting;
import java.util.Locale;
import net.minecraft.world.phys.Vec3;

/** One key copies your position to the clipboard in the format you like. */
public class CopyCoordinatesModule extends Module {
    private final KeybindSetting key = add(new KeybindSetting("key", "Copy Key", KeybindSetting.NONE));
    private final ModeSetting format = add(new ModeSetting("format", "Format", "X Y Z", "X, Y, Z", "/tp X Y Z", "XYZ: X / Y / Z", "Custom"));
    private final TextSetting custom = add(new TextSetting("custom", "Custom Format", "{x} {y} {z} ({dim})", 128)
            .placeholder("{x} {y} {z} {dim} {facing}")
            .describe("Placeholders: {x} {y} {z} {dim} {facing}.").visibleIf(() -> format.is("Custom")));
    private final NumberSetting decimals = add(new NumberSetting("decimals", "Decimal Places", 0, 0, 3, 1));
    private final BoolSetting dimension = add(new BoolSetting("dimension", "Add Dimension", false).visibleIf(() -> !format.is("Custom")));

    {
        group("After Copying");
    }

    private final BoolSetting notify = add(new BoolSetting("notify", "Show Notification", true));
    private final BoolSetting sendToChat = add(new BoolSetting("chat", "Also Send to Chat", false)
            .describe("Posts your coordinates in chat – careful on public servers."));

    public CopyCoordinatesModule() {
        super("copycoords", "Copy Coordinates", "One key copies your position to the clipboard.", Category.UTILITY, false);
        tags("clipboard", "share location");
    }

    private String num(double v) {
        int d = decimals.getInt();
        return d == 0 ? String.valueOf((int) Math.floor(v)) : String.format(Locale.ROOT, "%." + d + "f", v);
    }

    private String dimensionName() {
        if (mc().level == null) return "";
        String path = mc().level.dimension().location().getPath();
        return switch (path) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> path;
        };
    }

    @Override
    public void onTick() {
        if (!key.poll() || mc().player == null) return;
        Vec3 p = mc().player.position();
        String x = num(p.x), y = num(p.y), z = num(p.z);
        String text = switch (format.get()) {
            case "X, Y, Z" -> x + ", " + y + ", " + z;
            case "/tp X Y Z" -> "/tp " + x + " " + y + " " + z;
            case "XYZ: X / Y / Z" -> "XYZ: " + x + " / " + y + " / " + z;
            case "Custom" -> custom.get().replace("{x}", x).replace("{y}", y).replace("{z}", z)
                    .replace("{dim}", dimensionName())
                    .replace("{facing}", mc().player.getDirection().getSerializedName());
            default -> x + " " + y + " " + z;
        };
        if (dimension.isOn() && !format.is("Custom")) text += " (" + dimensionName() + ")";
        mc().keyboardHandler.setClipboard(text);
        if (notify.isOn()) Notifications.show("Coordinates copied", text);
        if (sendToChat.isOn() && mc().getConnection() != null) mc().getConnection().sendChat(text);
    }
}
