package gg.carbon.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import gg.carbon.client.CarbonClient;
import gg.carbon.client.screen.widget.Ui;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Fonts.Face;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Anim;
import gg.carbon.client.ui.anim.Easing;
import gg.carbon.client.ui.anim.Smooth;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Minecraft 1.21.10: draws every vanilla button as a Carbon stone block with hover sheen, plain buttons with Carbon's label. */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {
	@Unique
	private static final Map<AbstractButton, State> CARBON_STATE = new WeakHashMap<>();

	@Unique
	private static final class State {
		final Smooth hover = new Smooth(0, 16f);
		final Anim sheen = new Anim(1, 650, Easing.SMOOTH);
		boolean was;
	}

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIIII)V"))
	private void carbon$sprite(GuiGraphics g, RenderPipeline pipeline, ResourceLocation sprite, int x0, int y0, int w0, int h0, int color, Operation<Void> original) {
		if (!CarbonClient.restyleButtons()) {
			original.call(g, pipeline, sprite, x0, y0, w0, h0, color);
			return;
		}
		AbstractButton self = (AbstractButton) (Object) this;
		State s = CARBON_STATE.computeIfAbsent(self, k -> new State());
		boolean hov = self.isActive() && self.isHoveredOrFocused();
		if (hov && !s.was) s.sheen.play(0, 1);
		s.was = hov;
		s.hover.target(hov ? 1 : 0);
		float h = s.hover.get();
		float x = self.getX(), y = self.getY(), w = self.getWidth(), hh = self.getHeight();
		R2D.invert = gg.carbon.client.screen.MenuSkin.light();
		R2D.pushAlpha(((AbstractWidgetAlpha) self).carbon$alpha());
		if (!self.isActive()) {
			Ui.block(g, x, y, w, hh, 4, 3, 0xC0111113, 0xC0060607, 0x14FFFFFF);
		} else {
			Ui.block(g, x, y, w, hh, 4, 3, Theme.lerp(0xE81C1C20, 0xF02A2A30, h), 0xF0060607, Theme.alpha(0xFFFFFFFF, 0.12f + 0.22f * h));
			float sh = s.sheen.get();
			if (sh > 0 && sh < 1) {
				float bw = w * 0.4f;
				R2D.sheen(g, x + 3, x + w - 3, y + 1.5f, hh - 5, x - bw + (w + bw) * sh, bw, 0x22FFFFFF);
			}
		}
		R2D.popAlpha();
		R2D.invert = false;
	}

	@WrapOperation(method = "renderWidget", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/components/AbstractButton;renderString(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;I)V"))
	private void carbon$label(AbstractButton self, GuiGraphics g, Font font, int color0, Operation<Void> original) {
		// only plain buttons: other buttons draw icons or their own text in renderString
		if (!CarbonClient.restyleButtons() || !Fonts.enabled || self.getClass() != Button.class) {
			original.call(self, g, font, color0);
			return;
		}
		String label = self.getMessage().getString();
		float maxW = self.getWidth() - 10;
		float scale = 1f;
		while (Fonts.width(label, Face.MEDIUM, scale) > maxW && scale > 0.7f) scale -= 0.05f;
		int color = self.isActive() ? (self.isHoveredOrFocused() ? Theme.WHITE : 0xFFE4E4E8) : Theme.TEXT_MUTED;
		R2D.invert = gg.carbon.client.screen.MenuSkin.light();
		R2D.pushAlpha(((AbstractWidgetAlpha) self).carbon$alpha());
		Fonts.centered(g, label, self.getX() + self.getWidth() / 2f, self.getY() + (self.getHeight() - 3 - 8.5f * scale) / 2f + 0.5f, color, Face.MEDIUM, scale);
		R2D.popAlpha();
		R2D.invert = false;
	}
}
