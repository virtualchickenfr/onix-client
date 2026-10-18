package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.NametagsModule;
import dev.onix.client.module.impl.visual.OnixBadgeModule;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    /** Health in name tags and the Onix badge (1.21.11 takes the name tag text from getNameTag). */
    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true)
    private void onix$decorateNameTag(Entity entity, CallbackInfoReturnable<Component> cir) {
        Component text = cir.getReturnValue();
        if (OnixClient.MODULES == null || text == null) return;
        NametagsModule tags = OnixClient.MODULES.get(NametagsModule.class);
        Component result = tags == null ? text : tags.decorate(entity, text);
        OnixBadgeModule badge = OnixClient.MODULES.get(OnixBadgeModule.class);
        cir.setReturnValue(badge == null ? result : badge.decorateNametag(entity, result));
    }
}
