package gg.carbon.client.mixin;

import gg.carbon.client.screen.MenuSkin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.layouts.LayoutElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rounded Carbon selection instead of vanilla's white box around the selected list entry. */
@Mixin(AbstractSelectionList.class)
public abstract class MenuSkinListMixin {
	@Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true)
	private void carbon$selection(GuiGraphics g, @Coerce LayoutElement entry, int outlineColor, CallbackInfo ci) {
		if (!MenuSkin.active()) return;
		MenuSkin.selection(g, entry.getX(), entry.getY(), entry.getWidth(), entry.getHeight(), outlineColor == -1);
		ci.cancel();
	}
}
