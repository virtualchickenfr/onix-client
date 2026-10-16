package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.Random;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/** A particle trail behind you. Visible in third person; only you see it. */
public class TrailModule extends Module {
    private static final Random RANDOM = new Random();

    private final ModeSetting type = add(new ModeSetting("type", "Particle", "Blossoms", "Hearts", "Sparks", "Flames", "Soul Flames", "Notes", "Glow", "Bubbles"));
    private final NumberSetting density = add(new NumberSetting("density", "Density", 2, 1, 10, 1).describe("Particles per tick."));
    private final NumberSetting spread = add(new NumberSetting("spread", "Spread", 0.4, 0, 1.5, 0.05).unit("blocks"));
    private final NumberSetting height = add(new NumberSetting("height", "Height", 0.15, 0, 2, 0.05).unit("blocks"));
    private final BoolSetting onlyMoving = add(new BoolSetting("moving", "Only While Moving", true));
    private final BoolSetting onlyGround = add(new BoolSetting("ground", "Only on Ground", false));

    private int tick;

    public TrailModule() {
        super("trail", "Trail", "Leave a particle trail behind you.", Category.VISUAL, false);
        tags("particles", "cosmetic");
    }

    private ParticleOptions effect() {
        return switch (type.get()) {
            case "Hearts" -> ParticleTypes.HEART;
            case "Sparks" -> ParticleTypes.ELECTRIC_SPARK;
            case "Flames" -> ParticleTypes.SMALL_FLAME;
            case "Soul Flames" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "Notes" -> ParticleTypes.NOTE;
            case "Glow" -> ParticleTypes.GLOW;
            case "Bubbles" -> ParticleTypes.BUBBLE_POP;
            default -> ParticleTypes.CHERRY_LEAVES;
        };
    }

    @Override
    public void onTick() {
        var p = mc().player;
        if (p == null || mc().level == null || mc().isPaused()) return;
        double dx = p.getX() - p.xo;
        double dz = p.getZ() - p.zo;
        if (onlyMoving.isOn() && dx * dx + dz * dz < 0.0004) return;
        if (onlyGround.isOn() && !p.onGround()) return;
        tick++;
        boolean slow = type.is("Hearts") || type.is("Notes");
        if (slow && tick % 4 != 0) return;
        double s = spread.get();
        for (int i = 0; i < (slow ? 1 : density.getInt()); i++) {
            double x = p.xo + (RANDOM.nextDouble() - 0.5) * s;
            double z = p.zo + (RANDOM.nextDouble() - 0.5) * s;
            double vx = type.is("Notes") ? RANDOM.nextInt(24) / 24.0 : 0;
            mc().level.addParticle(effect(), x, p.getY() + height.get(), z, vx, 0.01, 0);
        }
    }
}
