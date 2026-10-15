package dev.onix.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.llamalad7.mixinextras.sugar.Local;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.BossBarModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {
    @Unique
    private boolean onix$pushed;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onix$start(CallbackInfo ci, @Local(argsOnly = true) DrawContext context) {
        onix$pushed = false;
        if (OnixClient.MODULES == null) return;
        BossBarModule m = OnixClient.MODULES.get(BossBarModule.class);
        if (m == null || !m.isEnabled()) return;
        if (m.hidden()) {
            ci.cancel();
            return;
        }
        float s = m.scale.getFloat();
        float cx = context.getScaledWindowWidth() / 2f;
        context.getMatrices().push();
        context.getMatrices().translate(cx + m.offsetX.getFloat(), m.offsetY.getFloat(), 0);
        context.draw(); // flush earlier HUD elements so only the bars are faded
        RenderSystem.setShaderColor(1f, 1f, 1f, m.opacity.getFloat() / 100f);
        context.getMatrices().scale(s, s, 1f);
        context.getMatrices().translate(-cx, 0, 0);
        onix$pushed = true;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onix$end(CallbackInfo ci, @Local(argsOnly = true) DrawContext context) {
        if (onix$pushed) {
            context.draw();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            context.getMatrices().pop();
            onix$pushed = false;
        }
    }
}
