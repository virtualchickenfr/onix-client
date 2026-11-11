package dev.onix.client.mixin;

import dev.onix.client.module.impl.chat.ChatCopyModule;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat Copy: Ctrl + click on a chat message copies it (one injector per Minecraft input API; the other is skipped). */
@Mixin(ChatScreen.class)
public abstract class ChatCopyMixin {
    /** 1.21.9 and newer: the click is an event with its modifier keys. */
    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void onix$copy(net.minecraft.client.input.MouseButtonEvent click, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ChatCopyModule.onClick(click.x(), click.y(), click.button(), click.modifiers())) cir.setReturnValue(true);
    }

    /** 1.21.8 and older: x, y and the button. */
    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void onix$copyOld(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (ChatCopyModule.onClick(x, y, button, 0)) cir.setReturnValue(true);
    }
}
