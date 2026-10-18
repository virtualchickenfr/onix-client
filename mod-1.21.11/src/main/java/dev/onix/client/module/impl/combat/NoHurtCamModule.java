package dev.onix.client.module.impl.combat;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.OptionOverride;
import net.minecraft.client.Options;

/** Less or no camera shake when you take damage, plus control over other screen distortions. */
public class NoHurtCamModule extends Module {
    private final NumberSetting intensity = add(new NumberSetting("intensity", "Shake Intensity", 0, 0, 100, 5).unit("%")
            .describe("0% removes the hurt shake completely; 100% is vanilla."));

    {
        group("Screen Effects");
    }

    private final BoolSetting effects = add(new BoolSetting("effects", "Adjust Screen Effects", false)
            .describe("Also tone down nausea and portal wobble and the Darkness pulse."));
    private final NumberSetting distortion = add(new NumberSetting("distortion", "Distortion", 0, 0, 100, 5).unit("%")
            .describe("Nausea and nether portal wobble.").visibleIf(effects::isOn));
    private final NumberSetting darkness = add(new NumberSetting("darkness", "Darkness Pulse", 50, 0, 100, 5).unit("%")
            .describe("How strongly the Darkness effect pulses.").visibleIf(effects::isOn));

    private final OptionOverride<Double> tilt = new OptionOverride<>(Options::damageTiltStrength);
    private final OptionOverride<Double> distortionOption = new OptionOverride<>(Options::screenEffectScale);
    private final OptionOverride<Double> darknessOption = new OptionOverride<>(Options::darknessEffectScale);

    public NoHurtCamModule() {
        super("nohurtcam", "No Hurt Cam", "Less camera shake when hit, and calmer screen effects.", Category.COMBAT, false);
        tags("hurt cam", "damage tilt", "shake", "nausea", "distortion");
    }

    @Override
    public void onTick() {
        tilt.apply(intensity.get() / 100.0);
        if (effects.isOn()) {
            distortionOption.apply(distortion.get() / 100.0);
            darknessOption.apply(darkness.get() / 100.0);
        } else {
            distortionOption.restore();
            darknessOption.restore();
        }
    }

    @Override
    protected void onDisable() {
        tilt.restore();
        distortionOption.restore();
        darknessOption.restore();
    }

    @Override
    public void onShutdown() {
        onDisable();
    }
}
