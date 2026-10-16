package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.visual.VisualMods;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	private static VisualMods.BlockOverlay carbon$overlay() {
		if (CarbonClient.modules() == null) return null;
		VisualMods.BlockOverlay o = CarbonClient.modules().get(VisualMods.BlockOverlay.class);
		return o.enabled() ? o : null;
	}

	@ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int carbon$outlineColor(int color) {
		VisualMods.BlockOverlay o = carbon$overlay();
		return o == null ? color : o.color.color();
	}

	@ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float carbon$outlineWidth(float width) {
		VisualMods.BlockOverlay o = carbon$overlay();
		return o == null ? width : o.width.f();
	}
}
