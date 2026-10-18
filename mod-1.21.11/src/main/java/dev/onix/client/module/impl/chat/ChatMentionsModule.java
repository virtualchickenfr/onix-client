package dev.onix.client.module.impl.chat;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.setting.TextSetting;
import dev.onix.client.util.ChatPipeline;
import java.util.Locale;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.sounds.SoundEvents;

/** Highlights and pings you when your name or a keyword shows up in chat. */
public class ChatMentionsModule extends Module implements ChatPipeline.Processor {
    private final BoolSetting ownName = add(new BoolSetting("ownName", "Your Name", true));
    private final TextSetting keywords = add(new TextSetting("keywords", "Extra Keywords", "", 200)
            .describe("Comma separated, for example: nick, team, base.").placeholder("word, another word"));
    private final ModeSetting highlight = add(new ModeSetting("highlight", "Highlight", "Marker", "Whole Line", "None"));
    private final ColorSetting color = add(ColorSetting.accent("color", "Highlight Color").visibleIf(() -> !highlight.is("None")));
    private final BoolSetting sound = add(new BoolSetting("sound", "Sound", true));
    private final NumberSetting volume = add(new NumberSetting("volume", "Volume", 70, 0, 100, 5).unit("%").visibleIf(sound::isOn));
    private final BoolSetting ignoreOwn = add(new BoolSetting("ignoreOwn", "Ignore Your Own Messages", true));

    public ChatMentionsModule() {
        super("mentions", "Chat Mentions", "Highlight and sound when someone mentions you.", Category.CHAT, false);
        tags("ping", "highlight", "name");
    }

    @Override
    public void init() {
        ChatPipeline.register(this);
    }

    @Override
    public int priority() {
        return 50;
    }

    private boolean matches(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String name = mc().getUser().getName().toLowerCase(Locale.ROOT);
        if (ignoreOwn.isOn() && (lower.startsWith("<" + name + ">") || lower.contains(name + ":") && lower.indexOf(name) < 20)) {
            return false;
        }
        if (ownName.isOn() && lower.contains(name)) return true;
        for (String k : keywords.get().split(",")) {
            String word = k.trim().toLowerCase(Locale.ROOT);
            if (!word.isEmpty() && lower.contains(word)) return true;
        }
        return false;
    }

    @Override
    public Component process(Component message) {
        if (!isEnabled() || !matches(message.getString())) return message;
        if (sound.isOn()) {
            mc().execute(() -> mc().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, volume.getFloat() / 100f)));
        }
        TextColor c = TextColor.fromRgb(color.color() & 0xFFFFFF);
        return switch (highlight.get()) {
            case "Whole Line" -> Component.empty().withStyle(s -> s.withColor(c)).append(message);
            case "Marker" -> Component.empty().append(Component.literal("▌ ").withStyle(s -> s.withColor(c))).append(message);
            default -> message;
        };
    }
}
