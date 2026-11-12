package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.gui.LoadingAnimation;
import dev.onix.client.gui.LoadingScreens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Singleplayer world loading ("Preparing spawn area"). */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onix$render(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics context) {
        if (!LoadingAnimation.enabled()) return;
        LoadingScreens.levelLoading((Screen) (Object) this, context);
        ci.cancel();
    }

    /** The sunken scene's water is already in the frame (WaterScene): no portal or panorama background over it. */
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private void onix$water(CallbackInfo ci) {
        if (dev.onix.client.gui.WaterScene.drawn()) ci.cancel();
    }
}
