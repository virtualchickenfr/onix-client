package dev.onix.client.module.impl.combat;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.TextSetting;
import net.minecraft.client.option.KeyBinding;

import java.util.List;

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
        KeyBinding sprint = mc().options.sprintKey;
        if (mode.is("Toggle")) {
            while (sprint.wasPressed()) toggled = !toggled;
        } else {
            toggled = true;
        }
        boolean blocked = stopInWater.isOn() && mc().player != null && mc().player.isTouchingWater();
        if (toggled && !blocked) {
            sprint.setPressed(true);
            forcing = true;
        } else if (forcing) {
            sprint.setPressed(false);
            forcing = false;
        }
    }

    @Override
    protected void onDisable() {
        if (forcing && mc().options != null) mc().options.sprintKey.setPressed(false);
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
