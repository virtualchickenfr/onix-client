package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.NametagsModule;
import dev.onix.client.module.impl.visual.OnixBadgeModule;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @ModifyVariable(method = "renderLabelIfPresent", at = @At("HEAD"), argsOnly = true)
    private Component onix$healthInNametag(Component text, @Local(argsOnly = true) Entity entity) {
        if (OnixClient.MODULES == null) return text;
        NametagsModule tags = OnixClient.MODULES.get(NametagsModule.class);
        Component result = tags == null ? text : tags.decorate(entity, text);
        OnixBadgeModule badge = OnixClient.MODULES.get(OnixBadgeModule.class);
        return badge == null ? result : badge.decorateNametag(entity, result);
    }
}
