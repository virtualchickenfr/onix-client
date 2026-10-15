package dev.onix.client.mixin;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.chat.ChatHistoryModule;
import dev.onix.client.util.ChatPipeline;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public abstract class ChatHudMixin {
    @Unique
    private boolean onix$reentry;

    /** Runs every incoming line through the chat pipeline (filters, mentions, timestamps …). */
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), cancellable = true)
    private void onix$pipeline(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        if (onix$reentry) return;
        Text processed = ChatPipeline.process(message);
        if (processed == null) {
            ci.cancel();
            return;
        }
        if (processed != message) {
            ci.cancel();
            onix$reentry = true;
            try {
                ((ChatHud) (Object) this).addMessage(processed, signature, indicator);
            } finally {
                onix$reentry = false;
            }
        }
    }

    @ModifyConstant(method = {"addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", "addVisibleMessage"},
            constant = @Constant(intValue = 100))
    private int onix$historyLimit(int vanilla) {
        if (OnixClient.MODULES == null) return vanilla;
        ChatHistoryModule history = OnixClient.MODULES.get(ChatHistoryModule.class);
        return history == null ? vanilla : history.limit(vanilla);
    }
}
