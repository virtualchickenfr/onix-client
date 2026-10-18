package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.performance.ParticleLimiterModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Particle Limiter. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void onix$limit(Particle particle, CallbackInfo ci) {
        if (OnixClient.MODULES == null) return;
        ParticleLimiterModule limiter = OnixClient.MODULES.get(ParticleLimiterModule.class);
        if (limiter != null && limiter.drop()) ci.cancel();
    }
}
