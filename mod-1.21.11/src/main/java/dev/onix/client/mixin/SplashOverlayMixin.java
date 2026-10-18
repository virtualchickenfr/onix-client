package dev.onix.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.gui.LoadingAnimation;
import dev.onix.client.gui.LoadingScreens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The resource-loading screen (game start, F3 + T, resource packs). Vanilla still runs all
 * of its logic; Onix only hides the Mojang logo and progress bar and draws its own overlay.
 */
@Mixin(LoadingOverlay.class)
public abstract class SplashOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onix$overlay(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics context) {
        LoadingScreens.splash(this, context);
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private boolean onix$hideLogo(GuiGraphics context, com.mojang.blaze3d.pipeline.RenderPipeline pipeline, Identifier texture, int x, int y,
                                  float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        return !LoadingAnimation.enabled();
    }

    @Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true)
    private void onix$hideBar(CallbackInfo ci) {
        if (LoadingAnimation.enabled()) ci.cancel();
    }
}
