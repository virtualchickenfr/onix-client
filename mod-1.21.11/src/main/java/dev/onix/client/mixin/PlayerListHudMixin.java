package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.OnixBadgeModule;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.onix.client.module.impl.visual.TabListModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the ping as a number instead of signal bars. */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudMixin {
    @Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true)
    private void onix$numericPing(CallbackInfo ci,
                                  @Local(argsOnly = true) GuiGraphics context,
                                  @Local(argsOnly = true, ordinal = 0) int width,
                                  @Local(argsOnly = true, ordinal = 1) int x,
                                  @Local(argsOnly = true, ordinal = 2) int y,
                                  @Local(argsOnly = true) PlayerInfo entry) {
        if (OnixClient.MODULES == null) return;
        TabListModule tab = OnixClient.MODULES.get(TabListModule.class);
        if (tab == null || !tab.isEnabled() || !tab.numericPing.isOn()) return;
        ci.cancel();
        var font = Minecraft.getInstance().font;
        int ms = entry.getLatency();
        String text = String.valueOf(ms);
        context.pose().pushMatrix();
        context.pose().translate(x + width - 1 - font.width(text) * 0.75f, y + 1.5f);
        context.pose().scale(0.75f, 0.75f);
        context.drawString(font, text, 0, 0, tab.pingColor(ms), true);
        context.pose().popMatrix();
    }

    /** Onix badge in front of (or after) the names of Onix players. */
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void onix$badge(PlayerInfo entry, CallbackInfoReturnable<Component> cir) {
        if (OnixClient.MODULES == null) return;
        Component name = dev.onix.client.cosmetics.NameTags.decorate(entry.getProfile().id(), entry.getProfile().name(), cir.getReturnValue());
        OnixBadgeModule badge = OnixClient.MODULES.get(OnixBadgeModule.class);
        cir.setReturnValue(badge != null && badge.isEnabled() ? badge.decorateTabName(entry, name) : name);
    }
}
