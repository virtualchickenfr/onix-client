package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.utility.ShulkerPreviewModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shulker Preview in container tooltips. */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin extends Screen {
    @Shadow
    protected @Nullable Slot hoveredSlot;

    protected ContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
    private void onix$shulkerPreview(GuiGraphics g, int mouseX, int mouseY, CallbackInfo ci) {
        if (OnixClient.MODULES == null || hoveredSlot == null || !hoveredSlot.hasItem()) return;
        ShulkerPreviewModule preview = OnixClient.MODULES.get(ShulkerPreviewModule.class);
        if (preview == null || !preview.handles(hoveredSlot.getItem())) return;
        preview.render(g, hoveredSlot.getItem(), mouseX, mouseY, width, height);
        ci.cancel();
    }
}
