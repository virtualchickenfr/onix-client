package dev.onix.client.mixin;

import dev.onix.client.util.ExtraKeys;
import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardMixin {
    /** Extra keys from the Extra Keybinds module act like the vanilla key of their action. */
    @Inject(method = "onKey", at = @At("HEAD"))
    private void onix$extraKeys(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        ExtraKeys.onInput(key, action);
    }
}
