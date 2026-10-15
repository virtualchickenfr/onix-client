package dev.onix.client.module.impl.chat;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.setting.TextSetting;
import dev.onix.client.util.ChatPipeline;
import net.minecraft.text.Text;

import java.util.Locale;

/** Says "gg" when a game ends. Triggers are configurable for any server. */
public class AutoGgModule extends Module implements ChatPipeline.Processor {
    private final TextSetting message = add(new TextSetting("message", "Message", "gg", 100));
    private final TextSetting triggers = add(new TextSetting("triggers", "Trigger Phrases",
            "winner:, 1st killer, victory!, you won, game over, won the game", 400)
            .describe("Comma separated. When a chat line contains one of these, the message is sent."));
    private final NumberSetting delay = add(new NumberSetting("delay", "Delay", 1, 0, 10, 0.5).unit("s"));
    private final NumberSetting cooldown = add(new NumberSetting("cooldown", "Cooldown", 15, 5, 120, 5).unit("s")
            .describe("Minimum time between two automatic messages."));

    private long lastSent;
    private long sendAt = -1;

    public AutoGgModule() {
        super("autogg", "Auto GG", "Sends \"gg\" automatically when a game ends.", Category.CHAT, false);
        restricted("Automatic chat messages can count as spam on some servers.");
        tags("good game", "minigames");
    }

    @Override
    public void init() {
        ChatPipeline.register(this);
    }

    @Override
    public int priority() {
        return -10; // observe before anything hides the line
    }

    @Override
    public Text process(Text text) {
        if (!isEnabled()) return text;
        String line = text.getString().toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        if (now - lastSent < cooldown.get() * 1000 || sendAt > 0) return text;
        for (String t : triggers.get().split(",")) {
            String trigger = t.trim().toLowerCase(Locale.ROOT);
            if (!trigger.isEmpty() && line.contains(trigger)) {
                sendAt = now + (long) (delay.get() * 1000);
                break;
            }
        }
        return text;
    }

    @Override
    public void onTick() {
        if (sendAt > 0 && System.currentTimeMillis() >= sendAt) {
            sendAt = -1;
            lastSent = System.currentTimeMillis();
            if (mc().getNetworkHandler() != null && !message.get().isBlank()) {
                String msg = message.get().trim();
                if (msg.startsWith("/")) mc().getNetworkHandler().sendChatCommand(msg.substring(1));
                else mc().getNetworkHandler().sendChatMessage(msg);
            }
        }
    }
}
