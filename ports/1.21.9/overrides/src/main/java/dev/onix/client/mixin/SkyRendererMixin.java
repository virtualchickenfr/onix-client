package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.AtmosphereModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.21.10 and older: Custom Sky replaces the sky disc (r, g, b floats); Atmosphere tints it. */
@Mixin(net.minecraft.client.renderer.SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Unique
    private boolean onix$tinting;

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void onix$skyDisc(float r, float g, float b, CallbackInfo ci) {
        if (dev.onix.client.render.SkyRenderer.render()) {
            ci.cancel();
            return;
        }
        if (onix$tinting || OnixClient.MODULES == null) return;
        AtmosphereModule atmosphere = OnixClient.MODULES.get(AtmosphereModule.class);
        if (atmosphere == null || !atmosphere.isEnabled()) return;
        int color = 0xFF000000 | Math.round(Math.max(0, Math.min(1, r)) * 255) << 16 | Math.round(Math.max(0, Math.min(1, g)) * 255) << 8 | Math.round(Math.max(0, Math.min(1, b)) * 255);
        int t = atmosphere.tintSky(color);
        onix$tinting = true;
        try {
            ((net.minecraft.client.renderer.SkyRenderer) (Object) this).renderSkyDisc((t >> 16 & 0xFF) / 255f, (t >> 8 & 0xFF) / 255f, (t & 0xFF) / 255f);
        } finally {
            onix$tinting = false;
        }
        ci.cancel();
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
