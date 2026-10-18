package dev.onix.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.BossBarModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public abstract class BossBarHudMixin {
    @Unique
    private boolean onix$pushed;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onix$start(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics context) {
        onix$pushed = false;
        if (OnixClient.MODULES == null) return;
        BossBarModule m = OnixClient.MODULES.get(BossBarModule.class);
        if (m == null || !m.isEnabled()) return;
        if (m.hidden()) {
            ci.cancel();
            return;
        }
        float s = m.scale.getFloat();
        float cx = context.guiWidth() / 2f;
        context.pose().pushMatrix();
        context.pose().translate(cx + m.offsetX.getFloat(), m.offsetY.getFloat());
        context.pose().scale(s, s);
        context.pose().translate(-cx, 0);
        onix$pushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onix$end(CallbackInfo ci, @Local(argsOnly = true) GuiGraphics context) {
        if (onix$pushed) {
            context.pose().popMatrix();
            onix$pushed = false;
        }
    }
}
