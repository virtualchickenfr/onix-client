package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.camera.ZoomModule;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    /** Totem Spirit can hide the big totem that pops up on screen. */
    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void onix$hideTotemOverlay(net.minecraft.world.item.ItemStack stack, CallbackInfo ci) {
        if (OnixClient.MODULES == null || !stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) return;
        var spirit = OnixClient.MODULES.get(dev.onix.client.module.impl.visual.TotemSpiritModule.class);
        if (spirit != null && spirit.hidesOverlay()) ci.cancel();
    }
}
