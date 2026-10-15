package dev.onix.client.mixin;

import dev.onix.client.cosmetics.Cosmetics;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Puts the selected Onix cape on your own player's elytra and hides the vanilla cape (CapeRenderer draws it in 3D). */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void onix$cape(CallbackInfoReturnable<SkinTextures> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        if (mc.player == null || !self.getUuid().equals(mc.player.getUuid())) return;
        Identifier cape = Cosmetics.capeTexture();
        if (cape == null) return;
        SkinTextures original = cir.getReturnValue();
        // the cape itself is drawn in 3D by CapeRenderer; vanilla only gets it for the elytra
        cir.setReturnValue(new SkinTextures(original.texture(), original.textureUrl(), null, cape, original.model(), original.secure()));
    }
}
