package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.OnixClient;
import dev.onix.client.render.SkyRenderer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import dev.onix.client.module.impl.visual.AtmosphereModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientLevel.class)
public abstract class ClientWorldMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void onix$skyColor(CallbackInfoReturnable<Vec3> cir, @Local(argsOnly = true) float tickDelta) {
        if (OnixClient.MODULES == null) return;
        // Custom Sky: vanilla derives the fog from this color, so the fog fades into the sky box
        Vec3 horizon = SkyRenderer.horizonColor(tickDelta);
        if (horizon != null) cir.setReturnValue(horizon);
        AtmosphereModule atmosphere = OnixClient.MODULES.get(AtmosphereModule.class);
        if (atmosphere != null && atmosphere.isEnabled()) cir.setReturnValue(atmosphere.tintSky(cir.getReturnValue()));
    }
}
