package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.render.FxDraw;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;

/** A spirit rises when a totem saves someone: a spiral of coloured particles, with options for the vanilla effects. */
public class TotemSpiritModule extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Spiral", "Pillar", "Burst", "Halo"));
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFFD34D));
    private final BoolSetting rainbow = add(new BoolSetting("rainbow", "Rainbow", false));
    private final NumberSetting amount = add(new NumberSetting("amount", "Amount", 1.0, 0.3, 3.0, 0.1).unit("x"));
    private final BoolSetting hideVanilla = add(new BoolSetting("hideVanilla", "Hide Vanilla Particles", false));
    private final BoolSetting hideOverlay = add(new BoolSetting("hideOverlay", "Hide Totem on Screen", false).describe("The big totem that pops up when your own totem is used."));
    private final BoolSetting others = add(new BoolSetting("others", "Other Players Too", true));

    private record Spirit(Entity entity, int startTick) {}

    private final List<Spirit> spirits = new ArrayList<>();
    private int ticks;

    public TotemSpiritModule() {
        super("totemspirit", "Totem Spirit", "A rising spirit effect when a totem pops.", Category.VISUAL, false);
        tags("totem", "pop", "particles", "effect");
    }

    public boolean hidesVanillaParticles() {
        return isEnabled() && hideVanilla.isOn();
    }

    public boolean hidesOverlay() {
        return isEnabled() && hideOverlay.isOn();
    }

    public void onPop(Entity e) {
        if (!isEnabled() || e == null) return;
        if (!others.isOn() && e != mc().player) return;
        spirits.add(new Spirit(e, ticks));
        if (spirits.size() > 16) spirits.remove(0);
    }

    @Override
    public void onTick() {
        ticks++;
        if (mc().level == null) {
            spirits.clear();
            return;
        }
        spirits.removeIf(s -> ticks - s.startTick() > 30 || s.entity().isRemoved());
        for (Spirit s : spirits) emit(s.entity(), ticks - s.startTick());
    }

    private void emit(Entity e, int age) {
        float t = age / 30f;
        int n = Math.max(1, Math.round(4 * amount.getFloat()));
        double h = e.getBbHeight();
        for (int i = 0; i < n; i++) {
            int c = rainbow.isOn() ? FxDraw.rainbow(t + i * 0.08f) : color.color();
            var dust = new DustParticleOptions(c & 0xFFFFFF, 1.2f);
            double a = age * 0.55 + i * (Math.PI * 2 / n);
            switch (style.get()) {
                case "Pillar" -> mc().level.addParticle(dust, e.getX() + Math.cos(a) * 0.35, e.getY() + t * h * 1.6, e.getZ() + Math.sin(a) * 0.35, 0, 0.05, 0);
                case "Burst" -> {
                    if (age < 3) {
                        for (int k = 0; k < 6; k++) {
                            double b = Math.random() * Math.PI * 2, up = Math.random() * 0.4;
                            mc().level.addParticle(dust, e.getX(), e.getY() + h * 0.6, e.getZ(), Math.cos(b) * 0.3, up, Math.sin(b) * 0.3);
                        }
                    }
                }
                case "Halo" -> mc().level.addParticle(dust, e.getX() + Math.cos(a) * 0.5, e.getY() + h + 0.25 + 0.05 * Math.sin(age * 0.4), e.getZ() + Math.sin(a) * 0.5, 0, 0, 0);
                default -> {
                    double r = 0.6 * (1 - t) + 0.15;
                    mc().level.addParticle(dust, e.getX() + Math.cos(a) * r, e.getY() + t * h * 1.4, e.getZ() + Math.sin(a) * r, 0, 0.02, 0);
                }
            }
        }
        if (age == 0) {
            for (int k = 0; k < 10; k++) {
                double b = k * Math.PI / 5;
                mc().level.addParticle(ParticleTypes.END_ROD, e.getX(), e.getY() + h * 0.6, e.getZ(), Math.cos(b) * 0.15, 0.05, Math.sin(b) * 0.15);
            }
        }
    }
}
