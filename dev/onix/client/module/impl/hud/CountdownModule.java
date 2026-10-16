package dev.onix.client.module.impl.hud;

import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.List;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

public class CountdownModule extends TextHudModule {
    private final NumberSetting minutes = add(new NumberSetting("minutes", "Minutes", 5, 0, 120, 1));
    private final NumberSetting seconds = add(new NumberSetting("seconds", "Seconds", 0, 0, 59, 1));
    private final KeybindSetting startKey = add(new KeybindSetting("startKey", "Start / Pause Key", KeybindSetting.NONE));
    private final KeybindSetting resetKey = add(new KeybindSetting("resetKey", "Reset Key", KeybindSetting.NONE));
    private final BoolSetting sound = add(new BoolSetting("sound", "Sound When Done", true));
    private final BoolSetting notify = add(new BoolSetting("notify", "Notification When Done", true));
    private final ColorSetting endingColor = add(new ColorSetting("endingColor", "Last 10 Seconds Color", 0xFFFF5A5F));

    private long remaining = -1;
    private long lastTick;
    private boolean running;

    public CountdownModule() {
        super("countdown", "Countdown", "A timer that counts down and alerts you.", Category.HUD, false, Anchor.TOP_CENTER, 0, 48);
        tags("timer", "alarm");
    }

    private long total() {
        return (minutes.getInt() * 60L + seconds.getInt()) * 1000L;
    }

    @Override
    public void onTick() {
        long now = System.currentTimeMillis();
        if (remaining < 0) remaining = total();
        if (startKey.poll()) {
            if (remaining <= 0) remaining = total();
            running = !running;
            lastTick = now;
        }
        if (resetKey.poll()) {
            running = false;
            remaining = total();
        }
        if (running) {
            remaining -= now - lastTick;
            lastTick = now;
            if (remaining <= 0) {
                remaining = 0;
                running = false;
                if (sound.isOn()) mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1f, 1f));
                if (notify.isOn()) Notifications.show("Countdown", "Time is up");
            }
        }
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        long ms = remaining < 0 ? total() : remaining;
        long s = (ms + 999) / 1000;
        out.add(new Line("Timer", String.format("%d:%02d", s / 60, s % 60), running && s <= 10 ? endingColor.color() : 0));
    }

    @Override
    protected String sampleValue() {
        return "00:00";
    }
}
