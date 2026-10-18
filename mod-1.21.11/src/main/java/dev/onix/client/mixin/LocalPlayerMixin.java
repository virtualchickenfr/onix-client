package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.utility.DropPreventionModule;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Drop Prevention: the drop key goes through LocalPlayer.drop(boolean). */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "drop", at = @At("HEAD"), cancellable = true)
    private void onix$drop(boolean all, CallbackInfoReturnable<Boolean> cir) {
        if (OnixClient.MODULES == null) return;
        DropPreventionModule protect = OnixClient.MODULES.get(DropPreventionModule.class);
        if (protect != null && protect.shouldBlock(((LocalPlayer) (Object) this).getMainHandItem())) cir.setReturnValue(false);
    }
}
