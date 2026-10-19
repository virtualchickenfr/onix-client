package gg.carbon.client.mixin;

import gg.carbon.client.screen.MenuSkin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft 1.21.8: rounded Carbon selection instead of vanilla's white box around the selected list entry. */
@Mixin(AbstractSelectionList.class)
public abstract class MenuSkinListMixin {
	@Inject(method = "renderSelection", at = @At("HEAD"), cancellable = true)
	private void carbon$selection(GuiGraphics g, int y, int rowWidth, int height, int outlineColor, int fillColor, CallbackInfo ci) {
		if (!MenuSkin.active()) return;
		AbstractSelectionList<?> self = (AbstractSelectionList<?>) (Object) this;
		int x = self.getX() + (self.getWidth() - rowWidth) / 2;
		MenuSkin.selection(g, x, y - 2, rowWidth, height + 4, outlineColor == -1);
		ci.cancel();
	}
}
