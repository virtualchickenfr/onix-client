package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.AtmosphereModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Sky draws its sky box instead of the vanilla sky disc; Atmosphere tints the vanilla sky. */
@Mixin(net.minecraft.client.renderer.SkyRenderer.class)
public abstract class SkyRendererMixin {
    @ModifyVariable(method = "renderSkyDisc", at = @At("HEAD"), argsOnly = true)
    private int onix$tintSky(int color) {
        if (OnixClient.MODULES == null) return color;
        AtmosphereModule atmosphere = OnixClient.MODULES.get(AtmosphereModule.class);
        return atmosphere != null && atmosphere.isEnabled() ? atmosphere.tintSky(color) : color;
    }

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void onix$customSky(int color, CallbackInfo ci) {
        if (dev.onix.client.render.SkyRenderer.render()) ci.cancel();
    }

    @Inject(method = {"renderSunriseAndSunset", "renderDarkDisc"}, at = @At("HEAD"), cancellable = true)
    private void onix$hideVanillaParts(CallbackInfo ci) {
        if (dev.onix.client.render.SkyRenderer.active()) ci.cancel();
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void onix$sunAndMoon(CallbackInfo ci) {
        if (dev.onix.client.render.SkyRenderer.active() && !dev.onix.client.render.SkyRenderer.sunAndMoon()) ci.cancel();
    }
}
