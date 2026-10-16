package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.visual.Freelook;
import gg.carbon.client.module.impl.visual.Zoom;
import gg.carbon.client.util.Stats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void carbon$onButton(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (action == 1 && mc.screen == null && mc.player != null) Stats.click(info.button());
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void carbon$onScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (CarbonClient.modules() == null || mc.screen != null || yoffset == 0) return;
		if (CarbonClient.modules().get(Zoom.class).onScroll(yoffset)) ci.cancel();
	}

	@WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void carbon$turn(LocalPlayer player, double dx, double dy, Operation<Void> original) {
		Stats.onMouseTurn(dx, dy);
		if (CarbonClient.modules() != null) {
			Freelook freelook = CarbonClient.modules().get(Freelook.class);
			if (freelook.active()) {
				freelook.turn(dx, dy);
				return;
			}
			double mult = CarbonClient.modules().get(Zoom.class).sensitivityMultiplier();
			dx *= mult;
			dy *= mult;
		}
		original.call(player, dx, dy);
	}
}
