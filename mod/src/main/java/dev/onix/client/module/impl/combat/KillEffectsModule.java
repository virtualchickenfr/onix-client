package dev.onix.client.module.impl.combat;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.CombatTracker;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.Random;

/** A little celebration when your target dies. Only you see and hear it. */
public class KillEffectsModule extends Module {
    private static final Random RANDOM = new Random();

    private final ModeSetting effect = add(new ModeSetting("effect", "Effect", "Totem", "Lightning", "Explosion", "Hearts", "Souls", "None"));
    private final ModeSetting sound = add(new ModeSetting("sound", "Sound", "Level Up", "Thunder", "Explosion", "Orb", "Anvil", "None"));
    private final NumberSetting volume = add(new NumberSetting("volume", "Volume", 80, 0, 100, 5).unit("%").visibleIf(() -> !sound.is("None")));
    private final NumberSetting pitch = add(new NumberSetting("pitch", "Pitch", 1, 0.5, 2, 0.05).visibleIf(() -> !sound.is("None")));
    private final BoolSetting onlyPlayers = add(new BoolSetting("onlyPlayers", "Only on Players", false));

    public KillEffectsModule() {
        super("killeffects", "Kill Effects", "Particles and a sound when your target dies.", Category.COMBAT, false);
        tags("kill sound", "death effect");
    }

    @Override
    public void init() {
        CombatTracker.onKill(this::onKill);
    }

    private SoundEvent soundEvent() {
        return switch (sound.get()) {
            case "Thunder" -> SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER;
            case "Explosion" -> SoundEvents.ENTITY_GENERIC_EXPLODE.value();
            case "Orb" -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP;
            case "Anvil" -> SoundEvents.BLOCK_ANVIL_LAND;
            case "None" -> null;
            default -> SoundEvents.ENTITY_PLAYER_LEVELUP;
        };
    }

    private void onKill(Entity e) {
        if (!isEnabled() || mc().world == null) return;
        if (onlyPlayers.isOn() && !(e instanceof PlayerEntity)) return;
        switch (effect.get()) {
            case "Totem" -> mc().particleManager.addEmitter(e, ParticleTypes.TOTEM_OF_UNDYING, 30);
            case "Lightning" -> {
                for (int i = 0; i < 30; i++) {
                    mc().world.addParticle(ParticleTypes.ELECTRIC_SPARK, e.getX() + (RANDOM.nextDouble() - 0.5) * 0.6,
                            e.getY() + i * 0.25, e.getZ() + (RANDOM.nextDouble() - 0.5) * 0.6, 0, 0, 0);
                }
            }
            case "Explosion" -> mc().world.addParticle(ParticleTypes.EXPLOSION, e.getX(), e.getY() + 1, e.getZ(), 0, 0, 0);
            case "Hearts" -> burst(e, ParticleTypes.HEART);
            case "Souls" -> burst(e, ParticleTypes.SOUL);
            default -> {}
        }
        SoundEvent s = soundEvent();
        if (s != null) mc().getSoundManager().play(PositionedSoundInstance.master(s, pitch.getFloat(), volume.getFloat() / 100f));
    }

    private void burst(Entity e, net.minecraft.particle.ParticleEffect type) {
        for (int i = 0; i < 16; i++) {
            mc().world.addParticle(type, e.getX() + (RANDOM.nextDouble() - 0.5) * e.getWidth(), e.getY() + RANDOM.nextDouble() * e.getHeight(),
                    e.getZ() + (RANDOM.nextDouble() - 0.5) * e.getWidth(), (RANDOM.nextDouble() - 0.5) * 0.2, 0.15, (RANDOM.nextDouble() - 0.5) * 0.2);
        }
    }
}
