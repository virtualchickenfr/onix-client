package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.utility.ToastControlModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Toast Control. */
@Mixin(ToastManager.class)
public abstract class ToastManagerMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void onix$hideToast(Toast toast, CallbackInfo ci) {
        if (OnixClient.MODULES == null) return;
        ToastControlModule control = OnixClient.MODULES.get(ToastControlModule.class);
        if (control != null && control.block(toast)) ci.cancel();
    }

    /** Minecraft's pop-ups (tutorial, recipes, advancements) wait while the Onix menu or HUD editor is open, instead of covering it. */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onix$hideOverMenu(GuiGraphics graphics, CallbackInfo ci) {
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof dev.onix.client.gui.OnixMenuScreen || screen instanceof dev.onix.client.gui.HudEditorScreen) ci.cancel();
    }
}
