package gg.carbon.client.mixin;

import java.util.function.IntSupplier;
import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/** Carbon always loads on a black backdrop. */
@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {
	@WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private int carbon$black(IntSupplier supplier, Operation<Integer> original) {
		return 0xFF000000;
	}
}
