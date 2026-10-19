package gg.carbon.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import gg.carbon.client.CarbonClient;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Minecraft 1.21.10: restyles vanilla sliders (options screens) to match Carbon. */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
	private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIIII)V";

	private static float carbon$alpha(AbstractSliderButton self) {
		return ((AbstractWidgetAlpha) self).carbon$alpha();
	}

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
	private void carbon$track(GuiGraphics g, RenderPipeline pipeline, ResourceLocation sprite, int x, int y, int w, int h, int color, Operation<Void> original) {
		if (!CarbonClient.restyleButtons()) {
			original.call(g, pipeline, sprite, x, y, w, h, color);
			return;
		}
		AbstractSliderButton self = (AbstractSliderButton) (Object) this;
		float hover = self.isHoveredOrFocused() ? 1 : 0;
		R2D.invert = gg.carbon.client.screen.MenuSkin.light();
		R2D.pushAlpha(carbon$alpha(self));
		R2D.rrect(g, x, y, w, h, 6, Theme.lerp(0xD8121215, 0xF01A1A1E, hover));
		R2D.outline(g, x, y, w, h, 6, 0.75f, Theme.alpha(0xFFFFFFFF, 0.10f + 0.2f * hover));
		R2D.popAlpha();
		R2D.invert = false;
	}

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
	private void carbon$handle(GuiGraphics g, RenderPipeline pipeline, ResourceLocation sprite, int x, int y, int w, int h, int color, Operation<Void> original) {
		if (!CarbonClient.restyleButtons()) {
			original.call(g, pipeline, sprite, x, y, w, h, color);
			return;
		}
		AbstractSliderButton self = (AbstractSliderButton) (Object) this;
		R2D.invert = gg.carbon.client.screen.MenuSkin.light();
		R2D.pushAlpha(carbon$alpha(self));
		R2D.rrect(g, x + 1, y + 2, w - 2, h - 5, 2.5f, self.isActive() ? 0xFF3A3A42 : 0xFF26262B);
		R2D.outline(g, x + 1, y + 2, w - 2, h - 5, 2.5f, 1f, self.isActive() ? 0xFFE8E8EA : 0xFF55555C);
		R2D.popAlpha();
		R2D.invert = false;
	}

	/** The slider label in Carbon's typeface, matching the restyled buttons next to it. */
	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/AbstractSliderButton;renderScrollingString(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;II)V"))
	private void carbon$label(AbstractSliderButton self, GuiGraphics g, Font font, int margin, int color0, Operation<Void> original) {
		if (!CarbonClient.restyleButtons() || !Fonts.enabled) {
			original.call(self, g, font, margin, color0);
			return;
		}
		String label = self.getMessage().getString();
		float scale = 1f;
		while (Fonts.width(label, Fonts.Face.MEDIUM, scale) > self.getWidth() - 10 && scale > 0.7f) scale -= 0.05f;
		R2D.invert = gg.carbon.client.screen.MenuSkin.light();
		R2D.pushAlpha(carbon$alpha(self));
		int color = self.isActive() ? (self.isHoveredOrFocused() ? Theme.WHITE : 0xFFE4E4E8) : Theme.TEXT_MUTED;
		Fonts.centered(g, label, self.getX() + self.getWidth() / 2f, self.getY() + (self.getHeight() - 8.5f * scale) / 2f,
			color, Fonts.Face.MEDIUM, scale);
		R2D.popAlpha();
		R2D.invert = false;
	}
}
