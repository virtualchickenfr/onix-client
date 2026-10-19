package gg.carbon.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gg.carbon.client.screen.MenuSkin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.render.state.GuiTextRenderState;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Minecraft 1.21.10 and older: light mode for vanilla menu text (prepareText has no includeEmpty flag here). */
@Mixin(GuiTextRenderState.class)
public abstract class MenuSkinTextMixin {
	@WrapOperation(method = "ensurePrepared", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZI)Lnet/minecraft/client/gui/Font$PreparedText;"))
	private Font.PreparedText carbon$lightText(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow,
		int background, Operation<Font.PreparedText> original) {
		if (!MenuSkin.light()) return original.call(font, text, x, y, color, shadow, background);
		FormattedCharSequence mapped = sink -> text.accept((index, style, codepoint) -> sink.accept(index, carbon$style(style), codepoint));
		return original.call(font, mapped, x, y, MenuSkin.textColor(color), false, background);
	}

	private static Style carbon$style(Style style) {
		TextColor c = style.getColor();
		if (c == null) return style;
		int rgb = c.getValue() & 0xFFFFFF;
		int mapped = MenuSkin.textColor(0xFF000000 | rgb) & 0xFFFFFF;
		return mapped == rgb ? style : style.withColor(mapped);
	}
}
