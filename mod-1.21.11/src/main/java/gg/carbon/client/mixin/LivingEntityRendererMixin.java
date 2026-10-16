package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.visual.VisualMods;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("RETURN"), cancellable = true)
	private void carbon$ownNametag(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
		Minecraft mc = Minecraft.getInstance();
		if (CarbonClient.modules() == null || entity != mc.player || cir.getReturnValueZ()) return;
		if (CarbonClient.modules().get(VisualMods.OwnNametag.class).enabled() && !mc.options.hideGui
			&& !mc.options.getCameraType().isFirstPerson() && !entity.isInvisible()) {
			cir.setReturnValue(true);
		}
	}
}
