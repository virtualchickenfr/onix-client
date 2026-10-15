package dev.onix.client.module.impl.performance;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.OptionOverride;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.sound.SoundCategory;

import java.util.EnumMap;
import java.util.Map;

/** Turns the game quieter while it is in the background, with a smooth fade. */
public class BackgroundMuteModule extends Module {
    private final NumberSetting volume = add(new NumberSetting("volume", "Background Volume", 0, 0, 100, 5).unit("%")
            .describe("Volume relative to your normal settings while the game is in the background."));
    private final ModeSetting sounds = add(new ModeSetting("sounds", "Affects", "Everything", "Music Only", "All but Music"));
    private final ModeSetting trigger = add(new ModeSetting("trigger", "When", "Unfocused", "Unfocused or Paused")
            .describe("\"Paused\" means the Esc menu is open."));
    private final NumberSetting fade = add(new NumberSetting("fade", "Fade Time", 0.5, 0, 3, 0.1).unit("s"));

    private final Map<SoundCategory, OptionOverride<Double>> overrides = new EnumMap<>(SoundCategory.class);
    private double factor = 1;

    public BackgroundMuteModule() {
        super("backgroundmute", "Background Mute", "Mute or lower sound when you tab out.", Category.PERFORMANCE, false);
        tags("sound", "volume", "alt tab");
        for (SoundCategory c : SoundCategory.values()) overrides.put(c, new OptionOverride<>(o -> o.getSoundVolumeOption(c)));
    }

    private boolean affects(SoundCategory c) {
        return switch (sounds.get()) {
            case "Music Only" -> c == SoundCategory.MUSIC || c == SoundCategory.RECORDS;
            case "All but Music" -> c != SoundCategory.MASTER && c != SoundCategory.MUSIC && c != SoundCategory.RECORDS;
            default -> c == SoundCategory.MASTER;
        };
    }

    @Override
    public void onTick() {
        boolean background = !mc().isWindowFocused()
                || (trigger.is("Unfocused or Paused") && mc().currentScreen instanceof GameMenuScreen);
        double target = background ? volume.get() / 100.0 : 1.0;
        double step = fade.get() <= 0 ? 1.0 : 0.05 / fade.get();
        factor = target < factor ? Math.max(target, factor - step) : Math.min(target, factor + step);

        for (Map.Entry<SoundCategory, OptionOverride<Double>> e : overrides.entrySet()) {
            OptionOverride<Double> o = e.getValue();
            if (!affects(e.getKey()) || factor >= 0.999) {
                o.restore();
                continue;
            }
            Double original = o.original();
            if (original != null) o.apply(original * factor);
        }
    }

    @Override
    protected void onDisable() {
        overrides.values().forEach(OptionOverride::restore);
        factor = 1;
    }

    @Override
    public void onShutdown() {
        onDisable();
    }
}
