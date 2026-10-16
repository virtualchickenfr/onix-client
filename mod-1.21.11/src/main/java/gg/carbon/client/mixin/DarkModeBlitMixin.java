package gg.carbon.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.impl.visual.DarkMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Dark mode: tints every container background texture (textures/gui/container/...) while it is drawn. */
@Mixin(GuiGraphics.class)
public class DarkModeBlitMixin {
	@ModifyVariable(
		method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 4)
	private int carbon$darken(int color, @Local(argsOnly = true) Identifier location) {
		return carbon$tint(color, location.getPath().startsWith("textures/gui/container/"));
	}

	// GUI-atlas sprites used by containers: creative tabs, scrollers, slots ("container/...")
	@ModifyVariable(
		method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 4)
	private int carbon$darkenSprite(int color, @Local(argsOnly = true) Identifier location) {
		return carbon$tint(color, location.getPath().startsWith("container/"));
	}

	@ModifyVariable(
		method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIIII)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 8)
	private int carbon$darkenSpriteRegion(int color, @Local(argsOnly = true) Identifier location) {
		return carbon$tint(color, location.getPath().startsWith("container/"));
	}

	/** Vanilla draws every container title in 0xFF404040; in dark mode that colour becomes light. */
	@ModifyVariable(
		method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int carbon$lightLabels(int color) {
		if (color != 0xFF404040 || CarbonClient.modules() == null) return color;
		DarkMode dark = CarbonClient.modules().get(DarkMode.class);
		if (!dark.enabled() || !dark.lightText.on()) return color;
		var screen = Minecraft.getInstance().screen;
		if (!(screen instanceof AbstractContainerScreen<?>)) return color;
		if (screen instanceof CreativeModeInventoryScreen && !dark.creative.on()) return color;
		return 0xFFD6D6DA;
	}

	private static int carbon$tint(int color, boolean containerArt) {
		if (!containerArt || CarbonClient.modules() == null) return color;
		DarkMode dark = CarbonClient.modules().get(DarkMode.class);
		if (!dark.enabled()) return color;
		var screen = Minecraft.getInstance().screen;
		if (!(screen instanceof AbstractContainerScreen<?>)) return color;
		if (screen instanceof CreativeModeInventoryScreen && !dark.creative.on()) return color;
		return dark.apply(color);
	}
}
