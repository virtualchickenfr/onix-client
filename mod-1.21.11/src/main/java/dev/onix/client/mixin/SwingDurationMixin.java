package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.SwingAnimationModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Swing Animation speed: only the local player's arm animation; attacks are timed by the attack cooldown. */
@Mixin(LivingEntity.class)
public abstract class SwingDurationMixin {
    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
    private void onix$swingSpeed(CallbackInfoReturnable<Integer> cir) {
        if (OnixClient.MODULES == null || (Object) this != Minecraft.getInstance().player) return;
        SwingAnimationModule swing = OnixClient.MODULES.get(SwingAnimationModule.class);
        if (swing != null && swing.isEnabled()) cir.setReturnValue(swing.duration(cir.getReturnValue()));
    }
}
