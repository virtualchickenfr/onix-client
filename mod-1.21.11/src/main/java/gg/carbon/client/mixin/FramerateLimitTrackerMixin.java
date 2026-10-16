package gg.carbon.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.performance.PerformanceMods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {
	@Inject(method = "getFramerateLimit", at = @At("RETURN"), cancellable = true)
	private void carbon$backgroundFps(CallbackInfoReturnable<Integer> cir) {
		if (CarbonClient.modules() == null) return;
		int limit = CarbonClient.modules().get(PerformanceMods.UnfocusedFps.class).apply(cir.getReturnValueI());
		if (limit != cir.getReturnValueI()) cir.setReturnValue(limit);
	}
}
