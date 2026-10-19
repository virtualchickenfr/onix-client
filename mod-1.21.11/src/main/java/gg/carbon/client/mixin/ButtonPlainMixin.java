package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Fonts.Face;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla text buttons: Carbon block sprite plus a Pixelify label. */
@Mixin(Button.Plain.class)
public abstract class ButtonPlainMixin {
	@Inject(method = "renderContents", at = @At("HEAD"), cancellable = true)
	private void carbon$contents(GuiGraphics g, int mouseX, int mouseY, float a, CallbackInfo ci) {
		if (!CarbonClient.restyleButtons() || !Fonts.enabled) return;
		Button self = (Button) (Object) this;
		((AbstractButtonInvoker) self).carbon$extractDefaultSprite(g);
		String label = self.getMessage().getString();
		float maxW = self.getWidth() - 10;
		float scale = 1f;
		while (Fonts.width(label, Face.MEDIUM, scale) > maxW && scale > 0.7f) scale -= 0.05f;
		int color = self.isActive() ? (self.isHoveredOrFocused() ? Theme.WHITE : 0xFFE4E4E8) : Theme.TEXT_MUTED;
		R2D.invert = gg.carbon.client.screen.MenuSkin.light();
		R2D.pushAlpha(self.getAlpha());
		Fonts.centered(g, label, self.getX() + self.getWidth() / 2f, self.getY() + (self.getHeight() - 3 - 8.5f * scale) / 2f + 0.5f, color, Face.MEDIUM, scale);
		R2D.popAlpha();
		R2D.invert = false;
		ci.cancel();
	}
}
