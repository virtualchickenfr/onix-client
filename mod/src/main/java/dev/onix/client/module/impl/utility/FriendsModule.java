package dev.onix.client.module.impl.utility;

import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.TextSetting;
import dev.onix.client.util.ChatPipeline;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** A friend list: see when friends join or leave the server you are on, and spot them in chat. */
public class FriendsModule extends Module implements ChatPipeline.Processor {
    private final TextSetting friends = add(new TextSetting("friends", "Friends", "", 500)
            .placeholder("Name1, Name2")
            .describe("Minecraft names, comma separated."));

    {
        group("Notifications");
    }

    private final BoolSetting onJoin = add(new BoolSetting("onJoin", "When a Friend Joins", true));
    private final BoolSetting onLeave = add(new BoolSetting("onLeave", "When a Friend Leaves", false));
    private final BoolSetting summary = add(new BoolSetting("summary", "Who Is Online When You Join", true));
    private final BoolSetting sound = add(new BoolSetting("sound", "Sound", true));

    {
        group("Chat");
    }

    private final BoolSetting markChat = add(new BoolSetting("markChat", "Mark Friends' Messages", true)
            .describe("A small star in front of lines that contain a friend's name."));
    private final ColorSetting color = add(ColorSetting.accent("color", "Star Color").visibleIf(markChat::isOn));

    private ClientPlayNetworkHandler lastHandler;
    private Set<String> online = new HashSet<>();
    private int timer;

    public FriendsModule() {
        super("friends", "Friends", "Join and leave alerts for your friends, and a star next to their chat messages.",
                Category.UTILITY, false);
        tags("friend list", "buddies", "party");
    }

    private Set<String> list() {
        Set<String> names = new LinkedHashSet<>();
        for (String n : friends.get().split(",")) {
            String name = n.trim();
            if (!name.isEmpty()) names.add(name.toLowerCase(Locale.ROOT));
        }
        return names;
    }

    @Override
    public void init() {
        ChatPipeline.register(this);
    }

    @Override
    public int priority() {
        return 60;
    }

    @Override
    public Text process(Text message) {
        if (!isEnabled() || !markChat.isOn()) return message;
        String line = message.getString().toLowerCase(Locale.ROOT);
        for (String f : list()) {
            if (containsWord(line, f)) {
                TextColor c = TextColor.fromRgb(color.color() & 0xFFFFFF);
                return Text.empty().append(Text.literal("★ ").styled(s -> s.withColor(c))).append(message);
            }
        }
        return message;
    }

    private static boolean containsWord(String line, String word) {
        int i = line.indexOf(word);
        while (i >= 0) {
            boolean start = i == 0 || !isNameChar(line.charAt(i - 1));
            int end = i + word.length();
            boolean stop = end >= line.length() || !isNameChar(line.charAt(end));
            if (start && stop) return true;
            i = line.indexOf(word, i + 1);
        }
        return false;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    @Override
    public void onTick() {
        if (++timer < 20) return;
        timer = 0;
        ClientPlayNetworkHandler handler = mc().getNetworkHandler();
        if (handler == null || mc().isInSingleplayer()) {
            lastHandler = null;
            online = new HashSet<>();
            return;
        }
        Set<String> wanted = list();
        Set<String> now = new HashSet<>();
        for (PlayerListEntry e : handler.getPlayerList()) {
            String name = e.getProfile().getName();
            if (wanted.contains(name.toLowerCase(Locale.ROOT))) now.add(name);
        }
        if (handler != lastHandler) {
            // just joined: one summary instead of a join alert per friend
            lastHandler = handler;
            online = now;
            if (summary.isOn() && !now.isEmpty()) {
                notify("Friends online", String.join(", ", now));
            }
            return;
        }
        for (String name : now) if (!online.contains(name) && onJoin.isOn()) notify("Friend joined", name);
        for (String name : online) if (!now.contains(name) && onLeave.isOn()) notify("Friend left", name);
        online = now;
    }

    private void notify(String title, String text) {
        Notifications.show(title, text);
        if (sound.isOn()) {
            mc().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.4f, 0.5f));
        }
    }

    @Override
    public String statusLine() {
        int total = list().size();
        if (total == 0) return "Add names to get started";
        if (lastHandler == null) return total + (total == 1 ? " friend" : " friends");
        return online.size() + " of " + total + " online";
    }
}
