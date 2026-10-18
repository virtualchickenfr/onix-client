package dev.onix.client.gui;

import dev.onix.client.config.ThemeStore;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.Setting;
import dev.onix.client.setting.TextSetting;

import java.util.List;

/**
 * Edits one custom theme with the regular setting controls. Changes apply live and are
 * written to the theme file shortly after the last change.
 */
public final class ThemeEditor {
    /** The edited theme including unsaved changes, so every screen shows them right away. */
    private static ThemeDef preview;

    private ThemeDef theme;
    private List<Setting<?, ?>> settings = List.of();
    private boolean dirty;
    private long changedAt;

    /** Starts editing a theme (no-op if it is already the edited one). */
    public void edit(ThemeDef def) {
        if (def == null || !def.custom()) {
            theme = null;
            settings = List.of();
            return;
        }
        if (theme != null && theme.id().equals(def.id())) return;
        flush();
        theme = def;
        TextSetting name = new TextSetting("name", "Name", def.name(), 24).placeholder("Theme name");
        ColorSetting accent = new ColorSetting("accent", "Accent", def.accent()).plain()
                .describe("Buttons, switches, highlights and accent-colored HUD elements.");
        ColorSetting panel = new ColorSetting("panel", "Background", def.panel()).plain()
                .describe("Base color of menus and windows. Dark colors work best.");
        ColorSetting sky = new ColorSetting("sky", "Sky Tint", def.sky()).plain()
                .describe("Tints the title screen and, with Atmosphere, the sky.");
        ColorSetting particle = new ColorSetting("particle", "Particle Color", def.particle()).plain();
        ModeSetting ambient = new ModeSetting("ambient", "Particle Style", ThemeDef.Ambient.names());
        ambient.set(def.ambient().displayName);
        Runnable apply = () -> {
            if (theme == null) return;
            String n = name.get().isBlank() ? theme.name() : name.get().trim();
            theme = theme.withName(n)
                    .withColors(0xFF000000 | accent.get(), 0xFF000000 | panel.get(), 0xFF000000 | sky.get(), 0xFF000000 | particle.get())
                    .withAmbient(ThemeDef.Ambient.byName(ambient.get()));
            dirty = true;
            changedAt = System.currentTimeMillis();
            preview = theme;
        };
        for (Setting<?, ?> s : List.<Setting<?, ?>>of(name, accent, panel, sky, particle, ambient)) s.onChange(apply);
        settings = List.of(name, accent, panel, sky, particle, ambient);
    }

    public static ThemeDef livePreview(String id) {
        return preview != null && preview.id().equals(id) ? preview : null;
    }

    public ThemeDef theme() {
        return theme;
    }

    public List<Setting<?, ?>> settings() {
        return settings;
    }

    /** Saves at most twice a second while sliders are dragged. */
    public void tick() {
        if (dirty && System.currentTimeMillis() - changedAt > 400) flush();
    }

    public void flush() {
        if (dirty && theme != null) {
            ThemeStore.save(theme);
            dirty = false;
        }
    }
}
