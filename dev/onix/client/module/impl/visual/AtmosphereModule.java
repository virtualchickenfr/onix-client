package dev.onix.client.module.impl.visual;

import dev.onix.client.gui.Theme;
import dev.onix.client.gui.ThemeDef;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.module.OverlayRenderer;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.Random;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Sky tint, color grading and ambient particles that match the theme. Only you see it. */
public class AtmosphereModule extends Module implements OverlayRenderer {
    private static final Random RANDOM = new Random();

    private final ModeSetting palette = add(new ModeSetting("palette", "Palette", "Theme", "Sunset", "Night", "Sakura", "Arctic"));
    private final BoolSetting tintSky = add(new BoolSetting("sky", "Tint Sky", true));
    private final NumberSetting skyStrength = add(new NumberSetting("skyStrength", "Sky Strength", 45, 0, 100, 5).unit("%").visibleIf(tintSky::isOn));
    private final BoolSetting filter = add(new BoolSetting("filter", "Color Grading", true));
    private final NumberSetting filterStrength = add(new NumberSetting("filterStrength", "Grading Strength", 35, 0, 100, 5).unit("%").visibleIf(filter::isOn));
    private final BoolSetting particles = add(new BoolSetting("particles", "Ambient Particles", true));
    private final ModeSetting particleType = add(new ModeSetting("particleType", "Particle Type", "Theme", "Blossoms", "Snow", "Embers", "Sparks", "Spores").visibleIf(particles::isOn));
    private final NumberSetting density = add(new NumberSetting("density", "Density", 4, 1, 15, 1).visibleIf(particles::isOn));
    private final NumberSetting radius = add(new NumberSetting("radius", "Radius", 14, 6, 32, 1).unit("blocks").visibleIf(particles::isOn));

    public AtmosphereModule() {
        super("atmosphere", "Atmosphere", "Animated sky tint, color grading and ambient particles.", Category.VISUAL, false);
        tags("ambience", "sky", "particles", "mood");
    }

    private int skyColor() {
        return switch (palette.get()) {
            case "Sunset" -> 0xFFFF8A3C;
            case "Night" -> 0xFF3B4BA8;
            case "Sakura" -> 0xFFE58FCB;
            case "Arctic" -> 0xFFA9E4F0;
            default -> Theme.current().sky();
        };
    }

    /** Called from ClientWorldMixin. */
    public Vec3 tintSky(Vec3 original) {
        if (!isEnabled() || !tintSky.isOn()) return original;
        int sky = skyColor();
        double t = skyStrength.get() / 100.0 * 0.7;
        double brightness = Math.max(0.2, original.length() / 1.2);
        return new Vec3(
                Mth.lerp(t, original.x, Theme.red(sky) * brightness),
                Mth.lerp(t, original.y, Theme.green(sky) * brightness),
                Mth.lerp(t, original.z, Theme.blue(sky) * brightness));
    }

    @Override
    public void renderOverlay(GuiGraphics ctx) {
        if (!filter.isOn() || mc().screen != null && mc().screen.isPauseScreen()) return;
        int sky = skyColor();
        float a = filterStrength.getFloat() / 100f;
        ctx.fillGradient(0, 0, ctx.guiWidth(), ctx.guiHeight(),
                Theme.withAlpha(sky, (int) (70 * a)), Theme.withAlpha(sky, (int) (15 * a)));
    }

    private ParticleOptions particle() {
        String type = particleType.get();
        if (type.equals("Theme")) {
            ThemeDef.Ambient ambient = Theme.current().ambient();
            type = switch (ambient) {
                case PETALS -> "Blossoms";
                case SNOW -> "Snow";
                case EMBERS -> "Embers";
                default -> "Sparks";
            };
        }
        return switch (type) {
            case "Blossoms" -> ParticleTypes.CHERRY_LEAVES;
            case "Snow" -> ParticleTypes.SNOWFLAKE;
            case "Embers" -> ParticleTypes.WAX_ON;
            case "Spores" -> ParticleTypes.SPORE_BLOSSOM_AIR;
            default -> ParticleTypes.END_ROD;
        };
    }

    @Override
    public void onTick() {
        if (!particles.isOn() || mc().level == null || mc().player == null || mc().isPaused()) return;
        if (mc().options.particles().get() == ParticleStatus.MINIMAL) return;
        ParticleOptions type = particle();
        boolean rising = type == ParticleTypes.WAX_ON || type == ParticleTypes.END_ROD;
        Vec3 p = mc().player.position();
        double r = radius.get();
        for (int i = 0; i < density.getInt(); i++) {
            double x = p.x + (RANDOM.nextDouble() - 0.5) * r * 2;
            double z = p.z + (RANDOM.nextDouble() - 0.5) * r * 2;
            double y = rising ? p.y + RANDOM.nextDouble() * 4 - 1 : p.y + 5 + RANDOM.nextDouble() * 8;
            double vy = rising ? 0.02 + RANDOM.nextDouble() * 0.03 : -0.03;
            mc().level.addParticle(type, x, y, z, (RANDOM.nextDouble() - 0.5) * 0.02, vy, (RANDOM.nextDouble() - 0.5) * 0.02);
        }
    }
}
