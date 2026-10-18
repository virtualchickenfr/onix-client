package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.AtmosphereModule;
import dev.onix.client.render.SkyRenderer;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The fog fades into the Custom Sky box and follows the Atmosphere tint. */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class FogColorMixin {
    @Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
    private void onix$fogColor(CallbackInfoReturnable<Integer> cir) {
        if (OnixClient.MODULES == null) return;
        Integer horizon = SkyRenderer.horizonColor();
        if (horizon != null) cir.setReturnValue(horizon);
        AtmosphereModule atmosphere = OnixClient.MODULES.get(AtmosphereModule.class);
        if (atmosphere != null && atmosphere.isEnabled()) cir.setReturnValue(atmosphere.tintSky(cir.getReturnValue()));
    }
}
