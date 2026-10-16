package gg.carbon.client.mixin;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.screen.widget.Ui;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Anim;
import gg.carbon.client.ui.anim.Easing;
import gg.carbon.client.ui.anim.Smooth;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws every vanilla button as a Carbon stone block with hover sheen. */
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

	@Inject(method = "renderDefaultSprite", at = @At("HEAD"), cancellable = true)
	private void carbon$sprite(GuiGraphics g, CallbackInfo ci) {
		if (!CarbonClient.restyleButtons()) return;
		AbstractButton self = (AbstractButton) (Object) this;
		State s = CARBON_STATE.computeIfAbsent(self, k -> new State());
		boolean hov = self.isActive() && self.isHoveredOrFocused();
		if (hov && !s.was) s.sheen.play(0, 1);
		s.was = hov;
		s.hover.target(hov ? 1 : 0);
		float h = s.hover.get();
		float x = self.getX(), y = self.getY(), w = self.getWidth(), hh = self.getHeight();
		R2D.invert = gg.carbon.client.screen.MenuSkin.light();
		R2D.pushAlpha(self.getAlpha());
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
		ci.cancel();
	}
}
