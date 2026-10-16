package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.ClientSettings;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	private void carbon$keyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (CarbonClient.modules() == null || handle != mc.getWindow().handle()) return;
		if (mc.screen != null || mc.getOverlay() != null) return;
		int key = event.key();
		ClientSettings client = CarbonClient.modules().client;
		if (action == 1 && mc.player != null) {
			if (client.menuKey.bound() && key == client.menuKey.key()) {
				CarbonClient.openMenu();
				ci.cancel();
				return;
			}
			if (client.hudKey.bound() && key == client.hudKey.key()) {
				CarbonClient.openHudEditor();
				ci.cancel();
				return;
			}
		}
		if (mc.player != null) CarbonClient.modules().key(key, action);
	}
}
