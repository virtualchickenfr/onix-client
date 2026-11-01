package dev.onix.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Minecraft draws every menu outside a world at 60 FPS. Onix's own menus (title screen, Onix menu, intro) animate a
 * lot, so they run at your normal frame limit (at most 240) – smooth on high refresh rate screens.
 */
@Mixin(FramerateLimitTracker.class)
public abstract class FramerateLimitMixin {
    @Shadow
    private int framerateLimit;

    @Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
    private void onix$smoothMenus(CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValueI() != 60) return;
        var screen = Minecraft.getInstance().screen;
        if (screen instanceof dev.onix.client.gui.OnixTitleScreen || screen instanceof dev.onix.client.gui.OnixMenuScreen
                || screen instanceof dev.onix.client.gui.WelcomeScreen) {
            cir.setReturnValue(Math.max(60, Math.min(240, framerateLimit)));
        }
    }
}
