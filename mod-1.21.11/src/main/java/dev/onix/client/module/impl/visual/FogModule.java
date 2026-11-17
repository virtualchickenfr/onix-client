package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;

/**
 * Fog and clouds: how far the distance fog starts, how thick environment fog is (water, lava, nether, weather;
 * blindness and darkness are never changed), and how far clouds are drawn.
 */
public class FogModule extends Module {
    private final ModeSetting distance = add(new ModeSetting("distance", "Distance Fog", "Softer", "Vanilla", "Off")
            .describe("The fog at the edge of your render distance. Softer starts it earlier and fades it longer."));
    private final NumberSetting environment = add(new NumberSetting("environment", "Environment Fog", 100, 0, 200, 5).unit("%")
            .describe("Thickness of water, lava, nether and weather fog. 0 % = clear, 200 % = twice as thick."));
    private final NumberSetting clouds = add(new NumberSetting("clouds", "Cloud Distance", 100, 25, 300, 25).unit("%")
            .describe("How far away clouds are still drawn."));

    public FogModule() {
        super("fog", "Fog & Clouds", "Softer or no distance fog, thinner environment fog and farther clouds.", Category.VISUAL, false);
        tags("fog", "clouds", "atmosphere", "nether", "water", "view distance");
    }

    /** v = environmental start, environmental end, render distance start, render distance end, sky end, cloud end. */
    public void adjust(float[] v) {
        float env = environment.getFloat() / 100f;
        // blindness and darkness stay as the game makes them: clearing them would be an unfair advantage
        var p = mc().player;
        if (p != null && (p.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) || p.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS))) env = 1f;
        if (Math.abs(env - 1f) < 0.01f) {
            // unchanged
        } else if (env <= 0.01f) {
            v[0] = Float.MAX_VALUE;
            v[1] = Float.MAX_VALUE;
        } else if (v[1] < 1e6f) {
            v[0] /= env;
            v[1] /= env;
        }
        switch (distance.get()) {
            case "Off" -> {
                v[2] = Float.MAX_VALUE;
                v[3] = Float.MAX_VALUE;
            }
            case "Softer" -> v[2] = Math.max(0f, v[3] * 0.55f);
            default -> {}
        }
        v[5] *= clouds.getFloat() / 100f;
    }
}
