package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.screen.MenuSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla menu screens outside a world get Carbon's backdrop instead of the blurred panorama. */
@Mixin(Screen.class)
public abstract class MenuSkinScreenMixin {
	@Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
	private void carbon$backdrop(GuiGraphics g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		Screen self = (Screen) (Object) this;
		if (CarbonClient.modules() == null || !CarbonClient.modules().client.menuBackgrounds.on()) return;
		if (Minecraft.getInstance().level != null || !MenuSkin.skins(self)) return;
		MenuSkin.backdrop(g, self.width, self.height, mouseX, mouseY);
		ci.cancel();
	}
}
