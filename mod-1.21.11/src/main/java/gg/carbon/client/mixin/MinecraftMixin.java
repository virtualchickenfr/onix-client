package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.screen.CarbonMainMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Shadow
	public abstract void setScreen(Screen screen);

	@Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
	private void carbon$title(CallbackInfoReturnable<String> cir) {
		cir.setReturnValue("Onix Client " + CarbonClient.VERSION + "  ·  " + cir.getReturnValue());
	}

	/** Swaps the vanilla title screen for Carbon's main menu. */
	@Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
	private void carbon$titleScreen(Screen screen, CallbackInfo ci) {
		if (screen instanceof TitleScreen && CarbonClient.modules() != null && CarbonClient.modules().client.mainMenu.on()
			&& !CarbonClient.modules().client.menuDesign.is("Vanilla")) {
			ci.cancel();
			setScreen(new CarbonMainMenu());
		}
	}
}
