package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.gui.OnixTitleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {
    /** Replaces the vanilla title screen with the Onix one. */
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onix$titleScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof TitleScreen && OnixClient.CONFIG != null && OnixClient.CONFIG.customMainMenu.isOn()) {
            String design = OnixClient.CONFIG.menuDesign.get();
            if (design.equals("Vanilla")) return;
            ci.cancel();
            ((Minecraft) (Object) this).setScreen(design.equals("Onix") ? new OnixTitleScreen() : new gg.carbon.client.screen.CarbonMainMenu());
        }
    }

    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void onix$windowTitle(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(OnixClient.NAME + " " + OnixClient.VERSION + " · " + cir.getReturnValue());
    }
}
