package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.utility.UtilityMods;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastManager.class)
public class ToastManagerMixin {
	@Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
	private void carbon$hideToast(Toast toast, CallbackInfo ci) {
		if (CarbonClient.modules() == null) return;
		UtilityMods.ToastControl tc = CarbonClient.modules().get(UtilityMods.ToastControl.class);
		if (tc.enabled() && tc.block(toast)) ci.cancel();
	}
}
