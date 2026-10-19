package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.AtmosphereModule;
import dev.onix.client.render.SkyRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Minecraft 1.21.2+: the sky colour is an int. */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void onix$skyColor(CallbackInfoReturnable<Integer> cir, @Local(argsOnly = true) float tickDelta) {
        if (OnixClient.MODULES == null) return;
        Vec3d color = Vec3d.unpackRgb(cir.getReturnValue());
        Vec3d horizon = SkyRenderer.horizonColor(tickDelta);
        if (horizon != null) color = horizon;
        AtmosphereModule atmosphere = OnixClient.MODULES.get(AtmosphereModule.class);
        if (atmosphere != null && atmosphere.isEnabled()) color = atmosphere.tintSky(color);
        cir.setReturnValue(ColorHelper.fromFloats(1f, (float) color.x, (float) color.y, (float) color.z));
    }
}
