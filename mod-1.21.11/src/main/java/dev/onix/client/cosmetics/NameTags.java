package dev.onix.client.cosmetics;

import dev.onix.client.OnixClient;
import dev.onix.client.network.OnixPresence;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * Name styles: colours the player's name above the head and in the tab list. Your own comes from {@link Cosmetics};
 * other players' from the Onix server ({@link OnixPresence#look}), which only hands out what they own. The looks are
 * the same as in the launcher (src/js/cosmetics.js) and tools/nametags.py.
 */
public final class NameTags {
    /** The badge of owners (everyone else's is grey). */
    public static final int OWNER_VIOLET = 0xFFB46CFF;

    private NameTags() {}

    private static UUID self() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getUser() == null ? null : mc.getUser().getProfileId();
    }

    /** The name style of a player (yours includes what you try on), or null. */
    public static CosmeticCatalog.Item styleOf(UUID id) {
        if (id == null) return null;
        if (id.equals(self())) return Cosmetics.shown(Cosmetics.Slot.NAMETAG);
        if (!OnixClient.CONFIG.nameTagLooks.isOn()) return null;
        String[] raw = OnixPresence.look(id);
        if (raw == null) return null;
        CosmeticCatalog.Item style = CosmeticCatalog.item(raw[0]);
        return style != null && style.slot() == Cosmetics.Slot.NAMETAG ? style : null;
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

    /** A name in a style (menus). */
    public static MutableComponent preview(String name, CosmeticCatalog.Item style) {
        return style != null ? paint(name, style.look(), Style.EMPTY) : Component.literal(name);
    }

    /**
     * Applies a player's name style to a name as the game shows it (it may hold a team prefix or a rank): only the
     * player's name inside it is coloured, everything else keeps its style.
     */
    public static Component decorate(UUID id, String name, Component text) {
        if (text == null || name == null || name.isEmpty()) return text;
        CosmeticCatalog.Item style = styleOf(id);
        if (style == null) return text;
        List<String> parts = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        text.visit((st, s) -> {
            parts.add(s);
            styles.add(st);
            return Optional.empty();
        }, Style.EMPTY);
        String plain = String.join("", parts);
        int start = plain.indexOf(name);
        if (start < 0) return text; // a nickname
        int end = start + name.length();
        MutableComponent out = Component.empty();
        int pos = 0;
        boolean done = false;
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
            if (!done) {
                done = true;
                out.append(paint(plain.substring(start, end), style.look(), styles.get(k)));
            }
            if (b > end) out.append(Component.literal(s.substring(end - a)).withStyle(styles.get(k)));
        }
        return out;
    }
}
