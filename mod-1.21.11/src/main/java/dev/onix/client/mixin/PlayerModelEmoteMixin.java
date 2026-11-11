package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.EmotesModule;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: after the model got its normal pose, the playing emote moves your own character's limbs. */
@Mixin(PlayerModel.class)
public abstract class PlayerModelEmoteMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"), require = 0)
    private void onix$emote(AvatarRenderState state, CallbackInfo ci) {
        if (OnixClient.MODULES == null) return;
        EmotesModule emotes = OnixClient.MODULES.get(EmotesModule.class);
        if (emotes != null && emotes.isEnabled()) emotes.pose((PlayerModel) (Object) this, state.id);
    }
}
