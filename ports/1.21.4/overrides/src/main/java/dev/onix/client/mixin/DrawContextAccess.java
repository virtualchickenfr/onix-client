package dev.onix.client.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Minecraft 1.21.2+: the GUI's vertex consumers are no longer public. */
@Mixin(DrawContext.class)
public interface DrawContextAccess {
    @Accessor("vertexConsumers")
    VertexConsumerProvider.Immediate onix$vertexConsumers();
}
