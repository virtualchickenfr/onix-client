package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.gui.LoadingScreens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Animated Onix background behind "Saving world", "Shutting down" and other progress screens. */
@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void onix$loadingBackground(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics context) {
        Screen self = (Screen) (Object) this;
        if (!LoadingScreens.replacesBackground(self)) return;
        LoadingScreens.background(self, context);
        ci.cancel();
    }

    /** The pause menu in the liquid glass look: darker, softer edges and a faint glow behind the buttons. */
    @Inject(method = "renderBackground", at = @At("TAIL"))
    private void onix$pauseGlass(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics context) {
        Screen self = (Screen) (Object) this;
        if (!(self instanceof net.minecraft.client.gui.screens.PauseScreen) || dev.onix.client.OnixClient.CONFIG == null
                || !dev.onix.client.OnixClient.CONFIG.menuGlass.isOn() || dev.onix.client.OnixClient.CONFIG.lightMode.isOn()) return;
        int w = self.width;
        int h = self.height;
        context.fill(0, 0, w, h, 0x66000000);
        dev.onix.client.gui.SoftShapes.glow(context, w / 2f, h / 2f, w * 0.42f, h * 0.45f, dev.onix.client.gui.Theme.withAlpha(dev.onix.client.gui.Theme.vibe(), 34));
        dev.onix.client.gui.SoftShapes.vignette(context, -w / 8, -h / 8, w + w / 4, h + h / 4, 0xB0000000);
    }
}
