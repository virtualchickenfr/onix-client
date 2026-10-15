package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.gui.LoadingAnimation;
import dev.onix.client.gui.LoadingScreens;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.LevelLoadingScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Singleplayer world loading ("Preparing spawn area"). */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onix$render(CallbackInfo ci, @Local(argsOnly = true) DrawContext context) {
        if (!LoadingAnimation.enabled()) return;
        LoadingScreens.levelLoading((Screen) (Object) this, context);
        ci.cancel();
    }
}
