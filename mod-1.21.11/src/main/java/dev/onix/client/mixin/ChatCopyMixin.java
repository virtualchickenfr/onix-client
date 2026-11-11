package dev.onix.client.mixin;

import dev.onix.client.module.impl.chat.ChatCopyModule;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat Copy: Ctrl + click on a chat message copies it. */
@Mixin(ChatScreen.class)
public abstract class ChatCopyMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void onix$copy(CallbackInfoReturnable<Boolean> cir) {
        if (ChatCopyModule.tryCopy()) cir.setReturnValue(true);
    }
}
