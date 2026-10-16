package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.visual.VisualMods;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 1.21.11 uploads the lightmap uniforms straight from updateLightTexture:
 * putFloat #3 is the night-vision scale, #6 the gamma/brightness.
 */
@Mixin(LightTexture.class)
public class LightmapMixin {
	private static final String PUT_FLOAT = "Lcom/mojang/blaze3d/buffers/Std140Builder;putFloat(F)Lcom/mojang/blaze3d/buffers/Std140Builder;";

	private static VisualMods.Fullbright carbon$fullbright() {
		if (CarbonClient.modules() == null) return null;
		VisualMods.Fullbright fb = CarbonClient.modules().get(VisualMods.Fullbright.class);
		return fb.enabled() ? fb : null;
	}

	@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 3))
	private float carbon$nightVision(float value) {
		VisualMods.Fullbright fb = carbon$fullbright();
		return fb != null && fb.mode.is("Night Vision") ? Math.max(value, fb.strength.f()) : value;
	}

	@ModifyArg(method = "updateLightTexture", at = @At(value = "INVOKE", target = PUT_FLOAT, ordinal = 6))
	private float carbon$brightness(float value) {
		VisualMods.Fullbright fb = carbon$fullbright();
		return fb != null && !fb.mode.is("Night Vision") ? Math.max(value, 1f + fb.strength.f() * 9f) : value;
	}
}
