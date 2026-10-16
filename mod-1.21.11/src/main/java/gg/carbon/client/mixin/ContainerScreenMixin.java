package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.utility.ShulkerPreview;
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

/** Shulker box previews in container tooltips. */
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenMixin extends Screen {
	@Shadow
	protected @Nullable Slot hoveredSlot;

	protected ContainerScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "renderTooltip", at = @At("HEAD"), cancellable = true)
	private void carbon$shulkerPreview(GuiGraphics g, int mouseX, int mouseY, CallbackInfo ci) {
		if (CarbonClient.modules() == null || hoveredSlot == null || !hoveredSlot.hasItem()) return;
		ShulkerPreview preview = CarbonClient.modules().get(ShulkerPreview.class);
		if (!preview.handles(hoveredSlot.getItem())) return;
		preview.render(g, hoveredSlot.getItem(), mouseX, mouseY, width, height);
		ci.cancel();
	}
}
