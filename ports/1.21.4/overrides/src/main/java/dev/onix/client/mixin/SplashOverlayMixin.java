package dev.onix.client.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.gui.LoadingAnimation;
import dev.onix.client.gui.LoadingScreens;
import java.util.function.Function;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.21.2+: the resource-loading screen; Onix hides the Mojang logo and progress bar and draws its own overlay. */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void onix$overlay(CallbackInfo ci, @Local(argsOnly = true) DrawContext context) {
        LoadingScreens.splash(this, context);
    }

    @WrapWithCondition(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V"))
    private boolean onix$hideLogo(DrawContext context, Function<Identifier, RenderLayer> layers, Identifier texture, int x, int y, float u, float v,
                                  int width, int height, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        return !LoadingAnimation.enabled();
    }

    @Inject(method = "renderProgressBar", at = @At("HEAD"), cancellable = true)
    private void onix$hideBar(CallbackInfo ci) {
        if (LoadingAnimation.enabled()) ci.cancel();
    }
}
