package dev.onix.client.mixin;

import dev.onix.client.gui.WaterScene;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The sunken scene's water (WaterScene) goes into the frame right before the menus are drawn on top of it. */
@Mixin(GameRenderer.class)
public abstract class WaterSceneMixin {
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/state/GuiRenderState;reset()V"), require = 0)
    private void onix$water(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        WaterScene.renderIfWanted();
    }
}
