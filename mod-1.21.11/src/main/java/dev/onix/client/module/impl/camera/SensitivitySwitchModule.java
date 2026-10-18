package dev.onix.client.module.impl.camera;

import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.OptionOverride;
import net.minecraft.client.Options;

/** A second mouse sensitivity (and mouse options) on a key. */
public class SensitivitySwitchModule extends Module {
    private final KeybindSetting key = add(new KeybindSetting("key", "Key", KeybindSetting.NONE));
    private final ModeSetting mode = add(new ModeSetting("mode", "Activation", "Hold", "Toggle"));
    private final NumberSetting sensitivity = add(new NumberSetting("sensitivity", "Sensitivity", 50, 1, 200, 1).unit("%")
            .describe("100% is Minecraft's default; the vanilla slider goes up to 200%."));
    private final BoolSetting notify = add(new BoolSetting("notify", "Notify When Toggled", true).visibleIf(() -> mode.is("Toggle")));

    {
        group("While Active");
    }

    private final BoolSetting rawInput = add(new BoolSetting("rawInput", "Force Raw Input", false));
    private final BoolSetting invert = add(new BoolSetting("invert", "Invert Mouse Y", false));

    private final OptionOverride<Double> sensitivityOption = new OptionOverride<>(Options::sensitivity);
    private final OptionOverride<Boolean> rawOption = new OptionOverride<>(Options::rawMouseInput);
    private final OptionOverride<Boolean> invertOption = new OptionOverride<>(Options::invertMouseY);
    private boolean toggled;

    public SensitivitySwitchModule() {
        super("sensitivity", "Sensitivity Switch", "A second mouse sensitivity on a key.", Category.CAMERA, false);
        tags("mouse", "dpi", "aim");
    }

    @Override
    public void onTick() {
        boolean pressed = key.poll();
        boolean want;
        if (mode.is("Toggle")) {
            if (pressed) {
                toggled = !toggled;
                if (notify.isOn()) Notifications.show("Sensitivity", toggled ? sensitivity.getInt() + "%" : "Normal");
            }
            want = toggled;
        } else {
            want = key.isDown();
        }
        if (want) {
            sensitivityOption.apply(sensitivity.get() / 200.0);
            if (rawInput.isOn()) rawOption.apply(true); else rawOption.restore();
            if (invert.isOn()) invertOption.apply(true); else invertOption.restore();
        } else {
            restoreAll();
        }
    }

    private void restoreAll() {
        sensitivityOption.restore();
        rawOption.restore();
        invertOption.restore();
    }

    @Override
    protected void onDisable() {
        restoreAll();
        toggled = false;
    }

    @Override
    public void onShutdown() {
        restoreAll();
    }
}
