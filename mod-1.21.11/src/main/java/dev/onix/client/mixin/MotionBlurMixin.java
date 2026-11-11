package dev.onix.client.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import dev.onix.client.module.impl.visual.MotionBlurModule;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Motion Blur: runs the blur post effect after the world (and its entity outlines) and before the HUD. */
@Mixin(GameRenderer.class)
public abstract class MotionBlurMixin {
    @Shadow
    @Final
    private CrossFrameResourcePool resourcePool;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;doEntityOutline()V", shift = At.Shift.AFTER), require = 0)
    private void onix$motionBlur(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        Identifier id = MotionBlurModule.effect();
        if (id == null) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            PostChain chain = mc.getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
            if (chain != null) chain.process(mc.getMainRenderTarget(), resourcePool);
        } catch (RuntimeException e) {
            dev.onix.client.OnixClient.LOGGER.warn("Motion blur failed: {}", e.toString());
        }
    }
}
