package dev.onix.client.mixin;

import dev.onix.client.util.OutgoingChat;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets chat modules change (or keep local) the messages you send. */
@Mixin(ClientPacketListener.class)
public abstract class ChatSendMixin {
    @Unique
    private static boolean onix$sending;

    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onix$outgoing(String message, CallbackInfo ci) {
        if (onix$sending) return;
        String out = OutgoingChat.process(message);
        if (out == null) {
            ci.cancel();
            return;
        }
        if (!out.equals(message)) {
            ci.cancel();
            onix$sending = true;
            try {
                ((ClientPacketListener) (Object) this).sendChat(out);
            } finally {
                onix$sending = false;
            }
        }
    }
}
