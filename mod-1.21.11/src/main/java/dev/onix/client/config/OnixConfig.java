package dev.onix.client.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.onix.client.gui.ThemeDef;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.setting.Setting;
import dev.onix.client.setting.TextSetting;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Client-wide settings that are the same for every profile. Stored in config/onix/client.json. */
public class OnixConfig {
    // Theme: id of a built-in theme ("onyx") or a custom one ("custom-1a2b3c"), see ThemeStore
    public final TextSetting theme = new TextSetting("theme", "Theme", "onyx", 64).hidden();

    // Title screen background, see TitleBackgrounds
    public final TextSetting titleBackground = new TextSetting("titleBackground", "Background", "carbon", 128).hidden();
    public final BoolSetting backgroundSlideshow = new BoolSetting("backgroundSlideshow", "Slideshow", false)
            .describe("Cycles through every background with a soft crossfade.");
    public final NumberSetting slideshowSeconds = new NumberSetting("slideshowSeconds", "Show Each For", 12, 4, 60, 1).unit("s")
            .visibleIf(backgroundSlideshow::isOn);
    public final BoolSetting backgroundMotion = new BoolSetting("backgroundMotion", "Background Motion", true)
            .describe("Slow drift and a little parallax when you move the mouse.");

    // Interface
    public final ModeSetting menuStyle = new ModeSetting("menuStyle", "Menu Style", "Classic", "Bloom")
            .describe("Classic opens straight into the module list. Bloom starts with a flower of categories.");
    public final ModeSetting menuScale = new ModeSetting("menuScale", "Menu Size", "Normal", "Small", "Large");
    public final BoolSetting menuAnimations = new BoolSetting("menuAnimations", "Animations", true);
    public final BoolSetting clickSounds = new BoolSetting("clickSounds", "Click Sounds", true);
    public final BoolSetting menuParticles = new BoolSetting("menuParticles", "Menu Particles", false);
    public final BoolSetting backgroundDim = new BoolSetting("backgroundDim", "Dim Background", true);
    public final BoolSetting tooltips = new BoolSetting("tooltips", "Tooltips", true);
    public final BoolSetting socialOn = new BoolSetting("socialOn", "Friends & Chat", true)
            .describe("Friend list, chat with friends and McReal through the Onix server.");
    public final BoolSetting shareServer = new BoolSetting("shareServer", "Show Friends My Server", true)
            .describe("Friends see which server you play on and can join you with one click.");
    public final BoolSetting nameTagLooks = new BoolSetting("nameTagLooks", "Name Styles of Others", true)
            .describe("Shows the name styles and tags other Onix players bought, above their heads and in the tab list.");
    public final BoolSetting customMainMenu = new BoolSetting("customMainMenu", "Onix Title Screen", true);

    // Onix server: badges, coins, cosmetics shop and gifts (see /server in the repository)
    public final TextSetting onixServer = new TextSetting("onixServer", "Onix Server", "", 200)
            .placeholder("https://onix.example.com")
            .describe("Shows the Onix badge on other players and runs your coins, the cosmetics shop and gifts. "
                    + "Empty = the server built into this Onix version.");


    // Design
    public final BoolSetting lightMode = new BoolSetting("lightMode", "Light Mode", false)
            .describe("Light surfaces and dark text in every Onix menu.");
    public final ModeSetting buttonStyle = new ModeSetting("buttonShape", "Button Style", "Block", "Rounded", "Square", "Pill")
            .describe("Block: Carbon's stone buttons with depth, a lift on hover and a light sweep.");
    public final BoolSetting carbonButtons = new BoolSetting("carbonButtons", "Carbon Buttons Everywhere", true)
            .describe("Minecraft's own buttons and sliders (options, singleplayer, multiplayer …) as Carbon blocks.");
    public final BoolSetting menuSkin = new BoolSetting("menuSkin", "Carbon Menu Skin", true)
            .describe("Minecraft's menus outside a world get Carbon's backdrop, lists, text fields and tooltips.");
    public final ModeSetting menuDesign = new ModeSetting("menuDesign", "Main Menu", "Onix", "Carbon", "Voxel", "Portal", "Classic", "Vanilla")
            .describe("Onix's title screen or one of the Carbon designs (Carbon, Voxel, Portal, Classic).");
    public final BoolSetting menuCharacter = new BoolSetting("menuCharacter", "Character Art", false)
            .describe("Carbon design: show the character artwork next to the menu.")
            .visibleIf(() -> menuDesign.is("Carbon"));
    public final ModeSetting titleDesign = new ModeSetting("titleDesign", "Title Screen Design", "Onix", "Centered", "Split", "Minimal", "Cards")
            .describe("Also switch on the title screen with the arrow keys, by dragging or with the dots at the bottom.");

    // Loading screens
    public final BoolSetting loadingScreens = new BoolSetting("loadingScreens", "Onix Loading Screens", true)
            .describe("Replaces resource loading, world loading, connecting and saving screens.");
    public final ModeSetting loadingStyle = new ModeSetting("loadingStyle", "Animation", "Gem", "Orbit", "Minimal");
    public final BoolSetting loadingTips = new BoolSetting("loadingTips", "Show Tips", true);

    // HUD
    public final BoolSetting hideHudInDebug = new BoolSetting("hideHudInDebug", "Hide HUD on F3 Screen", true);
    public final BoolSetting snapping = new BoolSetting("snapping", "Snap in HUD Editor", true)
            .describe("Elements snap to screen edges, the center and each other.");
    public final NumberSetting snapDistance = new NumberSetting("snapDistance", "Snap Distance", 4, 1, 12, 1).unit("px");
    public final BoolSetting editorGrid = new BoolSetting("editorGrid", "Grid in HUD Editor", true);

    // Notifications
    public final BoolSetting notifications = new BoolSetting("notifications", "Notifications", true);
    public final BoolSetting notificationSound = new BoolSetting("notificationSound", "Notification Sound", true);
    public final NumberSetting notificationTime = new NumberSetting("notificationTime", "Show For", 2.8, 1, 8, 0.2).unit("s");
    public final ModeSetting notificationPosition = new ModeSetting("notificationPosition", "Position",
            "Top Right", "Bottom Right", "Top Left", "Bottom Left");

    // Server rules
    public final BoolSetting ruleNotes = new BoolSetting("ruleNotes", "Show Server-Rule Notes", true)
            .describe("Marks modules that some servers or tournaments do not allow.");
    public final ModeSetting rulesOnJoin = new ModeSetting("rulesOnJoin", "When Joining a Server", "Remind Me", "Turn Off", "Do Nothing")
            .describe("Turn Off switches marked modules off while you play there and back on when you leave.");
    public final TextSetting rulesSkip = new TextSetting("rulesSkip", "Skip These Servers", "", 300)
            .placeholder("e.g. myserver.net, localhost")
            .describe("Comma-separated addresses where marked modules are fine (your own server, for example).");

    // Profiles
    public final BoolSetting autoSwitchProfiles = new BoolSetting("autoSwitchProfiles", "Load Profile per Server", true)
            .describe("Profiles linked to a server load automatically when you join it.");

    public String activeProfile = "Default";
    /** Profile to return to when CWT mode is switched off; empty while CWT mode is off. */
    public String cwtReturnProfile = "";
    /** Starred modules, shown under Favorites. */
    public final Set<String> favorites = new LinkedHashSet<>();
    /** Lower-case server address → profile name. */
    public final Map<String, String> serverProfiles = new LinkedHashMap<>();

    public List<Setting<?, ?>> backgroundSettings() {
        return List.of(backgroundSlideshow, slideshowSeconds, backgroundMotion);
    }

    public List<Setting<?, ?>> designSettings() {
        return List.of(menuDesign, menuCharacter, menuStyle, lightMode, buttonStyle, carbonButtons, menuSkin, menuScale, titleDesign, menuAnimations, menuParticles, backgroundDim);
    }

    public List<Setting<?, ?>> loadingSettings() {
        return List.of(loadingScreens, loadingStyle, loadingTips);
    }

    public List<Setting<?, ?>> interfaceSettings() {
        return List.of(customMainMenu, clickSounds, tooltips);
    }

    public List<Setting<?, ?>> hudSettings() {
        return List.of(hideHudInDebug, snapping, snapDistance, editorGrid);
    }

    public List<Setting<?, ?>> notificationSettings() {
        return List.of(notifications, notificationSound, notificationTime, notificationPosition);
    }

    public List<Setting<?, ?>> networkSettings() {
        return List.of(onixServer, socialOn, shareServer, nameTagLooks);
    }

    public List<Setting<?, ?>> serverRuleSettings() {
        return List.of(ruleNotes, rulesOnJoin, rulesSkip);
    }

    public List<Setting<?, ?>> profileSettings() {
        return List.of(autoSwitchProfiles);
    }

    private List<Setting<?, ?>> all() {
        return List.of(theme, titleBackground, backgroundSlideshow, slideshowSeconds, backgroundMotion,
                menuStyle, menuScale, menuAnimations, clickSounds, menuParticles, backgroundDim, tooltips, customMainMenu,
                onixServer, socialOn, shareServer, nameTagLooks, lightMode, buttonStyle, carbonButtons, menuSkin, menuDesign, menuCharacter, titleDesign, loadingScreens, loadingStyle, loadingTips,
                hideHudInDebug, snapping, snapDistance, editorGrid,
                notifications, notificationSound, notificationTime, notificationPosition,
                ruleNotes, rulesOnJoin, rulesSkip, autoSwitchProfiles);
    }

    public float menuScaleFactor() {
        return switch (menuScale.get()) {
            case "Large" -> 1.15f;
            case "Small" -> 0.85f;
            default -> 1f;
        };
    }

    public boolean toggleFavorite(String moduleId) {
        boolean added = favorites.add(moduleId);
        if (!added) favorites.remove(moduleId);
        save();
        return added;
    }

    private static Path file() {
        return ConfigFiles.dir().resolve("client.json");
    }

    public static OnixConfig load() {
        OnixConfig config = new OnixConfig();
        JsonObject json = ConfigFiles.read(file());
        if (json == null) return config;
        if (json.has("activeProfile")) {
            // the name becomes a file name: never trust it blindly (client.json can come from an imported pack)
            String clean = ProfileManager.sanitize(json.get("activeProfile").getAsString());
            config.activeProfile = clean.isEmpty() ? ProfileManager.DEFAULT : clean;
        }
        if (json.has("cwtReturnProfile")) config.cwtReturnProfile = json.get("cwtReturnProfile").getAsString();
        JsonObject s = json.has("settings") && json.get("settings").isJsonObject() ? json.getAsJsonObject("settings") : new JsonObject();
        for (Setting<?, ?> setting : config.all()) setting.fromJson(s.get(setting.getKey()));
        migrateTheme(config, s);
        if (json.has("favorites") && json.get("favorites").isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray("favorites")) config.favorites.add(e.getAsString());
        }
        if (json.has("serverProfiles") && json.get("serverProfiles").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("serverProfiles").entrySet()) {
                config.serverProfiles.put(e.getKey(), e.getValue().getAsString());
            }
        }
        return config;
    }

    /** Old versions stored theme names and one "Custom" theme as RGB numbers. */
    private static void migrateTheme(OnixConfig config, JsonObject s) {
        String value = config.theme.get();
        if ("Custom".equalsIgnoreCase(value)) {
            int accent = rgb(s, "customR", "customG", "customB", 0xFF8B7CFF);
            int panel = rgb(s, "panelR", "panelG", "panelB", 0xFF0D0D11);
            ThemeDef.Ambient ambient = ThemeDef.Ambient.byName(s.has("customAmbient") ? s.get("customAmbient").getAsString() : "Sparks");
            ThemeDef base = new ThemeDef("custom", "My Theme", accent, panel, accent, ambient, 0xFFFFFFFF, true);
            config.theme.set(ThemeStore.create(base, "My Theme").id());
        } else {
            config.theme.set(ThemeStore.byId(value).id());
        }
    }

    private static int rgb(JsonObject s, String r, String g, String b, int fallback) {
        try {
            if (!s.has(r) || !s.has(g) || !s.has(b)) return fallback;
            return 0xFF000000 | (s.get(r).getAsInt() << 16) | (s.get(g).getAsInt() << 8) | s.get(b).getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    public void save() {
        JsonObject json = new JsonObject();
        json.addProperty("activeProfile", activeProfile);
        json.addProperty("cwtReturnProfile", cwtReturnProfile);
        JsonObject s = new JsonObject();
        for (Setting<?, ?> setting : all()) s.add(setting.getKey(), setting.toJson());
        json.add("settings", s);
        JsonArray fav = new JsonArray();
        favorites.forEach(fav::add);
        json.add("favorites", fav);
        JsonObject servers = new JsonObject();
        serverProfiles.forEach(servers::addProperty);
        json.add("serverProfiles", servers);
        ConfigFiles.write(file(), json);
    }
}
