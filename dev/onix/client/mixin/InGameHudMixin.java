package dev.onix.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.combat.CrosshairModule;
import dev.onix.client.module.impl.visual.OverlaysModule;
import dev.onix.client.module.impl.visual.ScoreboardModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * HUD hooks. Handlers only take CallbackInfo plus @Local arguments, so small signature
 * differences between Minecraft versions cannot crash the game – the hook is just skipped.
 */
@Mixin(Gui.class)
public abstract class InGameHudMixin {
    @Unique
    private boolean onix$scoreboardPushed;

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void onix$crosshair(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics context) {
        CrosshairModule crosshair = (OnixClient.MODULES == null ? null : OnixClient.MODULES.get(CrosshairModule.class));
        if (crosshair == null || !crosshair.isEnabled()) return;
        ci.cancel();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getDebugOverlay().showDebugScreen()) return;
        if (!mc.options.getCameraType().isFirstPerson() && !crosshair.showInThirdPerson()) return;
        if (mc.screen != null && crosshair.hideInMenus()) return;
        crosshair.render(context, context.guiWidth() / 2, context.guiHeight() / 2);
    }

    @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
    private void onix$overlay(CallbackInfo ci, @Local(argsOnly = true) ResourceLocation texture) {
        OverlaysModule overlays = (OnixClient.MODULES == null ? null : OnixClient.MODULES.get(OverlaysModule.class));
        if (overlays == null || !overlays.isEnabled() || texture == null) return;
        String path = texture.getPath();
        if (overlays.pumpkin.isOn() && path.contains("pumpkinblur")) ci.cancel();
        if (overlays.powderSnow.isOn() && path.contains("powder_snow")) ci.cancel();
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void onix$vignette(CallbackInfo ci) {
        OverlaysModule overlays = (OnixClient.MODULES == null ? null : OnixClient.MODULES.get(OverlaysModule.class));
        if (overlays != null && overlays.isEnabled() && overlays.vignette.isOn()) ci.cancel();
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onix$portal(CallbackInfo ci) {
        OverlaysModule overlays = (OnixClient.MODULES == null ? null : OnixClient.MODULES.get(OverlaysModule.class));
        if (overlays != null && overlays.isEnabled() && overlays.portal.isOn()) ci.cancel();
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void onix$spyglass(CallbackInfo ci) {
        OverlaysModule overlays = (OnixClient.MODULES == null ? null : OnixClient.MODULES.get(OverlaysModule.class));
        if (overlays != null && overlays.isEnabled() && overlays.spyglass.isOn()) ci.cancel();
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("HEAD"), cancellable = true)
    private void onix$scoreboardStart(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics context) {
        onix$scoreboardPushed = false;
        ScoreboardModule sb = (OnixClient.MODULES == null ? null : OnixClient.MODULES.get(ScoreboardModule.class));
        if (sb == null || !sb.isEnabled()) return;
        if (sb.hidden()) {
            ci.cancel();
            return;
        }
        float s = sb.scale.getFloat();
        int w = context.guiWidth();
        int h = context.guiHeight();
        context.pose().pushPose();
        context.pose().translate(w + sb.offsetX.getFloat(), h / 2f + sb.offsetY.getFloat(), 0);
        context.pose().scale(s, s, 1f);
        context.pose().translate(-w, -h / 2f, 0);
        context.flush(); // flush earlier HUD elements so only the scoreboard is faded
        RenderSystem.setShaderColor(1f, 1f, 1f, sb.opacity.getFloat() / 100f);
        onix$scoreboardPushed = true;
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
            at = @At("RETURN"))
    private void onix$scoreboardEnd(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics context) {
        if (onix$scoreboardPushed) {
            context.flush();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            context.pose().popPose();
            onix$scoreboardPushed = false;
        }
    }
}
