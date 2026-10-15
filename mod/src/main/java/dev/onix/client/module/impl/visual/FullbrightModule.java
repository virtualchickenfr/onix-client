package dev.onix.client.module.impl.visual;

import dev.onix.client.mixin.SimpleOptionAccessor;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

/** Raises brightness beyond the normal maximum (or uses a client-side night vision effect). */
public class FullbrightModule extends Module {
    private final ModeSetting mode = add(new ModeSetting("mode", "Method", "Gamma", "Night Vision")
            .describe("Gamma brightens everything evenly. Night Vision looks like the potion and is only visible to you."));
    private final NumberSetting strength = add(new NumberSetting("strength", "Brightness", 1500, 100, 1500, 50).unit("%")
            .describe("100% equals the vanilla \"Bright\" setting.").visibleIf(() -> mode.is("Gamma")));
    private final BoolSetting smooth = add(new BoolSetting("smooth", "Smooth Fade", true).visibleIf(() -> mode.is("Gamma")));

    {
        group("Conditions");
    }

    private final BoolSetting onlyWhenDark = add(new BoolSetting("onlyWhenDark", "Only When Dark", false)
            .describe("Only brightens while the light level where you stand is low."));
    private final NumberSetting darkBelow = add(new NumberSetting("darkBelow", "Dark Below Light Level", 7, 1, 15, 1)
            .visibleIf(onlyWhenDark::isOn));

    private Double original;
    private double current = -1;

    public FullbrightModule() {
        super("fullbright", "Fullbright", "See in caves and at night without torches.", Category.VISUAL, false);
        restricted("Seeing in the dark is an advantage in some game modes; some servers and tournaments forbid it.");
        tags("gamma", "brightness", "night vision");
    }

    private boolean wanted() {
        if (!onlyWhenDark.isOn() || mc().world == null || mc().player == null) return true;
        return mc().world.getLightLevel(mc().player.getBlockPos()) < darkBelow.getInt();
    }

    @Override
    public void onTick() {
        boolean active = wanted();
        if (mode.is("Night Vision")) {
            fadeGamma(false);
            if (active) applyNightVision(); else removeNightVision();
        } else {
            removeNightVision();
            fadeGamma(active);
        }
    }

    private void fadeGamma(boolean active) {
        SimpleOption<Double> gamma = mc().options.getGamma();
        if (original == null) {
            if (!active) return;
            original = gamma.getValue();
            current = original;
        }
        double target = active ? strength.get() / 100.0 : original;
        current = smooth.isOn() ? current + (target - current) * 0.25 : target;
        if (Math.abs(current - target) < 0.01) current = target;
        set(gamma, current);
        if (!active && current == original) restoreGamma();
    }

    private static boolean ours(StatusEffectInstance effect) {
        return effect != null && effect.isAmbient() && !effect.shouldShowParticles() && !effect.shouldShowIcon();
    }

    private void applyNightVision() {
        ClientPlayerEntity player = mc().player;
        if (player == null) return;
        StatusEffectInstance effect = player.getStatusEffect(StatusEffects.NIGHT_VISION);
        if (effect == null || (ours(effect) && effect.getDuration() < 300)) {
            player.setStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 420, 0, true, false, false), null);
        }
    }

    private void removeNightVision() {
        ClientPlayerEntity player = mc().player;
        if (player != null && ours(player.getStatusEffect(StatusEffects.NIGHT_VISION))) {
            player.removeStatusEffect(StatusEffects.NIGHT_VISION);
        }
    }

    @Override
    protected void onDisable() {
        restoreGamma();
        removeNightVision();
    }

    @Override
    public void onShutdown() {
        restoreGamma();
    }

    private void restoreGamma() {
        if (original != null && mc().options != null) {
            set(mc().options.getGamma(), original);
            original = null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void set(SimpleOption<Double> option, double value) {
        ((SimpleOptionAccessor<Double>) (Object) option).onix$setValue(value);
    }
}
