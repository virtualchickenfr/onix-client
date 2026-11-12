package dev.onix.client.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.onix.client.OnixClient;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Onix's own translations (English is built in, German comes from assets/onix/lang_onix/de.json). Independent of
 * Minecraft's language so the menus can be German while the game stays English and the other way round.
 */
public final class I18n {
    private I18n() {}

    private static Map<String, String> german;

    public static boolean german() {
        return OnixClient.CONFIG != null && OnixClient.CONFIG.language.is("Deutsch");
    }

    private static Map<String, String> table() {
        if (german != null) return german;
        Map<String, String> m = new HashMap<>();
        try (InputStream in = I18n.class.getResourceAsStream("/assets/onix/lang_onix/de.json")) {
            if (in != null) {
                JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : o.entrySet()) m.put(e.getKey(), e.getValue().getAsString());
            }
        } catch (Exception e) {
            OnixClient.LOGGER.warn("Could not load the German translation", e);
        }
        german = m;
        return m;
    }

    /** The translation for a key, or the English text. */
    public static String t(String key, String english) {
        if (!german()) return english;
        return table().getOrDefault(key, english);
    }

    /** The German translation for a key, or the English text (whatever the language setting; for the website list). */
    public static String german(String key, String english) {
        return table().getOrDefault(key, english);
    }

    /** UI text: the English text is the key. */
    public static String ui(String english) {
        return t("ui." + english, english);
    }
}
