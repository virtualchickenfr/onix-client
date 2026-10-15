package dev.onix.client.module.impl.performance;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.OptionOverride;
import net.minecraft.client.option.GameOptions;

/** Entity render distance, optionally lowered automatically when the frame rate drops. */
public class EntityDistanceModule extends Module {
    private final NumberSetting distance = add(new NumberSetting("distance", "Entity Distance", 75, 50, 500, 25).unit("%"));

    {
        group("Adaptive");
    }

    private final BoolSetting adaptive = add(new BoolSetting("adaptive", "Adapt to Frame Rate", false)
            .describe("Lowers the distance step by step while FPS is below the target, and raises it again when there is headroom."));
    private final NumberSetting targetFps = add(new NumberSetting("targetFps", "Target FPS", 90, 30, 240, 10).unit("fps").visibleIf(adaptive::isOn));
    private final NumberSetting minimum = add(new NumberSetting("minimum", "Lowest Distance", 50, 50, 500, 25).unit("%").visibleIf(adaptive::isOn));

    private final OptionOverride<Double> option = new OptionOverride<>(GameOptions::getEntityDistanceScaling);
    private double current = -1;
    private int ticks;

    public EntityDistanceModule() {
        super("entitydistance", "Entity Distance", "Render entities closer or further away.", Category.PERFORMANCE, false);
        tags("entities", "render distance", "lag", "fps");
    }

    @Override
    public void onTick() {
        double max = distance.get();
        if (!adaptive.isOn() || current < 0) current = max;
        if (adaptive.isOn() && ++ticks >= 40) {
            ticks = 0;
            int fps = mc().getCurrentFps();
            double low = Math.min(minimum.get(), max);
            if (fps < targetFps.get() - 5) current = Math.max(low, current - 25);
            else if (fps > targetFps.get() + 20) current = Math.min(max, current + 25);
        }
        current = Math.min(current, max);
        option.apply(current / 100.0);
    }

    @Override
    protected void onDisable() {
        option.restore();
        current = -1;
    }

    @Override
    public void onShutdown() {
        onDisable();
    }
}
