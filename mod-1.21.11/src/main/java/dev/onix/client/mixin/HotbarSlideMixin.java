package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.SmoothHotbarModule;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Smooth Hotbar: the selection frame slides to the selected slot instead of jumping (its x position). */
@Mixin(Gui.class)
public abstract class HotbarSlideMixin {
    @ModifyArg(method = "renderItemHotbar",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"),
            index = 2, require = 0)
    private int onix$slide(int x) {
        if (OnixClient.MODULES == null) return x;
        SmoothHotbarModule m = OnixClient.MODULES.get(SmoothHotbarModule.class);
        return m != null && m.isEnabled() ? m.slide(x) : x;
    }
}
