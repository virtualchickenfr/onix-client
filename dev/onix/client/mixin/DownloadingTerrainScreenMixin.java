package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.gui.LoadingAnimation;
import dev.onix.client.gui.LoadingScreens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** "Loading terrain" after joining a server or changing dimension. */
@Mixin(ReceivingLevelScreen.class)
public abstract class DownloadingTerrainScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onix$render(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics context) {
        if (!LoadingAnimation.enabled()) return;
        LoadingScreens.terrain((Screen) (Object) this, context);
        ci.cancel();
    }
}
