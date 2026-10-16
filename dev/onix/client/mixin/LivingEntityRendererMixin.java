package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.NametagsModule;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("RETURN"), cancellable = true)
    private void onix$ownNametag(CallbackInfoReturnable<Boolean> cir, @Local(argsOnly = true) LivingEntity entity) {
        if (OnixClient.MODULES == null) return;
        NametagsModule tags = OnixClient.MODULES.get(NametagsModule.class);
        if (tags != null && tags.showOwn(entity)) cir.setReturnValue(true);
    }
}
