package dev.onix.client.cosmetics;

import dev.onix.client.OnixClient;
import dev.onix.client.network.OnixPresence;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Name styles and tags: colours the player's name and puts a tag in front of it, above the head and in the tab list.
 * Your own come from {@link Cosmetics}; other players' from the Onix server ({@link OnixPresence#look}), which only
 * hands out what they own. The looks are the same as in the launcher (src/js/cosmetics.js) and tools/nametags.py.
 */
public final class NameTags {
    /** What a custom tag may contain (the server checks the same). */
    public static final int CUSTOM_MAX = 12;
    private static final Pattern ALLOWED = Pattern.compile("[\\p{L}\\p{N} \\-_.!?#&+*'★♥✦☀☾❄♪✿⚔☠♛♦♣♠☺✔⚡☁☂☃♫]+");
    /** Tags that would pretend to be staff, or that insult people. Compared without spaces, dots and case. */
    private static final List<String> BLOCKED_PARTS = List.of("admin", "owner", "moderator", "staff", "helper", "developer", "support",
            "mojang", "microsoft", "official", "nigg", "fagg", "hitler", "nazi", "retard", "fuck", "cunt", "whore", "hurensohn",
            "wichser", "fotze", "schwuchtel", "missgeburt", "spast", "nutte", "kys");
    private static final List<String> BLOCKED_WORDS = List.of("mod", "dev", "gm", "op", "fag", "hure", "sex");
    private static final int BRACKET = 0xFF9A9AA6;

    /** One player's name style, tag and custom tag text; any part may be null. */
    public record PlayerLook(CosmeticCatalog.Item style, CosmeticCatalog.Item tag, String text) {
        public boolean empty() {
            return style == null && tag == null;
        }
    }

    private NameTags() {}

    // ---------------------------------------------------------------- custom text

    /** Null if the text may be used as a custom tag, otherwise why not. */
    public static String problem(String text) {
        if (text == null || text.isBlank()) return "Type some text for your tag.";
        if (text.length() > CUSTOM_MAX) return "At most " + CUSTOM_MAX + " characters.";
        if (!ALLOWED.matcher(text).matches()) return "Letters, numbers, spaces and a few symbols only.";
        String flat = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
        for (String bad : BLOCKED_PARTS) if (flat.contains(bad)) return "This tag is not allowed.";
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (BLOCKED_WORDS.contains(word)) return "This tag is not allowed.";
        }
        return null;
    }

    // ---------------------------------------------------------------- whose look

    private static UUID self() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getUser() == null ? null : mc.getUser().getProfileId();
    }

    /** The look of a player: yours from your selection (including what you try on), others' from the Onix server. */
    public static PlayerLook lookOf(UUID id) {
        if (id == null) return null;
        if (id.equals(self())) {
            CosmeticCatalog.Item tag = Cosmetics.shown(Cosmetics.Slot.TAG);
            String text = tag != null && tag.custom() ? (Cosmetics.tagText().isEmpty() ? "TAG" : Cosmetics.tagText()) : null;
            PlayerLook look = new PlayerLook(Cosmetics.shown(Cosmetics.Slot.NAMETAG), tag, text);
            return look.empty() ? null : look;
        }
        if (!OnixClient.CONFIG.nameTagLooks.isOn()) return null;
        String[] raw = OnixPresence.look(id);
        if (raw == null) return null;
        CosmeticCatalog.Item style = CosmeticCatalog.item(raw[0]);
        if (style != null && style.slot() != Cosmetics.Slot.NAMETAG) style = null;
        CosmeticCatalog.Item tag = CosmeticCatalog.item(raw[1]);
        if (tag != null && tag.slot() != Cosmetics.Slot.TAG) tag = null;
        String text = null;
        if (tag != null && tag.custom()) {
            text = raw[2];
            if (text == null || problem(text) != null) tag = null;
        }
        PlayerLook look = new PlayerLook(style, tag, text);
        return look.empty() ? null : look;
    }

    // ---------------------------------------------------------------- drawing

    private static float now() {
        return (System.currentTimeMillis() % 3_600_000L) / 50f;
    }

    /** The colour of letter i of n in a look at time t (ticks). */
    public static int color(CosmeticCatalog.Look look, int i, int n, float t) {
        int[] c = look.colors();
        float speed = look.speed();
        switch (look.type()) {
            case "gradient" -> {
                return c.length == 0 ? 0xFFFFFFFF : stops(c, n <= 1 ? 0 : i / (float) (n - 1), false);
            }
            case "wave" -> {
                float p = i / (float) Math.max(n, 6) - t * 0.02f * speed;
                return c.length == 0 ? 0xFFFFFFFF : stops(c, p - (float) Math.floor(p), true);
            }
            case "rainbow" -> {
                float h = i * 0.07f - t * 0.012f * speed;
                return hsv(h - (float) Math.floor(h), 0.62f, 1f);
            }
            case "pulse" -> {
                float f = 0.5f + 0.5f * (float) Math.sin(t * 0.15f * speed);
                return c.length < 2 ? (c.length == 1 ? c[0] : 0xFFFFFFFF) : lerp(c[0], c[1], f);
            }
            case "shine" -> {
                float span = n + 10;
                float p = (t * 0.5f * speed) % span - 5;
                float w = Math.max(0, 1 - Math.abs(i - p) / 2.5f);
                return c.length < 2 ? (c.length == 1 ? c[0] : 0xFFFFFFFF) : lerp(c[0], c[1], w);
            }
            default -> {
                return c.length == 0 ? 0xFFFFFFFF : c[0];
            }
        }
    }

    private static int hsv(float h, float s, float v) {
        float r = Math.abs(h * 6 - 3) - 1, g = 2 - Math.abs(h * 6 - 2), b = 2 - Math.abs(h * 6 - 4);
        r = v * (1 - s + s * Math.max(0, Math.min(1, r)));
        g = v * (1 - s + s * Math.max(0, Math.min(1, g)));
        b = v * (1 - s + s * Math.max(0, Math.min(1, b)));
        return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(g * 255) << 8 | Math.round(b * 255);
    }

    private static int stops(int[] c, float p, boolean cyclic) {
        if (c.length == 1) return c[0];
        int segs = cyclic ? c.length : c.length - 1;
        float x = Math.max(0, Math.min(0.9999f, p)) * segs;
        int k = (int) x;
        return lerp(c[k], c[(k + 1) % c.length], x - k);
    }

    private static int lerp(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    /** text with every letter coloured by the look. */
    public static MutableComponent paint(String text, CosmeticCatalog.Look look, Style base) {
        MutableComponent out = Component.empty();
        float t = now();
        int n = text.codePointCount(0, text.length());
        int i = 0;
        for (int off = 0; off < text.length(); ) {
            int cp = text.codePointAt(off);
            int col = color(look, i, n, t);
            Style s = base.withColor(TextColor.fromRgb(col & 0xFFFFFF));
            if (look.bold()) s = s.withBold(true);
            out.append(Component.literal(new String(Character.toChars(cp))).withStyle(s));
            off += Character.charCount(cp);
            i++;
        }
        return out;
    }

    /** The tag as shown in front of a name: "[TEXT]" (symbols without brackets), no trailing space. */
    public static MutableComponent tag(CosmeticCatalog.Item tag, String customText) {
        String text = tag.custom() ? customText : tag.tagText();
        if (text == null || text.isEmpty()) return Component.empty();
        boolean symbol = text.codePoints().noneMatch(Character::isLetterOrDigit);
        MutableComponent body = paint(text, tag.look(), Style.EMPTY);
        if (symbol) return body;
        Style br = Style.EMPTY.withColor(TextColor.fromRgb(BRACKET & 0xFFFFFF));
        return Component.empty().append(Component.literal("[").withStyle(br)).append(body).append(Component.literal("]").withStyle(br));
    }

    /** A preview of a whole look on a name (menus). */
    public static MutableComponent preview(String name, CosmeticCatalog.Item style, CosmeticCatalog.Item tag, String customText) {
        MutableComponent out = Component.empty();
        if (tag != null) out.append(tag(tag, customText)).append(" ");
        if (style != null) out.append(paint(name, style.look(), Style.EMPTY));
        else out.append(Component.literal(name));
        return out;
    }

    /**
     * Applies a player's look to a name as the game shows it (it may hold a team prefix or a rank): the player's
     * name inside it gets the name style, the tag goes right in front of it. Everything else keeps its style.
     */
    public static Component decorate(UUID id, String name, Component text) {
        if (text == null || name == null || name.isEmpty()) return text;
        PlayerLook look = lookOf(id);
        if (look == null) return text;
        List<String> parts = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        text.visit((style, s) -> {
            parts.add(s);
            styles.add(style);
            return Optional.empty();
        }, Style.EMPTY);
        String plain = String.join("", parts);
        int start = plain.indexOf(name);
        if (start < 0) {
            // a nickname: only the tag, in front of everything
            if (look.tag() == null) return text;
            return Component.empty().append(tag(look.tag(), look.text())).append(" ").append(text);
        }
        int end = start + name.length();
        MutableComponent out = Component.empty();
        int pos = 0;
        Style nameStyle = null;
        for (int k = 0; k < parts.size(); k++) {
            String s = parts.get(k);
            int a = pos;
            int b = pos + s.length();
            pos = b;
            if (b <= start || a >= end) {
                out.append(Component.literal(s).withStyle(styles.get(k)));
                continue;
            }
            if (a < start) out.append(Component.literal(s.substring(0, start - a)).withStyle(styles.get(k)));
            if (nameStyle == null) {
                nameStyle = styles.get(k);
                if (look.tag() != null) out.append(tag(look.tag(), look.text())).append(Component.literal(" ").withStyle(nameStyle));
                String whole = plain.substring(start, end);
                out.append(look.style() != null ? paint(whole, look.style().look(), nameStyle) : Component.literal(whole).withStyle(nameStyle));
            }
            if (b > end) out.append(Component.literal(s.substring(end - a)).withStyle(styles.get(k)));
        }
        return out;
    }
}
