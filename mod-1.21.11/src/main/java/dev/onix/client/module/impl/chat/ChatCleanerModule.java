package dev.onix.client.module.impl.chat;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.ChatPipeline;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** Hides repeated messages and empty lines. */
public class ChatCleanerModule extends Module implements ChatPipeline.Processor {
    private final BoolSetting duplicates = add(new BoolSetting("duplicates", "Hide Duplicates", true));
    private final NumberSetting window = add(new NumberSetting("window", "Duplicate Window", 10, 2, 120, 1).unit("s").visibleIf(duplicates::isOn));
    private final BoolSetting emptyLines = add(new BoolSetting("empty", "Remove Empty Lines", true));
    private final BoolSetting separators = add(new BoolSetting("separators", "Remove Separator Lines", false)
            .describe("Lines made only of dashes, equals signs or similar, common on server networks."));

    private final Map<String, Long> recent = new HashMap<>();

    public ChatCleanerModule() {
        super("chatcleaner", "Chat Cleaner", "Hide repeated messages, empty and separator lines.", Category.CHAT, false);
        tags("spam", "duplicate", "clean chat");
    }

    @Override
    public void init() {
        ChatPipeline.register(this);
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public Component process(Component message) {
        if (!isEnabled()) return message;
        String text = message.getString();
        if (emptyLines.isOn() && text.isBlank()) return null;
        if (separators.isOn() && text.strip().matches("[-=_*~▬━─#.\\s]{6,}")) return null;
        if (duplicates.isOn()) {
            long now = System.currentTimeMillis();
            long limit = (long) (window.get() * 1000);
            recent.values().removeIf(t -> now - t > limit);
            if (recent.containsKey(text)) return null;
            recent.put(text, now);
        }
        return message;
    }
}
