package dev.onix.client.mixin;

import dev.onix.client.gui.WaterScene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While a screen shows the sunken scene's water (WaterScene), the in-game HUD stays out of it (hotbar, Onix HUD, …). */
@Mixin(Gui.class)
public abstract class WaterSceneHudMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void onix$hideUnderWater(CallbackInfo ci) {
        if (WaterScene.drawn() && Minecraft.getInstance().screen != null) ci.cancel();
    }
}
