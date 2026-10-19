package gg.carbon.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import gg.carbon.client.CarbonClient;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/** Restyles vanilla sliders (options screens) to match Carbon. */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin {
	private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V";

	private static boolean carbon$on() {
		return CarbonClient.restyleButtons();
	}

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
	private void carbon$track(GuiGraphics g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int color, Operation<Void> original) {
		if (!carbon$on()) {
			original.call(g, pipeline, sprite, x, y, w, h, color);
			return;
		}
		AbstractSliderButton self = (AbstractSliderButton) (Object) this;
		float hover = self.isHoveredOrFocused() ? 1 : 0;
		R2D.invert = gg.carbon.client.screen.MenuSkin.light();
		R2D.pushAlpha(self.getAlpha());
		R2D.rrect(g, x, y, w, h, 6, Theme.lerp(0xD8121215, 0xF01A1A1E, hover));
		R2D.outline(g, x, y, w, h, 6, 0.75f, Theme.alpha(0xFFFFFFFF, 0.10f + 0.2f * hover));
		R2D.popAlpha();
		R2D.invert = false;
	}

	/** The slider label in Carbon's typeface, matching the restyled buttons next to it. */
	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/AbstractSliderButton;renderScrollingStringOverContents(Lnet/minecraft/client/gui/ActiveTextCollector;Lnet/minecraft/network/chat/Component;I)V"))
	private void carbon$label(AbstractSliderButton self, net.minecraft.client.gui.ActiveTextCollector out, net.minecraft.network.chat.Component message, int margin, Operation<Void> original) {
		if (!carbon$on() || !gg.carbon.client.ui.Fonts.enabled) {
			original.call(self, out, message, margin);
			return;
		}
		GuiGraphics g = carbon$graphics;
		if (g == null) return;
		String label = message.getString();
		float scale = 1f;
		while (gg.carbon.client.ui.Fonts.width(label, gg.carbon.client.ui.Fonts.Face.MEDIUM, scale) > self.getWidth() - 10 && scale > 0.7f) scale -= 0.05f;
		R2D.invert = gg.carbon.client.screen.MenuSkin.light();
		R2D.pushAlpha(self.getAlpha());
		int color = self.isActive() ? (self.isHoveredOrFocused() ? Theme.WHITE : 0xFFE4E4E8) : Theme.TEXT_MUTED;
		gg.carbon.client.ui.Fonts.centered(g, label, self.getX() + self.getWidth() / 2f, self.getY() + (self.getHeight() - 8.5f * scale) / 2f,
			color, gg.carbon.client.ui.Fonts.Face.MEDIUM, scale);
		R2D.popAlpha();
		R2D.invert = false;
	}

	@org.spongepowered.asm.mixin.Unique
	private GuiGraphics carbon$graphics;

	@org.spongepowered.asm.mixin.injection.Inject(method = "renderWidget", at = @At("HEAD"))
	private void carbon$capture(GuiGraphics g, int mx, int my, float a, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		carbon$graphics = g;
	}

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
	private void carbon$handle(GuiGraphics g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int color, Operation<Void> original) {
		if (!carbon$on()) {
			original.call(g, pipeline, sprite, x, y, w, h, color);
			return;
		}
		AbstractSliderButton self = (AbstractSliderButton) (Object) this;
		R2D.invert = gg.carbon.client.screen.MenuSkin.light();
		R2D.pushAlpha(self.getAlpha());
		R2D.rrect(g, x + 1, y + 2, w - 2, h - 5, 2.5f, self.isActive() ? 0xFF3A3A42 : 0xFF26262B);
		R2D.outline(g, x + 1, y + 2, w - 2, h - 5, 2.5f, 1f, self.isActive() ? 0xFFE8E8EA : 0xFF55555C);
		R2D.popAlpha();
		R2D.invert = false;
	}
}
