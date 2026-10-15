package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.camera.CameraTweaksModule;
import dev.onix.client.module.impl.camera.FreelookModule;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique
    private float onix$desiredDistance = 4f;

    /** Freelook: the camera uses the freelook angles instead of the player's. */
    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 0))
    private void onix$freelook(Args args) {
        if (OnixClient.MODULES == null) return;
        FreelookModule freelook = OnixClient.MODULES.get(FreelookModule.class);
        if (freelook != null && freelook.isActive()) {
            args.set(0, freelook.yaw());
            args.set(1, freelook.pitch());
        }
    }

    /** Third-person distance. */
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"), index = 0)
    private float onix$distance(float desired) {
        CameraTweaksModule tweaks = OnixClient.MODULES == null ? null : OnixClient.MODULES.get(CameraTweaksModule.class);
        float value = tweaks != null && tweaks.isEnabled() ? desired * tweaks.distanceFactor() : desired;
        onix$desiredDistance = value;
        return value;
    }

    /** Height offset of the third-person camera. */
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;moveBy(FFF)V"), index = 1)
    private float onix$height(float y) {
        CameraTweaksModule tweaks = OnixClient.MODULES == null ? null : OnixClient.MODULES.get(CameraTweaksModule.class);
        return tweaks != null && tweaks.isEnabled() ? y + tweaks.height() : y;
    }

    /** Over-the-shoulder offset of the third-person camera. */
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;moveBy(FFF)V"), index = 2)
    private float onix$shoulder(float z) {
        CameraTweaksModule tweaks = OnixClient.MODULES == null ? null : OnixClient.MODULES.get(CameraTweaksModule.class);
        return tweaks != null && tweaks.isEnabled() ? z + tweaks.shoulder() : z;
    }

    /** No clipping: move the full distance even if a wall is in the way. */
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;moveBy(FFF)V"), index = 0)
    private float onix$noClip(float x) {
        CameraTweaksModule tweaks = OnixClient.MODULES == null ? null : OnixClient.MODULES.get(CameraTweaksModule.class);
        if (tweaks != null && tweaks.isEnabled() && tweaks.noClip() && x < 0) return -onix$desiredDistance;
        return x;
    }
}
