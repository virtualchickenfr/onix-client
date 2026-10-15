package dev.onix.client.module;

import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.Setting;
import net.minecraft.client.MinecraftClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class of every module. Subclasses declare their settings as fields using
 * {@link #add}; settings are grouped by the last {@link #group} call.
 */
public abstract class Module {
    public static final String GROUP_GENERAL = "General";
    public static final String GROUP_APPEARANCE = "Appearance";
    public static final String GROUP_CONTROLS = "Controls";

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final boolean enabledByDefault;
    private final List<Setting<?, ?>> settings = new ArrayList<>();
    private final List<String> tags = new ArrayList<>();
    private String currentGroup = GROUP_GENERAL;
    private boolean enabled;
    private String restriction;

    public final KeybindSetting toggleKey;
    public final BoolSetting toggleNotify;

    protected Module(String id, String name, String description, Category category, boolean enabledByDefault) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabledByDefault = enabledByDefault;
        this.enabled = enabledByDefault;

        group(GROUP_CONTROLS);
        toggleKey = add(new KeybindSetting("toggleKey", "Toggle Key", KeybindSetting.NONE)
                .describe("Press this key in-game to turn the module on or off."));
        toggleNotify = add(new BoolSetting("toggleNotify", "Notify on Toggle", true)
                .describe("Show a notification when the toggle key is used."));
        group(GROUP_GENERAL);
    }

    protected static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    // ---------------------------------------------------------------- settings

    /** Starts a new settings section; following {@link #add} calls land in it. */
    protected final void group(String name) {
        currentGroup = name;
    }

    protected final <S extends Setting<?, ?>> S add(S setting) {
        if (GROUP_GENERAL.equals(setting.getGroup())) setting.group(currentGroup);
        settings.add(setting);
        return setting;
    }

    /**
     * Marks the module as one that some servers or tournaments do not allow. The reason is shown as a
     * note on the module, and {@link dev.onix.client.util.ServerRules} can remind you or turn it off on join.
     */
    protected final void restricted(String reason) {
        this.restriction = reason;
        tags("server rules", "restricted", "not allowed");
    }

        /** Extra search words, e.g. "fps counter" for the FPS module. */
    protected final void tags(String... words) {
        Collections.addAll(tags, words);
    }

    public List<Setting<?, ?>> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    /** Settings the user can see (not hidden), used for counts in the menu. */
    public int visibleSettingCount() {
        int n = 0;
        for (Setting<?, ?> s : settings) if (!s.isHidden()) n++;
        return n;
    }

    public List<String> getTags() {
        return Collections.unmodifiableList(tags);
    }

    // ---------------------------------------------------------------- state

    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        if (value) onEnable(); else onDisable();
    }

    public void resetToDefaults() {
        for (Setting<?, ?> s : settings) s.reset();
        setEnabled(enabledByDefault);
    }

    // ---------------------------------------------------------------- lifecycle hooks

    /** Called once after all modules are registered. Register Fabric events here (check {@link #isEnabled()} inside). */
    public void init() {}

    protected void onEnable() {}

    protected void onDisable() {}

    /** Every client tick while enabled. */
    public void onTick() {}

    /** When the game closes, regardless of state. */
    public void onShutdown() {}

    // ---------------------------------------------------------------- metadata

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public boolean isEnabledByDefault() { return enabledByDefault; }

    /** Why some servers may not allow this module, or null if it is fine everywhere. */
    public String restriction() { return restriction; }
    public boolean isRestricted() { return restriction != null; }

    /** Optional live status shown on the module's settings page (e.g. a connection state). */
    public String statusLine() { return null; }
}
