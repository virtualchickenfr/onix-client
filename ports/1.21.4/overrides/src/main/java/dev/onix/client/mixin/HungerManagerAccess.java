package dev.onix.client.mixin;

import net.minecraft.entity.player.HungerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minecraft 1.21.2+: exhaustion has no public getter any more. */
@Mixin(HungerManager.class)
public interface HungerManagerAccess {
    @Accessor("exhaustion")
    float onix$exhaustion();
}
