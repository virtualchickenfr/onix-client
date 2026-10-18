package dev.onix.client.module.impl.chat;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.TextSetting;
import dev.onix.client.util.ChatPipeline;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Hides or censors messages that contain words you choose. */
public class ChatFilterModule extends Module implements ChatPipeline.Processor {
    private final TextSetting words = add(new TextSetting("words", "Words", "", 500)
            .describe("Comma separated list of words or phrases.").placeholder("advert, discord.gg"));
    private final ModeSetting action = add(new ModeSetting("action", "Action", "Hide Message", "Censor Word"));
    private final BoolSetting caseSensitive = add(new BoolSetting("case", "Case Sensitive", false));
    private final BoolSetting onlyPlayerChat = add(new BoolSetting("onlyPlayers", "Only Player Messages", false)
            .describe("Skip server messages (those without a <name> or name: prefix)."));

    public ChatFilterModule() {
        super("chatfilter", "Chat Filter", "Hide or censor messages with certain words.", Category.CHAT, false);
        tags("block words", "censor", "mute");
    }

    @Override
    public void init() {
        ChatPipeline.register(this);
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public Component process(Component message) {
        if (!isEnabled() || words.get().isBlank()) return message;
        String text = message.getString();
        if (onlyPlayerChat.isOn() && !(text.startsWith("<") || text.matches("^[\\w\\[\\] ]{1,40}: .*"))) return message;
        String haystack = caseSensitive.isOn() ? text : text.toLowerCase(Locale.ROOT);
        boolean hit = false;
        String censored = text;
        for (String w : words.get().split(",")) {
            String word = w.trim();
            if (word.isEmpty()) continue;
            String needle = caseSensitive.isOn() ? word : word.toLowerCase(Locale.ROOT);
            if (haystack.contains(needle)) {
                hit = true;
                censored = censored.replaceAll((caseSensitive.isOn() ? "" : "(?i)") + java.util.regex.Pattern.quote(word), "*".repeat(word.length()));
            }
        }
        if (!hit) return message;
        return action.is("Hide Message") ? null : Component.literal(censored);
    }
}
