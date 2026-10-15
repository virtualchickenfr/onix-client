package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.HandViewModelModule;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique
    private int onix$depth;

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"))
    private void onix$viewModelStart(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices, @Local(argsOnly = true) Hand hand) {
        if (OnixClient.MODULES == null) return;
        HandViewModelModule vm = OnixClient.MODULES.get(HandViewModelModule.class);
        if (vm == null || !vm.isEnabled()) return;
        matrices.push();
        vm.apply(matrices, hand);
        onix$depth++;
    }

    @Inject(method = "renderFirstPersonItem", at = @At("RETURN"))
    private void onix$viewModelEnd(CallbackInfo ci, @Local(argsOnly = true) MatrixStack matrices) {
        if (onix$depth > 0) {
            matrices.pop();
            onix$depth--;
        }
    }
}
