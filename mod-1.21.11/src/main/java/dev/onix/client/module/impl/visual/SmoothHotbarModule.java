package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.NumberSetting;

/** The hotbar's selection frame glides to the selected slot instead of jumping there. */
public class SmoothHotbarModule extends Module {
    private final NumberSetting speed = add(new NumberSetting("speed", "Speed", 14, 4, 40, 1)
            .describe("How fast the frame catches up (higher is snappier)."));

    private float x = Float.NaN;
    private long last;

    public SmoothHotbarModule() {
        super("smoothhotbar", "Smooth Hotbar", "The hotbar's selection frame glides to the selected slot.", Category.VISUAL, false);
        tags("hotbar", "smooth", "animation", "slot", "selection");
    }

    /** Called while the hotbar is drawn with the frame's real x; returns where to draw it this frame. */
    public int slide(int target) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0f : Math.min(0.1f, (now - last) / 1e9f);
        last = now;
        // a jump of more than 3 slots (scrolling from 1 to 9) is taken straight, like the vanilla hotbar wrapping
        if (Float.isNaN(x) || Math.abs(target - x) > 61) x = target;
        x += (target - x) * (1f - (float) Math.exp(-speed.get() * dt));
        if (Math.abs(target - x) < 0.3f) x = target;
        return Math.round(x);
    }

    @Override
    protected void onDisable() {
        x = Float.NaN;
        last = 0;
    }
}
