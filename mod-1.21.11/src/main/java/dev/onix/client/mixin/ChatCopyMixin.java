package dev.onix.client.mixin;

import dev.onix.client.module.impl.chat.ChatCopyModule;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat Copy: Ctrl + click on a chat message copies it. The click carries its modifier keys (ports before 1.21.9 turn
 * this into mouseClicked(double, double, int) with a file rule in ports/<version>/port.json).
 */
@Mixin(ChatScreen.class)
public abstract class ChatCopyMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onix$copy(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ChatCopyModule.onClick(click.x(), click.y(), click.button(), click.modifiers())) cir.setReturnValue(true);
    }
}
