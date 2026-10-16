package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.network.OnixPresence;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.setting.TextSetting;
import dev.onix.client.util.ChatPipeline;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The Onix badge: a small Onix logo next to the names of players who use Onix – in
 * nametags, the tab list and chat. Your own badge always shows; other players are
 * recognised through an Onix presence server (see /server in the repository).
 */
public class OnixBadgeModule extends Module implements ChatPipeline.Processor {
    /** The badge glyph from assets/onix/font/badge.json. */
    public static final String GLYPH = "";
    private static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath("onix", "badge");

    private final BoolSetting self = add(new BoolSetting("self", "Show on Yourself", true));
    private final BoolSetting nametags = add(new BoolSetting("nametags", "Nametags", true));
    private final BoolSetting tabList = add(new BoolSetting("tabList", "Tab List", true));
    private final BoolSetting chat = add(new BoolSetting("chat", "Chat", true)
            .describe("Adds the badge to chat lines that start with the name of an Onix player."));
    private final ModeSetting position = add(new ModeSetting("position", "Position", "Before Name", "After Name"));

    {
        group(GROUP_APPEARANCE);
    }

    private final ColorSetting color = add(ColorSetting.accent("color", "Badge Color"));
    private final ColorSetting selfColor = add(ColorSetting.accent("selfColor", "Own Badge Color"));

    {
        group("Network");
    }

    private final BoolSetting share = add(new BoolSetting("share", "Show Others I Use Onix", true)
            .describe("Lets other Onix players see your badge. Turn off to stay invisible."));
    /** Older versions kept the server address here; it moved to Settings → Onix Server. */
    private final TextSetting server = add(new TextSetting("server", "Badge Server", "", 200).hidden());
    private final NumberSetting lookup = add(new NumberSetting("lookup", "Refresh Every", 15, 5, 120, 5).unit("s"));

    public OnixBadgeModule() {
        super("badge", "Onix Badge", "An Onix logo next to every player who uses Onix – including you.", Category.VISUAL, true);
        tags("client icon", "client tag", "onix users", "logo", "nametag icon");
    }

    @Override
    public void init() {
        ChatPipeline.register(this);
    }

    /** The server address from older versions (now in Settings → Onix Server). */
    public String legacyServer() {
        return server.get();
    }

    public boolean sharing() {
        return share.isOn();
    }

    public int lookupSeconds() {
        return lookup.getInt();
    }

    @Override
    protected void onDisable() {
        OnixPresence.leave();
    }

    @Override
    public void onShutdown() {
        if (isEnabled()) OnixPresence.leave();
    }

    @Override
    public String statusLine() {
        return dev.onix.client.OnixClient.serverUrl().isBlank() ? "Only your own badge is shown – set the Onix Server in Settings" : OnixPresence.status();
    }

    // ------------------------------------------------------------------ queries

    private UUID selfId() {
        return mc().getUser() == null ? null : mc().getUser().getProfileId();
    }

    public boolean hasBadge(UUID id) {
        if (!isEnabled() || id == null) return false;
        if (id.equals(selfId())) return self.isOn();
        return OnixPresence.isOnixUser(id);
    }

    public MutableComponent badge(UUID id) {
        int c = (id != null && id.equals(selfId()) ? selfColor : color).color() & 0xFFFFFF;
        return Component.literal(GLYPH).withStyle(s -> s.withFont(FONT).withColor(TextColor.fromRgb(c)).withBold(false).withItalic(false));
    }

    private Component attach(UUID id, Component name) {
        boolean after = position.is("After Name");
        return after ? Component.empty().append(name).append(" ").append(badge(id))
                : Component.empty().append(badge(id)).append(" ").append(name);
    }

    // ------------------------------------------------------------------ hooks

    /** Nametags (EntityRendererMixin). */
    public Component decorateNametag(Entity entity, Component text) {
        if (!nametags.isOn() || !(entity instanceof Player player)) return text;
        UUID id = player.getUUID();
        return hasBadge(id) ? attach(id, text) : text;
    }

    /** Tab list names (PlayerListHudMixin). */
    public Component decorateTabName(PlayerInfo entry, Component name) {
        if (!tabList.isOn() || entry == null) return name;
        UUID id = entry.getProfile().getId();
        return hasBadge(id) ? attach(id, name) : name;
    }

    @Override
    public int priority() {
        return 60;
    }

    @Override
    public Component process(Component message) {
        if (!isEnabled() || !chat.isOn() || mc().getConnection() == null) return message;
        String plain = message.getString();
        String head = plain.length() > 48 ? plain.substring(0, 48) : plain;
        for (PlayerInfo entry : mc().getConnection().getOnlinePlayers()) {
            UUID id = entry.getProfile().getId();
            if (!hasBadge(id)) continue;
            String name = entry.getProfile().getName();
            if (name == null || name.isEmpty()) continue;
            Pattern p = Pattern.compile("(^|[^A-Za-z0-9_])" + Pattern.quote(name) + "([^A-Za-z0-9_]|$)");
            if (p.matcher(head).find() && head.toLowerCase(Locale.ROOT).indexOf(name.toLowerCase(Locale.ROOT)) < 24) {
                return Component.empty().append(badge(id)).append(" ").append(message);
            }
        }
        return message;
    }
}
