package dev.onix.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.LowFireModule;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Unique
    private static boolean onix$pushed;

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void onix$lowFireStart(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices) {
        onix$pushed = false;
        if (OnixClient.MODULES == null) return;
        LowFireModule lowFire = OnixClient.MODULES.get(LowFireModule.class);
        if (lowFire == null || !lowFire.isEnabled()) return;
        if (lowFire.hideCompletely()) {
            ci.cancel();
            return;
        }
        matrices.push();
        matrices.translate(0.0F, -lowFire.offset(), 0.0F);
        float scale = lowFire.scale();
        matrices.scale(scale, scale, 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, lowFire.opacity());
        onix$pushed = true;
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"))
    private static void onix$lowFireEnd(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices) {
        if (onix$pushed) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            matrices.pop();
            onix$pushed = false;
        }
    }
}
