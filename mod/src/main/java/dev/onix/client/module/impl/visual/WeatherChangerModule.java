package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.OptionOverride;
import net.minecraft.sound.SoundCategory;

/** Client-side weather. Applied by WorldMixin; never touches the server. */
public class WeatherChangerModule extends Module {
    private final ModeSetting weather = add(new ModeSetting("weather", "Weather", "Clear", "Rain", "Thunder", "Server")
            .describe("\"Server\" keeps the real weather but lets you scale how strong it looks."));
    private final NumberSetting intensity = add(new NumberSetting("intensity", "Intensity", 100, 0, 100, 5).unit("%")
            .visibleIf(() -> !weather.is("Clear")));
    private final BoolSetting hideLightning = add(new BoolSetting("hideLightning", "Hide Lightning Flashes", false)
            .describe("No bright sky flashes during thunderstorms."));

    {
        group("Sound");
    }

    private final BoolSetting adjustSound = add(new BoolSetting("adjustSound", "Change Weather Volume", false));
    private final NumberSetting soundVolume = add(new NumberSetting("soundVolume", "Weather Volume", 30, 0, 100, 5).unit("%")
            .describe("Relative to your normal weather volume.").visibleIf(adjustSound::isOn));

    private final OptionOverride<Double> weatherSound = new OptionOverride<>(o -> o.getSoundVolumeOption(SoundCategory.WEATHER));

    public WeatherChangerModule() {
        super("weather", "Weather Changer", "Choose the weather you see – client-side only.", Category.VISUAL, false);
        tags("rain", "clear", "storm", "lightning");
    }

    public float rain(float server) {
        float k = intensity.getFloat() / 100f;
        return switch (weather.get()) {
            case "Clear" -> 0f;
            case "Server" -> server * k;
            default -> k;
        };
    }

    public float thunder(float server) {
        float k = intensity.getFloat() / 100f;
        return switch (weather.get()) {
            case "Thunder" -> k;
            case "Server" -> server * k;
            default -> 0f;
        };
    }

    @Override
    public void onTick() {
        if (hideLightning.isOn() && mc().world != null) mc().world.setLightningTicksLeft(0);
        if (adjustSound.isOn()) {
            Double own = weatherSound.original();
            if (own != null) weatherSound.apply(own * soundVolume.get() / 100.0);
        } else {
            weatherSound.restore();
        }
    }

    @Override
    protected void onDisable() {
        weatherSound.restore();
    }

    @Override
    public void onShutdown() {
        weatherSound.restore();
    }
}
