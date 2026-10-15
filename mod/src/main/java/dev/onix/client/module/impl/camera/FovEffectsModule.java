package dev.onix.client.module.impl.camera;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.OptionOverride;
import net.minecraft.client.option.GameOptions;

/** Dynamic FOV strength and fixed FOV values, separately for first and third person. */
public class FovEffectsModule extends Module {
    private final NumberSetting effects = add(new NumberSetting("effects", "FOV Effects", 0, 0, 100, 5).unit("%")
            .describe("How much sprinting, speed potions and bows change your FOV. 0% keeps it steady."));

    {
        group("Field of View");
    }

    private final BoolSetting overrideFov = add(new BoolSetting("override", "Custom FOV", false));
    private final NumberSetting fov = add(new NumberSetting("fov", "FOV", 90, 30, 110, 1).unit("°").visibleIf(overrideFov::isOn));
    private final BoolSetting thirdPerson = add(new BoolSetting("thirdPerson", "Separate Third-Person FOV", false));
    private final NumberSetting thirdPersonFov = add(new NumberSetting("thirdPersonFov", "Third-Person FOV", 80, 30, 110, 1).unit("°")
            .visibleIf(thirdPerson::isOn));

    private final OptionOverride<Double> effectOption = new OptionOverride<>(GameOptions::getFovEffectScale);
    private final OptionOverride<Integer> fovOption = new OptionOverride<>(GameOptions::getFov);

    public FovEffectsModule() {
        super("fov", "FOV Effects", "Stop the FOV from changing when you sprint and set fixed FOVs.", Category.CAMERA, false);
        tags("dynamic fov", "field of view", "speed fov");
    }

    @Override
    public void onTick() {
        effectOption.apply(effects.get() / 100.0);
        boolean third = !mc().options.getPerspective().isFirstPerson();
        if (third && thirdPerson.isOn()) fovOption.apply(thirdPersonFov.getInt());
        else if (overrideFov.isOn()) fovOption.apply(fov.getInt());
        else fovOption.restore();
    }

    @Override
    protected void onDisable() {
        effectOption.restore();
        fovOption.restore();
    }

    @Override
    public void onShutdown() {
        onDisable();
    }
}
