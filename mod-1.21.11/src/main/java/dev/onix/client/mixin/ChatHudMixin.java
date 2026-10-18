package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.chat.ChatHistoryModule;
import dev.onix.client.util.ChatPipeline;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {
    @Unique
    private boolean onix$reentry;

    /** Runs every incoming line through the chat pipeline (filters, mentions, timestamps …). */
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void onix$pipeline(Component message, MessageSignature signature, GuiMessageTag indicator, CallbackInfo ci) {
        if (onix$reentry) return;
        Component processed = ChatPipeline.process(message);
        if (processed == null) {
            ci.cancel();
            return;
        }
        if (processed != message) {
            ci.cancel();
            onix$reentry = true;
            try {
                ((ChatComponent) (Object) this).addMessage(processed, signature, indicator);
            } finally {
                onix$reentry = false;
            }
        }
    }

    @ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"},
            constant = @Constant(intValue = 100))
    private int onix$historyLimit(int vanilla) {
        if (OnixClient.MODULES == null) return vanilla;
        ChatHistoryModule history = OnixClient.MODULES.get(ChatHistoryModule.class);
        return history == null ? vanilla : history.limit(vanilla);
    }
}
