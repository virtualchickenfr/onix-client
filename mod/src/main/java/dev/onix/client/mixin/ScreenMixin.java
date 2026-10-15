package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.gui.LoadingScreens;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Animated Onix background behind "Saving world", "Shutting down" and other progress screens. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void onix$loadingBackground(CallbackInfo ci, @Local(argsOnly = true) DrawContext context) {
        Screen self = (Screen) (Object) this;
        if (!LoadingScreens.replacesBackground(self)) return;
        LoadingScreens.background(self, context);
        ci.cancel();
    }
}
