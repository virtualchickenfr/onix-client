package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.camera.FreelookModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While freelook is active, mouse movement turns the camera instead of the player. */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void onix$freelook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (OnixClient.MODULES == null || (Object) this != MinecraftClient.getInstance().player) return;
        FreelookModule freelook = OnixClient.MODULES.get(FreelookModule.class);
        if (freelook != null && freelook.isActive()) {
            freelook.look(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}
