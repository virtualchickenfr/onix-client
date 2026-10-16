package dev.onix.client.module.impl.combat;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.CombatTracker;
import java.util.Random;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** A satisfying sound on every hit. */
public class HitSoundModule extends Module {
    private static final Random RANDOM = new Random();

    private final ModeSetting sound = add(new ModeSetting("sound", "Sound", "Ding", "Click", "Bass", "Pling", "Bell", "Crit", "Arrow Hit"));
    private final NumberSetting volume = add(new NumberSetting("volume", "Volume", 60, 0, 100, 5).unit("%"));
    private final NumberSetting pitch = add(new NumberSetting("pitch", "Pitch", 1.2, 0.5, 2, 0.05));
    private final BoolSetting comboPitch = add(new BoolSetting("comboPitch", "Rise With Combo", true)
            .describe("Pitch goes up a little with every hit in a combo."));
    private final BoolSetting randomize = add(new BoolSetting("randomize", "Slight Variation", false));
    private final BoolSetting onlyPlayers = add(new BoolSetting("onlyPlayers", "Only on Players", false));
    private final BoolSetting onlyDamaging = add(new BoolSetting("onlyDamaging", "Only Counted Hits", true)
            .describe("Skip hits during the target's invulnerability frames."));

    public HitSoundModule() {
        super("hitsound", "Hit Sound", "Plays a sound whenever you land a hit.", Category.COMBAT, false);
        tags("hit marker", "ding");
    }

    @Override
    public void init() {
        CombatTracker.onHit(this::onHit);
    }

    private SoundEvent event() {
        return switch (sound.get()) {
            case "Click" -> SoundEvents.NOTE_BLOCK_HAT.value();
            case "Bass" -> SoundEvents.NOTE_BLOCK_BASS.value();
            case "Pling" -> SoundEvents.NOTE_BLOCK_PLING.value();
            case "Bell" -> SoundEvents.NOTE_BLOCK_BELL.value();
            case "Crit" -> SoundEvents.PLAYER_ATTACK_CRIT;
            case "Arrow Hit" -> SoundEvents.ARROW_HIT_PLAYER;
            default -> SoundEvents.NOTE_BLOCK_CHIME.value();
        };
    }

    private void onHit(Entity e) {
        if (!isEnabled()) return;
        if (onlyPlayers.isOn() && !(e instanceof Player)) return;
        if (onlyDamaging.isOn() && e instanceof LivingEntity living && living.hurtTime > 0) return;
        float p = pitch.getFloat();
        if (comboPitch.isOn()) p += Math.min(10, CombatTracker.combo()) * 0.04f;
        if (randomize.isOn()) p += (RANDOM.nextFloat() - 0.5f) * 0.1f;
        mc().getSoundManager().play(SimpleSoundInstance.forUI(event(), p, volume.getFloat() / 100f));
    }
}
