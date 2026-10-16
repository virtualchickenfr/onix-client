package dev.onix.client.module.impl.combat;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.CombatTracker;
import java.util.Random;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Extra particles when you hit something. Only you see them. */
public class HitEffectsModule extends Module {
    private static final Random RANDOM = new Random();

    private final ModeSetting particle = add(new ModeSetting("particle", "Particle", "Crit", "Magic Crit", "Hearts", "Blossoms", "Sparks", "Flames", "Blood"));
    private final NumberSetting amount = add(new NumberSetting("amount", "Amount", 8, 1, 40, 1));
    private final NumberSetting spread = add(new NumberSetting("spread", "Speed", 0.3, 0.05, 1, 0.05));
    private final BoolSetting onlyPlayers = add(new BoolSetting("onlyPlayers", "Only on Players", false));

    public HitEffectsModule() {
        super("hiteffects", "Hit Effects", "Extra particles every time you hit.", Category.COMBAT, false);
        tags("particles", "crit");
    }

    @Override
    public void init() {
        CombatTracker.onHit(this::onHit);
    }

    private ParticleOptions effect() {
        return switch (particle.get()) {
            case "Magic Crit" -> ParticleTypes.ENCHANTED_HIT;
            case "Hearts" -> ParticleTypes.HEART;
            case "Blossoms" -> ParticleTypes.CHERRY_LEAVES;
            case "Sparks" -> ParticleTypes.ELECTRIC_SPARK;
            case "Flames" -> ParticleTypes.FLAME;
            case "Blood" -> ParticleTypes.DAMAGE_INDICATOR;
            default -> ParticleTypes.CRIT;
        };
    }

    private void onHit(Entity e) {
        if (!isEnabled() || mc().level == null) return;
        if (onlyPlayers.isOn() && !(e instanceof Player)) return;
        double s = spread.get();
        for (int i = 0; i < amount.getInt(); i++) {
            mc().level.addParticle(effect(),
                    e.getX() + (RANDOM.nextDouble() - 0.5) * e.getBbWidth(),
                    e.getY() + RANDOM.nextDouble() * e.getBbHeight(),
                    e.getZ() + (RANDOM.nextDouble() - 0.5) * e.getBbWidth(),
                    (RANDOM.nextDouble() - 0.5) * s, RANDOM.nextDouble() * s, (RANDOM.nextDouble() - 0.5) * s);
        }
    }
}
