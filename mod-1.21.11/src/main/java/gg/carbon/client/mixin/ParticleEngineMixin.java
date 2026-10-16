package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.performance.PerformanceMods;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {
	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void carbon$limit(Particle particle, CallbackInfo ci) {
		if (CarbonClient.modules() != null && CarbonClient.modules().get(PerformanceMods.ParticleLimiter.class).drop()) ci.cancel();
	}
}
