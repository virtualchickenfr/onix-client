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
    /** getFov returns a float since 1.21.2 (a Double here crashed the game as soon as zoom was used). */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void onix$zoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (OnixClient.MODULES == null) return;
        float beat = dev.onix.client.module.impl.hud.SongLyricsModule.fovFactor();
        if (beat != 1f) cir.setReturnValue(cir.getReturnValue() * beat);
        float punch = dev.onix.client.module.impl.pack.ViralFx.fovFactor();
        if (punch != 1f) cir.setReturnValue(cir.getReturnValue() * punch);
        ZoomModule zoom = OnixClient.MODULES.get(ZoomModule.class);
        if (zoom == null) return;
        double divisor = zoom.fovDivisor();
        if (divisor != 1.0) cir.setReturnValue((float) (cir.getReturnValue() / divisor));
    }

    /** Camera shake of the viral pack (only your view, not where you aim). */
    @Inject(method = "bobHurt", at = @At("HEAD"), require = 0)
    private void onix$shake(com.mojang.blaze3d.vertex.PoseStack pose, float tickDelta, CallbackInfo ci) {
        dev.onix.client.module.impl.pack.ViralFx.applyShake(pose);
    }

    /** Totem Spirit can hide the big totem that pops up on screen. */
    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void onix$hideTotemOverlay(net.minecraft.world.item.ItemStack stack, CallbackInfo ci) {
        if (OnixClient.MODULES == null || !stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) return;
        var spirit = OnixClient.MODULES.get(dev.onix.client.module.impl.visual.TotemSpiritModule.class);
        if (spirit != null && spirit.hidesOverlay()) ci.cancel();
    }
}
