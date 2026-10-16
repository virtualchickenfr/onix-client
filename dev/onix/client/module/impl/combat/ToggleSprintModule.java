package dev.onix.client.module.impl.combat;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.TextSetting;
import java.util.List;
import net.minecraft.client.KeyMapping;

/** Tap the sprint key once to keep sprinting; tap again to stop. */
public class ToggleSprintModule extends TextHudModule {
    private final ModeSetting mode = add(new ModeSetting("mode", "Mode", "Toggle", "Always Sprint")
            .describe("Toggle: tap the sprint key. Always: sprint whenever you walk forward."));
    private final BoolSetting showStatus = add(new BoolSetting("status", "Show Status", true));
    private final TextSetting toggledText = add(new TextSetting("toggledText", "Toggled Text", "Sprinting (Toggled)", 32).visibleIf(showStatus::isOn));
    private final TextSetting holdText = add(new TextSetting("holdText", "Holding Text", "Sprinting (Key Held)", 32).visibleIf(showStatus::isOn));
    private final BoolSetting stopInWater = add(new BoolSetting("water", "Pause in Water", false));

    private boolean toggled;
    private boolean forcing;

    public ToggleSprintModule() {
        super("togglesprint", "Toggle Sprint", "Tap sprint once instead of holding it.", Category.COMBAT, true, Anchor.BOTTOM_LEFT, 4, -4);
        tags("sprint", "auto sprint");
        labelStyle.withDefault("Value Only");
    }

    @Override
    public void onTick() {
        KeyMapping sprint = mc().options.keySprint;
        if (mode.is("Toggle")) {
            while (sprint.consumeClick()) toggled = !toggled;
        } else {
            toggled = true;
        }
        boolean blocked = stopInWater.isOn() && mc().player != null && mc().player.isInWater();
        if (toggled && !blocked) {
            sprint.setDown(true);
            forcing = true;
        } else if (forcing) {
            sprint.setDown(false);
            forcing = false;
        }
    }

    @Override
    protected void onDisable() {
        if (forcing && mc().options != null) mc().options.keySprint.setDown(false);
        forcing = false;
        toggled = false;
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (!showStatus.isOn() && !preview) return;
        if (toggled) out.add(new Line("Sprint", toggledText.get()));
        else if (preview) out.add(new Line("Sprint", toggledText.get()));
        else if (mc().player != null && mc().player.isSprinting()) out.add(new Line("Sprint", holdText.get()));
    }
}
