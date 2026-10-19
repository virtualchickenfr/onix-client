package dev.onix.client.mixin;

import dev.onix.client.cosmetics.Cosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Minecraft 1.21.8: puts the selected Onix cape on your elytra and hides the vanilla cape (CapeRenderer draws it in 3D). */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntityMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void onix$cape(CallbackInfoReturnable<PlayerSkin> cir) {
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        if (mc.player == null || !self.getUUID().equals(mc.player.getUUID())) return;
        ResourceLocation cape = Cosmetics.capeTexture();
        if (cape == null) return;
        PlayerSkin original = cir.getReturnValue();
        cir.setReturnValue(new PlayerSkin(original.texture(), original.textureUrl(), null, cape, original.model(), original.secure()));
    }
}
