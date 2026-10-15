package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.gui.OnixTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    /** Replaces the vanilla title screen with the Onix one. */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onix$titleScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof TitleScreen && OnixClient.CONFIG != null && OnixClient.CONFIG.customMainMenu.isOn()) {
            ci.cancel();
            ((MinecraftClient) (Object) this).setScreen(new OnixTitleScreen());
        }
    }

    @Inject(method = "getWindowTitle", at = @At("RETURN"), cancellable = true)
    private void onix$windowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(OnixClient.NAME + " " + OnixClient.VERSION + " · " + cir.getReturnValue());
    }
}
