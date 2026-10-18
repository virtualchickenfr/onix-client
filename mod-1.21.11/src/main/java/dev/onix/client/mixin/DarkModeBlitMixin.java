package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.Identifier;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.DarkModeModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Dark mode: tints every container background texture (textures/gui/container/...) while it is drawn. */
@Mixin(GuiGraphics.class)
public abstract class DarkModeBlitMixin {
	@ModifyVariable(
		method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 4)
	private int onix$darken(int color, @Local(argsOnly = true) Identifier location) {
		return onix$tint(color, location.getPath().startsWith("textures/gui/container/"));
	}

	// GUI-atlas sprites used by containers: creative tabs, scrollers, slots ("container/...")
	@ModifyVariable(
		method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 4)
	private int onix$darkenSprite(int color, @Local(argsOnly = true) Identifier location) {
		return onix$tint(color, location.getPath().startsWith("container/"));
	}

	@ModifyVariable(
		method = "blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIIII)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 8)
	private int onix$darkenSpriteRegion(int color, @Local(argsOnly = true) Identifier location) {
		return onix$tint(color, location.getPath().startsWith("container/"));
	}

	/** Vanilla draws every container title in 0xFF404040; in dark mode that colour becomes light. */
	@ModifyVariable(
		method = "drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
		at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int onix$lightLabels(int color) {
		if (color != 0xFF404040 || OnixClient.MODULES == null) return color;
		DarkModeModule dark = OnixClient.MODULES.get(DarkModeModule.class);
		if (dark == null || !dark.appliesNow() || !dark.lightText.isOn()) return color;
		return 0xFFD6D6DA;
	}

	private static int onix$tint(int color, boolean containerArt) {
		if (!containerArt || OnixClient.MODULES == null) return color;
		DarkModeModule dark = OnixClient.MODULES.get(DarkModeModule.class);
		return dark != null && dark.appliesNow() ? dark.apply(color) : color;
	}
}
