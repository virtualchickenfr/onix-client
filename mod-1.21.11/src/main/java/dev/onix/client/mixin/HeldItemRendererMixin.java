package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.HandAuraModule;
import dev.onix.client.module.impl.visual.HandViewModelModule;
import dev.onix.client.module.impl.visual.SwingAnimationModule;
import dev.onix.client.module.impl.visual.WeaponInspectModule;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** First-person hand: view model, weapon inspect, swing animation and hand aura. */
@Mixin(ItemInHandRenderer.class)
public abstract class HeldItemRendererMixin {
    @Unique
    private int onix$depth;

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void onix$viewModelStart(CallbackInfo ci, @Local(argsOnly = true) PoseStack matrices, @Local(argsOnly = true) InteractionHand hand) {
        if (OnixClient.MODULES == null) return;
        HandViewModelModule vm = OnixClient.MODULES.get(HandViewModelModule.class);
        WeaponInspectModule inspect = OnixClient.MODULES.get(WeaponInspectModule.class);
        boolean model = vm != null && vm.isEnabled();
        boolean inspecting = inspect != null && inspect.active() && hand == InteractionHand.MAIN_HAND;
        if (!model && !inspecting) return;
        matrices.pushPose();
        if (model) vm.apply(matrices, hand);
        if (inspecting) {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            int side = player == null || player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
            inspect.apply(matrices, side);
        }
        onix$depth++;
    }

    @Inject(method = "renderArmWithItem", at = @At("RETURN"))
    private void onix$viewModelEnd(CallbackInfo ci, @Local(argsOnly = true) PoseStack matrices) {
        if (onix$depth > 0) {
            matrices.popPose();
            onix$depth--;
        }
    }

    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void onix$swing(float progress, PoseStack matrices, int side, HumanoidArm arm, CallbackInfo ci) {
        if (OnixClient.MODULES == null) return;
        SwingAnimationModule swing = OnixClient.MODULES.get(SwingAnimationModule.class);
        if (swing != null && swing.isEnabled() && swing.apply(matrices, progress, side)) ci.cancel();
    }

    @Inject(method = "renderArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    private void onix$aura(CallbackInfo ci, @Local(argsOnly = true) PoseStack matrices, @Local(argsOnly = true) SubmitNodeCollector collector,
                           @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true) ItemStack stack) {
        if (OnixClient.MODULES == null) return;
        HandAuraModule aura = OnixClient.MODULES.get(HandAuraModule.class);
        if (aura != null && aura.shows(hand == InteractionHand.MAIN_HAND, stack.isEmpty())) aura.render(matrices, collector);
    }
}
