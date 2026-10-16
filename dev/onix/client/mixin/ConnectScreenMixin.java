package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.gui.LoadingAnimation;
import dev.onix.client.gui.LoadingScreens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Connecting to the server…" – keeps the Cancel button. */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onix$render(CallbackInfo ci,
                             @Local(argsOnly = true) GuiGraphics context,
                             @Local(argsOnly = true, ordinal = 0) int mouseX,
                             @Local(argsOnly = true, ordinal = 1) int mouseY,
                             @Local(argsOnly = true) float delta) {
        if (!LoadingAnimation.enabled()) return;
        LoadingScreens.connecting((Screen) (Object) this, context, mouseX, mouseY, delta);
        ci.cancel();
    }
}
