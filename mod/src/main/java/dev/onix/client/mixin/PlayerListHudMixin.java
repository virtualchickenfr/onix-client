package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.OnixBadgeModule;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.onix.client.module.impl.visual.TabListModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the ping as a number instead of signal bars. */
@Mixin(PlayerListHud.class)
public abstract class PlayerListHudMixin {
    @Inject(method = "renderLatencyIcon", at = @At("HEAD"), cancellable = true)
    private void onix$numericPing(CallbackInfo ci,
                                  @Local(argsOnly = true) DrawContext context,
                                  @Local(argsOnly = true, ordinal = 0) int width,
                                  @Local(argsOnly = true, ordinal = 1) int x,
                                  @Local(argsOnly = true, ordinal = 2) int y,
                                  @Local(argsOnly = true) PlayerListEntry entry) {
        if (OnixClient.MODULES == null) return;
        TabListModule tab = OnixClient.MODULES.get(TabListModule.class);
        if (tab == null || !tab.isEnabled() || !tab.numericPing.isOn()) return;
        ci.cancel();
        var font = MinecraftClient.getInstance().textRenderer;
        int ms = entry.getLatency();
        String text = String.valueOf(ms);
        context.getMatrices().push();
        context.getMatrices().translate(x + width - 1 - font.getWidth(text) * 0.75f, y + 1.5f, 0);
        context.getMatrices().scale(0.75f, 0.75f, 1f);
        context.drawText(font, text, 0, 0, tab.pingColor(ms), true);
        context.getMatrices().pop();
    }

    /** Onix badge in front of (or after) the names of Onix players. */
    @Inject(method = "getPlayerName", at = @At("RETURN"), cancellable = true)
    private void onix$badge(PlayerListEntry entry, CallbackInfoReturnable<Text> cir) {
        if (OnixClient.MODULES == null) return;
        OnixBadgeModule badge = OnixClient.MODULES.get(OnixBadgeModule.class);
        if (badge != null && badge.isEnabled()) cir.setReturnValue(badge.decorateTabName(entry, cir.getReturnValue()));
    }
}
