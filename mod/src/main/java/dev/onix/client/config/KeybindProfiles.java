package dev.onix.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.onix.client.OnixClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Named sets of Minecraft key bindings (vanilla and other mods), one file each in config/onix/keybinds/.
 * Separate from Onix profiles, so you can mix e.g. your "PvP" keys with any module setup –
 * or link them by giving both the same name.
 */
public final class KeybindProfiles {
    private static final String ACTIVE_FILE = "_active.json";

    private KeybindProfiles() {}

    private static Path dir() {
        return ConfigFiles.dir().resolve("keybinds");
    }

    private static Path file(String name) {
        return dir().resolve(name + ".json");
    }

    public static List<String> list() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(dir())) return names;
        try (Stream<Path> files = Files.list(dir())) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".json") && !n.equals(ACTIVE_FILE))
                    .map(n -> n.substring(0, n.length() - 5))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .forEach(names::add);
        } catch (Exception e) {
            OnixClient.LOGGER.warn("Could not list keybind profiles", e);
        }
        return names;
    }

    public static boolean exists(String name) {
        return Files.exists(file(name));
    }

    /** The profile applied last, or "" if none. */
    public static String active() {
        JsonObject json = ConfigFiles.read(dir().resolve(ACTIVE_FILE));
        return json != null && json.has("active") ? json.get("active").getAsString() : "";
    }

    private static void setActive(String name) {
        JsonObject json = new JsonObject();
        json.addProperty("active", name);
        ConfigFiles.write(dir().resolve(ACTIVE_FILE), json);
    }

    /** Saves the current key bindings under a name (overwrites). Returns false for an empty name. */
    public static boolean save(String rawName) {
        String name = ProfileManager.sanitize(rawName);
        MinecraftClient mc = MinecraftClient.getInstance();
        if (name.isEmpty() || mc.options == null) return false;
        JsonObject keys = new JsonObject();
        for (KeyBinding kb : mc.options.allKeys) keys.addProperty(kb.getTranslationKey(), kb.getBoundKeyTranslationKey());
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("keys", keys);
        ConfigFiles.write(file(name), root);
        setActive(name);
        return true;
    }

    /** Applies a saved profile. Bindings the file does not mention (e.g. from a mod added later) stay as they are. */
    public static int apply(String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        JsonObject root = ConfigFiles.read(file(name));
        if (root == null || mc.options == null || !root.has("keys")) return -1;
        JsonObject keys = root.getAsJsonObject("keys");
        int changed = 0;
        for (KeyBinding kb : mc.options.allKeys) {
            JsonElement value = keys.get(kb.getTranslationKey());
            if (value == null || !value.isJsonPrimitive()) continue;
            String wanted = value.getAsString();
            if (wanted.equals(kb.getBoundKeyTranslationKey())) continue;
            try {
                kb.setBoundKey(InputUtil.fromTranslationKey(wanted));
                changed++;
            } catch (Exception e) {
                OnixClient.LOGGER.warn("Unknown key {} for {}", wanted, kb.getTranslationKey());
            }
        }
        KeyBinding.updateKeysByCode();
        KeyBinding.unpressAll();
        mc.options.write();
        setActive(name);
        return changed;
    }

    public static boolean delete(String name) {
        try {
            boolean removed = Files.deleteIfExists(file(name));
            if (removed && name.equals(active())) setActive("");
            return removed;
        } catch (Exception e) {
            OnixClient.LOGGER.warn("Could not delete keybind profile {}", name, e);
            return false;
        }
    }
}
