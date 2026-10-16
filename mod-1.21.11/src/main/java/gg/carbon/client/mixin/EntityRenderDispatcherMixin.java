package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.performance.PerformanceMods;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void carbon$cull(Entity entity, Frustum culler, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (CarbonClient.modules() != null && CarbonClient.modules().get(PerformanceMods.EntityCulling.class).cull(entity)) cir.setReturnValue(false);
	}
}
