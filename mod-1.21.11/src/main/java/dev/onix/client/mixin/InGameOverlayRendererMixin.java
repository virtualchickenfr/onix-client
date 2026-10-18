package dev.onix.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.LowFireModule;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class InGameOverlayRendererMixin {
    @Unique
    private static boolean onix$pushed;

    @Unique
    private static LowFireModule onix$lowFire() {
        if (OnixClient.MODULES == null) return null;
        LowFireModule lowFire = OnixClient.MODULES.get(LowFireModule.class);
        return lowFire != null && lowFire.isEnabled() ? lowFire : null;
    }

    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void onix$lowFireStart(CallbackInfo ci, @Local(argsOnly = true) PoseStack matrices) {
        onix$pushed = false;
        LowFireModule lowFire = onix$lowFire();
        if (lowFire == null) return;
        if (lowFire.hideCompletely()) {
            ci.cancel();
            return;
        }
        matrices.pushPose();
        matrices.translate(0.0F, -lowFire.offset(), 0.0F);
        float scale = lowFire.scale();
        matrices.scale(scale, scale, 1.0F);
        onix$pushed = true;
    }

    /** Vanilla draws the flames with alpha 0.9. */
    @ModifyConstant(method = "renderFire", constant = @Constant(floatValue = 0.9F))
    private static float onix$lowFireAlpha(float alpha) {
        LowFireModule lowFire = onix$lowFire();
        return lowFire == null ? alpha : alpha * lowFire.opacity();
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void onix$lowFireEnd(CallbackInfo ci, @Local(argsOnly = true) PoseStack matrices) {
        if (onix$pushed) {
            matrices.popPose();
            onix$pushed = false;
        }
    }
}
