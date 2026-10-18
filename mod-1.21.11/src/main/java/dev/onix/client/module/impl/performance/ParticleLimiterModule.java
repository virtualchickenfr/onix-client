package dev.onix.client.module.impl.performance;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.NumberSetting;

import java.util.concurrent.ThreadLocalRandom;

/** From Carbon: spawns only part of the particles. */
public class ParticleLimiterModule extends Module {
    private final NumberSetting amount = add(new NumberSetting("amount", "Particle Amount", 50, 0, 100, 5).unit("%")
            .describe("Share of particles that are still spawned."));

    public ParticleLimiterModule() {
        super("particlelimiter", "Particle Limiter", "Reduces the number of particles for more FPS (from Carbon).", Category.PERFORMANCE, false);
        tags("particles", "fps", "carbon");
    }

    public boolean drop() {
        return isEnabled() && ThreadLocalRandom.current().nextInt(100) >= amount.getInt();
    }
}
