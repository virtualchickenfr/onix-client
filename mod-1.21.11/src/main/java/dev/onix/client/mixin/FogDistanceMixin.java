package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.FogModule;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Fog: environment fog, distance fog and cloud distance as the Fog module sets them. */
@Mixin(FogRenderer.class)
public abstract class FogDistanceMixin {
    @ModifyArgs(method = "setupFog",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;updateBuffer(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"), require = 0)
    private void onix$fog(Args args) {
        if (OnixClient.MODULES == null) return;
        FogModule m = OnixClient.MODULES.get(FogModule.class);
        if (m == null || !m.isEnabled()) return;
        float[] v = {args.get(3), args.get(4), args.get(5), args.get(6), args.get(7), args.get(8)};
        m.adjust(v);
        for (int i = 0; i < 6; i++) args.set(3 + i, v[i]);
    }
}
