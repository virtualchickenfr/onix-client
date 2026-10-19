package gg.carbon.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import gg.carbon.client.screen.MenuSkin;
import gg.carbon.client.ui.R2D;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps vanilla's menu chrome (list backgrounds, separators, text fields, scroll bars, tooltips,
 * tabs) for Carbon's while {@link MenuSkin} is active, and remaps plain fills in light mode.
 */
@Mixin(GuiGraphics.class)
public abstract class MenuSkinGraphicsMixin {
	@Inject(method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V",
		at = @At("HEAD"), cancellable = true)
	private void carbon$textures(RenderPipeline pipeline, Identifier location, int x0, int x1, int y0, int y1,
		float u0, float u1, float v0, float v1, int color, CallbackInfo ci) {
		if (!"minecraft".equals(location.getNamespace())) return;
		String path = location.getPath();
		if (!path.startsWith("textures/gui/")) return;
		boolean list = path.equals("textures/gui/menu_list_background.png");
		boolean menu = path.equals("textures/gui/menu_background.png");
		boolean header = path.equals("textures/gui/header_separator.png");
		boolean footer = path.equals("textures/gui/footer_separator.png");
		if (!(list || menu || header || footer) || !MenuSkin.active()) return;
		GuiGraphics g = (GuiGraphics) (Object) this;
		if (list) MenuSkin.listPanel(g, x0, y0, x1 - x0, y1 - y0);
		else if (header || footer) MenuSkin.separator(g, x0, y0, x1 - x0, header);
		ci.cancel();
	}

	@Inject(method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",
		at = @At("HEAD"), cancellable = true)
	private void carbon$sprites(RenderPipeline pipeline, Identifier location, int x, int y, int w, int h, int color, CallbackInfo ci) {
		if (!"minecraft".equals(location.getNamespace())) return;
		String path = location.getPath();
		if (!(path.startsWith("widget/") || path.startsWith("tooltip/"))) return;
		GuiGraphics g = (GuiGraphics) (Object) this;
		switch (path) {
			case "widget/text_field", "widget/text_field_highlighted" -> {
				if (!MenuSkin.active()) return;
				MenuSkin.textField(g, x, y, w, h, path.endsWith("highlighted"));
			}
			case "widget/scroller_background" -> {
				if (!MenuSkin.active()) return;
				MenuSkin.scrollTrack(g, x, y, w, h);
			}
			case "widget/scroller" -> {
				if (!MenuSkin.active()) return;
				MenuSkin.scrollThumb(g, x, y, w, h);
			}
			case "tooltip/background" -> {
				if (!MenuSkin.active()) return;
				MenuSkin.tooltip(g, x, y, w, h);
			}
			case "tooltip/frame" -> {
				if (!MenuSkin.active()) return;
			}
			case "widget/tab", "widget/tab_highlighted", "widget/tab_selected", "widget/tab_selected_highlighted" -> {
				if (!MenuSkin.active()) return;
				MenuSkin.tab(g, x, y, w, h, path.startsWith("widget/tab_selected"), path.endsWith("highlighted"));
			}
			default -> {
				return;
			}
		}
		ci.cancel();
	}

	/** Light mode: vanilla's white outlines, black selection fills and light cursors become ink and paper. */
	@ModifyVariable(method = "fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;IIIII)V", at = @At("HEAD"), argsOnly = true, ordinal = 4)
	private int carbon$lightFill(int color) {
		if (R2D.device > 0 || !MenuSkin.light()) return color;
		return MenuSkin.fillColor(color);
	}
}
