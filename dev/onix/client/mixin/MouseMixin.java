package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.camera.CameraTweaksModule;
import dev.onix.client.module.impl.camera.ZoomModule;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.util.CpsTracker;
import dev.onix.client.util.ExtraKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseMixin {
    @Inject(method = "onMouseButton", at = @At("HEAD"))
    private void onix$countClicks(long window, int button, int action, int mods, CallbackInfo ci) {
        if (action == GLFW.GLFW_PRESS && Minecraft.getInstance().screen == null) CpsTracker.click(button);
        ExtraKeys.onInput(KeybindSetting.mouse(button), action);
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onix$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (OnixClient.MODULES == null || Minecraft.getInstance().screen != null) return;
        CameraTweaksModule camera = OnixClient.MODULES.get(CameraTweaksModule.class);
        if (camera != null && camera.onScroll(vertical)) {
            ci.cancel();
            return;
        }
        ZoomModule zoom = OnixClient.MODULES.get(ZoomModule.class);
        if (zoom != null && zoom.onScroll(vertical)) ci.cancel();
    }
}
