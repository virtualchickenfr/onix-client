package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.utility.ToastControlModule;
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
}
