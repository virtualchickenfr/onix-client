package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.pvp.PvpMods;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Drop prevention: in 1.21.11 the drop key goes through LocalPlayer.drop(boolean). */
@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {
	@Inject(method = "drop", at = @At("HEAD"), cancellable = true)
	private void carbon$drop(boolean all, CallbackInfoReturnable<Boolean> cir) {
		if (CarbonClient.modules() == null) return;
		LocalPlayer self = (LocalPlayer) (Object) this;
		PvpMods.DropPrevention dp = CarbonClient.modules().get(PvpMods.DropPrevention.class);
		if (dp.enabled() && dp.shouldBlock(self.getMainHandItem())) cir.setReturnValue(false);
	}
}
