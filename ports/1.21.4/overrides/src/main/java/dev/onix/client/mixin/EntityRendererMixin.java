package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.NametagsModule;
import dev.onix.client.module.impl.visual.OnixBadgeModule;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.21.2+: the name tag text is worked out with the render state, so health and the Onix badge are added there. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void onix$decorateNametag(Entity entity, EntityRenderState state, float tickDelta, CallbackInfo ci) {
        if (OnixClient.MODULES == null || state.displayName == null) return;
        Text text = state.displayName;
        NametagsModule tags = OnixClient.MODULES.get(NametagsModule.class);
        if (tags != null) text = tags.decorate(entity, text);
        OnixBadgeModule badge = OnixClient.MODULES.get(OnixBadgeModule.class);
        if (badge != null) text = badge.decorateNametag(entity, text);
        state.displayName = text;
    }
}
