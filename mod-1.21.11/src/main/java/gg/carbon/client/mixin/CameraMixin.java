package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.visual.Freelook;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

@Mixin(Camera.class)
public class CameraMixin {
	@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
	private float carbon$yaw(Entity entity, float partialTicks, Operation<Float> original) {
		Freelook f = CarbonClient.modules() == null ? null : CarbonClient.modules().get(Freelook.class);
		return f != null && f.active() ? f.yaw() : original.call(entity, partialTicks);
	}

	@WrapOperation(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
	private float carbon$pitch(Entity entity, float partialTicks, Operation<Float> original) {
		Freelook f = CarbonClient.modules() == null ? null : CarbonClient.modules().get(Freelook.class);
		return f != null && f.active() ? f.pitch() : original.call(entity, partialTicks);
	}
}
