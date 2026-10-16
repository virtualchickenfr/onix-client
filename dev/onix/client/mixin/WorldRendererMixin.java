package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.render.SkyRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Custom Sky: draws the selected sky box instead of the vanilla overworld sky. */
@Mixin(LevelRenderer.class)
public abstract class WorldRendererMixin {
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void onix$customSky(CallbackInfo ci,
                                @Local(argsOnly = true, ordinal = 0) Matrix4f viewMatrix,
                                @Local(argsOnly = true) float tickDelta,
                                @Local(argsOnly = true) boolean thickFog) {
        // thick fog (e.g. blindness, lava) keeps vanilla behaviour, which hides the sky
        if (thickFog) return;
        if (SkyRenderer.render(viewMatrix, tickDelta)) ci.cancel();
    }
}
