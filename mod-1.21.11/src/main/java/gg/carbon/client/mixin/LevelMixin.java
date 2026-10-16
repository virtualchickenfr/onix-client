package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.visual.VisualMods;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public class LevelMixin {
	private boolean carbon$clear() {
		return (Object) this instanceof ClientLevel && CarbonClient.modules() != null
			&& CarbonClient.modules().get(VisualMods.WeatherChanger.class).enabled();
	}

	@Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
	private void carbon$rain(float a, CallbackInfoReturnable<Float> cir) {
		if (carbon$clear()) cir.setReturnValue(0f);
	}

	@Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
	private void carbon$thunder(float a, CallbackInfoReturnable<Float> cir) {
		if (carbon$clear()) cir.setReturnValue(0f);
	}
}
