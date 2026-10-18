package dev.onix.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.onix.client.OnixClient;
import dev.onix.client.module.HudModule;
import dev.onix.client.module.Module;
import dev.onix.client.module.ModuleManager;
import dev.onix.client.module.impl.utility.KeybindProfilesModule;
import dev.onix.client.setting.Setting;
import dev.onix.client.util.ServerRules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * A profile stores which modules are on, all module settings and HUD positions.
 * Each profile is one file in config/onix/profiles/.
 */
public class ProfileManager {
    public static final String DEFAULT = "Default";
    private static final int MAX_NAME = 24;

    private final ModuleManager modules;
    private final OnixConfig config;

    public ProfileManager(ModuleManager modules, OnixConfig config) {
        this.modules = modules;
        this.config = config;
    }

    private static Path dir() {
        return ConfigFiles.dir().resolve("profiles");
    }

    private static Path file(String name) {
        return dir().resolve(name + ".json");
    }

    public String active() {
        return config.activeProfile;
    }

    public List<String> list() {
        List<String> names = new ArrayList<>();
        if (Files.isDirectory(dir())) {
            try (Stream<Path> files = Files.list(dir())) {
                files.map(p -> p.getFileName().toString())
                        .filter(n -> n.endsWith(".json"))
                        .map(n -> n.substring(0, n.length() - 5))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(names::add);
            } catch (Exception e) {
                OnixClient.LOGGER.warn("Could not list profiles", e);
            }
        }
        if (!names.contains(active())) names.add(0, active());
        return names;
    }

    /** Letters, digits, spaces, dashes and underscores only. */
    public static String sanitize(String name) {
        String clean = name.replaceAll("[^\\p{L}\\p{N} _-]", "").trim();
        return clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME).trim() : clean;
    }

    public void loadActive() {
        apply(ConfigFiles.read(file(active())));
    }

    public void saveActive() {
        ConfigFiles.write(file(active()), capture());
    }

    public void switchTo(String name) {
        if (name.equals(active())) return;
        saveActive();
        config.activeProfile = name;
        config.save();
        apply(ConfigFiles.read(file(name)));
    }

    /**
     * Activates a profile that was written to disk from outside (the launcher's "Send Profile").
     * Unlike {@link #switchTo} the current state is not saved first, so it cannot overwrite the received file.
     */
    public boolean receive(String name) {
        String clean = sanitize(name);
        if (clean.isEmpty() || !Files.exists(file(clean))) return false;
        config.activeProfile = clean;
        config.save();
        apply(ConfigFiles.read(file(clean)));
        return true;
    }

    /** Creates a profile as a copy of the current one and activates it. */
    public boolean create(String rawName) {
        String name = sanitize(rawName);
        if (name.isEmpty() || Files.exists(file(name)) || name.equals(active())) return false;
        saveActive();
        ConfigFiles.write(file(name), capture());
        config.activeProfile = name;
        config.save();
        return true;
    }

    public boolean delete(String name) {
        if (name.equals(active())) return false;
        try {
            return Files.deleteIfExists(file(name));
        } catch (Exception e) {
            OnixClient.LOGGER.warn("Could not delete profile {}", name, e);
            return false;
        }
    }

    /** Resets every module of the active profile to its defaults. */
    public void resetActive() {
        for (Module m : modules.all()) m.resetToDefaults();
        saveActive();
    }

    // ---------------------------------------------------------------- serialisation

    private JsonObject capture() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        JsonObject mods = new JsonObject();
        for (Module module : modules.all()) {
            JsonObject m = new JsonObject();
            // modules paused by the server rules stay on in the profile
            m.addProperty("enabled", module.isEnabled() || ServerRules.isPaused(module));
            if (module instanceof HudModule hud) {
                m.addProperty("anchor", hud.getAnchor().name());
                m.addProperty("x", hud.getOffsetX());
                m.addProperty("y", hud.getOffsetY());
            }
            JsonObject s = new JsonObject();
            for (Setting<?, ?> setting : module.getSettings()) {
                if (!setting.isDefault()) s.add(setting.getKey(), setting.toJson());
            }
            m.add("settings", s);
            mods.add(module.getId(), m);
        }
        root.add("modules", mods);
        return root;
    }

    private void apply(JsonObject root) {
        JsonObject mods = root != null && root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
        for (Module module : modules.all()) {
            module.resetToDefaults();
            JsonElement element = mods.get(module.getId());
            if (element == null || !element.isJsonObject()) continue;
            JsonObject m = element.getAsJsonObject();
            try {
                if (m.has("settings") && m.get("settings").isJsonObject()) {
                    JsonObject s = m.getAsJsonObject("settings");
                    for (Setting<?, ?> setting : module.getSettings()) setting.fromJson(s.get(setting.getKey()));
                }
                if (module instanceof HudModule hud && m.has("x") && m.has("y")) {
                    HudModule.Anchor anchor = HudModule.Anchor.TOP_LEFT;
                    if (m.has("anchor")) {
                        try {
                            anchor = HudModule.Anchor.valueOf(m.get("anchor").getAsString());
                        } catch (IllegalArgumentException ignored) {
                            // unknown anchor: keep top left
                        }
                    }
                    hud.setStoredPosition(anchor, m.get("x").getAsInt(), m.get("y").getAsInt());
                }
                if (m.has("enabled")) module.setEnabled(m.get("enabled").getAsBoolean());
            } catch (Exception e) {
                OnixClient.LOGGER.warn("Could not load settings of {}", module.getId(), e);
            }
        }
        ServerRules.profileChanged();
        KeybindProfilesModule keys = modules.get(KeybindProfilesModule.class);
        if (keys != null) keys.onOnixProfile(active());
    }
}
