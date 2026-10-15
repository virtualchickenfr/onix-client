package dev.onix.client.module.impl.chat;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.TextSetting;
import dev.onix.client.util.ChatPipeline;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Adds the time each chat message arrived. */
public class ChatTimestampsModule extends Module implements ChatPipeline.Processor {
    private final ModeSetting format = add(new ModeSetting("format", "Format", "HH:mm", "HH:mm:ss", "h:mm a", "Custom"));
    private final TextSetting pattern = add(new TextSetting("pattern", "Custom Pattern", "HH:mm:ss", 32).placeholder("HH:mm:ss")
            .describe("Java time pattern, e.g. HH:mm, hh:mm a or HH:mm:ss.SSS.").visibleIf(() -> format.is("Custom")));
    private final ModeSetting brackets = add(new ModeSetting("brackets", "Brackets", "[ ]", "( )", "< >", "None"));
    private final ModeSetting position = add(new ModeSetting("position", "Position", "Before", "After"));

    {
        group(GROUP_APPEARANCE);
    }

    private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFF8A8A93));
    private final BoolSetting italic = add(new BoolSetting("italic", "Italic", false));
    private final BoolSetting bold = add(new BoolSetting("bold", "Bold", false));

    private String cachedPattern;
    private DateTimeFormatter cachedFormatter;

    public ChatTimestampsModule() {
        super("timestamps", "Chat Timestamps", "Shows when each message arrived.", Category.CHAT, false);
        tags("time", "clock");
    }

    @Override
    public void init() {
        ChatPipeline.register(this);
    }

    @Override
    public int priority() {
        return 100;
    }

    private DateTimeFormatter formatter() {
        String p = format.is("Custom") ? pattern.get() : format.get();
        if (!p.equals(cachedPattern)) {
            cachedPattern = p;
            try {
                cachedFormatter = DateTimeFormatter.ofPattern(p, Locale.ENGLISH);
            } catch (IllegalArgumentException e) {
                cachedFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
            }
        }
        return cachedFormatter;
    }

    @Override
    public Text process(Text message) {
        if (!isEnabled()) return message;
        String time;
        try {
            time = LocalTime.now().format(formatter());
        } catch (RuntimeException e) {
            time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH));
        }
        String stamp = switch (brackets.get()) {
            case "( )" -> "(" + time + ")";
            case "< >" -> "<" + time + ">";
            case "None" -> time;
            default -> "[" + time + "]";
        };
        int rgb = color.color() & 0xFFFFFF;
        boolean after = position.is("After");
        MutableText styled = Text.literal(after ? " " + stamp : stamp + " ")
                .styled(s -> s.withColor(TextColor.fromRgb(rgb)).withItalic(italic.isOn()).withBold(bold.isOn()));
        return after ? Text.empty().append(message).append(styled) : Text.empty().append(styled).append(message);
    }
}
