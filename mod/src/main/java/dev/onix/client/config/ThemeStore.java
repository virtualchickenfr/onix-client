package dev.onix.client.config;

import com.google.gson.JsonObject;
import dev.onix.client.OnixClient;
import dev.onix.client.gui.ThemeDef;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.Stream;

/**
 * Built-in and custom themes. Custom themes live in config/onix/themes/&lt;id&gt;.json:
 * <pre>{"id": "custom-1a2b3c", "name": "Midnight", "accent": "#8B7CFF", "panel": "#0E0C17",
 *  "sky": "#7C6CF6", "particle": "#C4BAFF", "ambient": "Sparks"}</pre>
 */
public final class ThemeStore {
    private static final Random RANDOM = new Random();
    private static List<ThemeDef> custom = new ArrayList<>();
    private static long lastScan;

    private ThemeStore() {}

    private static Path dir() {
        return ConfigFiles.dir().resolve("themes");
    }

    /** Built-in themes followed by custom ones. */
    public static List<ThemeDef> all() {
        rescanIfStale();
        List<ThemeDef> list = new ArrayList<>(List.of(ThemeDef.BUILT_IN));
        list.addAll(custom);
        return Collections.unmodifiableList(list);
    }

    public static List<ThemeDef> custom() {
        rescanIfStale();
        return Collections.unmodifiableList(custom);
    }

    public static ThemeDef byId(String id) {
        if (id != null) {
            for (ThemeDef t : ThemeDef.BUILT_IN) if (t.id().equalsIgnoreCase(id) || t.name().equalsIgnoreCase(id)) return t;
            for (ThemeDef t : custom()) if (t.id().equals(id)) return t;
        }
        return ThemeDef.BUILT_IN[0];
    }

    /** Re-reads the folder at most every two seconds, so themes made in the launcher show up. */
    private static void rescanIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastScan < 2000) return;
        lastScan = now;
        reload();
    }

    public static void reload() {
        List<ThemeDef> found = new ArrayList<>();
        if (Files.isDirectory(dir())) {
            try (Stream<Path> files = Files.list(dir())) {
                files.filter(p -> p.toString().endsWith(".json")).sorted().forEach(p -> {
                    ThemeDef t = read(p);
                    if (t != null) found.add(t);
                });
            } catch (Exception e) {
                OnixClient.LOGGER.warn("Could not list themes", e);
            }
        }
        custom = found;
    }

    /** Creates a custom copy of a theme with a free name ("Aurora Copy", "My Theme 2" …). */
    public static ThemeDef create(ThemeDef base, String preferredName) {
        String name = preferredName;
        int n = 2;
        while (nameTaken(name)) name = preferredName + " " + n++;
        String id;
        do {
            id = String.format(Locale.ROOT, "custom-%06x", RANDOM.nextInt(0x1000000));
        } while (Files.exists(dir().resolve(id + ".json")));
        ThemeDef theme = base.copyAs(id, name);
        save(theme);
        return theme;
    }

    private static boolean nameTaken(String name) {
        for (ThemeDef t : all()) if (t.name().equalsIgnoreCase(name)) return true;
        return false;
    }

    public static void save(ThemeDef theme) {
        if (!theme.custom()) return;
        JsonObject json = new JsonObject();
        json.addProperty("id", theme.id());
        json.addProperty("name", theme.name());
        json.addProperty("accent", hex(theme.accent()));
        json.addProperty("panel", hex(theme.panel()));
        json.addProperty("sky", hex(theme.sky()));
        json.addProperty("particle", hex(theme.particle()));
        json.addProperty("ambient", theme.ambient().displayName);
        ConfigFiles.write(dir().resolve(theme.id() + ".json"), json);
        replaceCached(theme);
    }

    public static void delete(String id) {
        try {
            Files.deleteIfExists(dir().resolve(id + ".json"));
        } catch (Exception e) {
            OnixClient.LOGGER.warn("Could not delete theme {}", id, e);
        }
        custom.removeIf(t -> t.id().equals(id));
    }

    private static void replaceCached(ThemeDef theme) {
        List<ThemeDef> list = new ArrayList<>(custom);
        list.removeIf(t -> t.id().equals(theme.id()));
        list.add(theme);
        list.sort((a, b) -> a.id().compareTo(b.id()));
        custom = list;
    }

    private static ThemeDef read(Path file) {
        JsonObject json = ConfigFiles.read(file);
        if (json == null) return null;
        try {
            // the id is used as a file name when saving or deleting, so it always comes from the file name
            String id = file.getFileName().toString().replaceFirst("\\.json$", "");
            String name = json.has("name") ? json.get("name").getAsString() : id;
            int accent = color(json, "accent", 0xFF8B7CFF);
            int panel = color(json, "panel", 0xFF0E0C17);
            int sky = color(json, "sky", accent);
            int particle = color(json, "particle", 0xFFFFFFFF);
            ThemeDef.Ambient ambient = ThemeDef.Ambient.byName(json.has("ambient") ? json.get("ambient").getAsString() : "Sparks");
            return new ThemeDef(id, name.isBlank() ? id : name, accent, panel, sky, ambient, particle, true);
        } catch (Exception e) {
            OnixClient.LOGGER.warn("Skipping broken theme file {}", file, e);
            return null;
        }
    }

    private static int color(JsonObject json, String key, int fallback) {
        if (!json.has(key)) return fallback;
        String s = json.get(key).getAsString().replace("#", "");
        try {
            return 0xFF000000 | Integer.parseInt(s.length() > 6 ? s.substring(s.length() - 6) : s, 16);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public static String hex(int argb) {
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }
}
