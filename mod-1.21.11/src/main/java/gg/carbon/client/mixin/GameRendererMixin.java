package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.visual.VisualMods;
import gg.carbon.client.module.impl.visual.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	public abstract boolean isPanoramicMode();

	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void carbon$noHurtCam(CallbackInfo ci) {
		if (CarbonClient.modules() == null) return;
		VisualMods.CameraTweaks cam = CarbonClient.modules().get(VisualMods.CameraTweaks.class);
		if (cam.enabled() && cam.noHurtCam.on()) ci.cancel();
	}

	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void carbon$hideHand(CallbackInfo ci) {
		if (CarbonClient.modules() != null && CarbonClient.modules().get(Zoom.class).hideHand()) ci.cancel();
	}

	/** World FOV only (useFovSetting=true); the hand pass keeps vanilla's fixed 70. */
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void carbon$fov(Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Float> cir) {
		if (CarbonClient.modules() == null || !useFovSetting || isPanoramicMode()) return;
		float fov = cir.getReturnValueF();
		VisualMods.FovChanger changer = CarbonClient.modules().get(VisualMods.FovChanger.class);
		if (changer.enabled()) {
			float base = Minecraft.getInstance().options.fov().get();
			if (changer.custom.on()) {
				float custom = changer.fov.f();
				fov = changer.staticFov.on() ? custom : fov / base * custom;
			} else if (changer.staticFov.on()) {
				fov = base;
			}
		}
		fov /= CarbonClient.modules().get(Zoom.class).divisor();
		cir.setReturnValue(fov);
	}
}
