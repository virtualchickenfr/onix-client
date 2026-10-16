package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.camera.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onix$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (OnixClient.MODULES == null) return;
        ZoomModule zoom = OnixClient.MODULES.get(ZoomModule.class);
        if (zoom == null) return;
        double divisor = zoom.fovDivisor();
        if (divisor != 1.0) cir.setReturnValue(cir.getReturnValue() / divisor);
    }
}
