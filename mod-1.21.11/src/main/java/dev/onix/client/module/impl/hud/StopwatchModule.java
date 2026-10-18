package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.KeybindSetting;

import java.util.List;
import java.util.Locale;

public class StopwatchModule extends TextHudModule {
    private final KeybindSetting startKey = add(new KeybindSetting("startKey", "Start / Stop Key", KeybindSetting.NONE));
    private final KeybindSetting resetKey = add(new KeybindSetting("resetKey", "Reset Key", KeybindSetting.NONE));
    private final BoolSetting millis = add(new BoolSetting("millis", "Show Milliseconds", true));
    private final ColorSetting runningColor = add(new ColorSetting("runningColor", "Running Color", 0xFF34D399));

    private long started;
    private long elapsed;
    private boolean running;

    public StopwatchModule() {
        super("stopwatch", "Stopwatch", "Time anything with a key – parkour, speedruns, farms.", Category.HUD, false, Anchor.TOP_CENTER, 0, 30);
        tags("timer", "speedrun");
    }

    private long current() {
        return running ? elapsed + System.currentTimeMillis() - started : elapsed;
    }

    @Override
    public void onTick() {
        if (startKey.poll()) {
            if (running) {
                elapsed += System.currentTimeMillis() - started;
                running = false;
            } else {
                started = System.currentTimeMillis();
                running = true;
            }
        }
        if (resetKey.poll()) {
            running = false;
            elapsed = 0;
        }
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        long ms = current();
        String value = String.format(Locale.ROOT, "%d:%02d", ms / 60000, (ms / 1000) % 60);
        if (millis.isOn()) value += String.format(Locale.ROOT, ".%03d", ms % 1000);
        out.add(new Line("Stopwatch", value, running ? runningColor.color() : 0));
    }

    @Override
    protected String sampleValue() {
        return "00:00.000";
    }
}
