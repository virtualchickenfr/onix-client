package dev.onix.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.onix.client.gui.Theme;
import dev.onix.client.util.ColorUtil;

/**
 * An ARGB color with optional chroma (rainbow) and an option to follow the
 * theme accent. Value is a packed ARGB int.
 */
public final class ColorSetting extends Setting<Integer, ColorSetting> {
    /** Quick picks shown in the color picker. */
    public static final int[] PRESETS = {
            0xFFFFFFFF, 0xFFA1A1AA, 0xFF3F3F46, 0xFF000000,
            0xFFFF5A5F, 0xFFFB923C, 0xFFFACC15, 0xFF34D399,
            0xFF2DD4BF, 0xFF38BDF8, 0xFF6366F1, 0xFFA78BFA,
            0xFFF472B6, 0xFFFF8FC4, 0xFF8B7CFF, 0xFF5EEAD4,
    };

    private final boolean defaultAccent;
    private final boolean defaultChroma;
    private boolean accent;
    private boolean chroma;
    private boolean plain;

    public ColorSetting(String key, String name, int argb) {
        this(key, name, argb, false);
    }

    private ColorSetting(String key, String name, int argb, boolean accent) {
        super(key, name, argb);
        this.defaultAccent = accent;
        this.accent = accent;
        this.defaultChroma = false;
    }

    /** A color that follows the theme accent until the user picks something else. */
    public static ColorSetting accent(String key, String name) {
        return new ColorSetting(key, name, 0xFFFFFFFF, true);
    }

    /** Hides "Theme Accent" and "Chroma" in the picker (e.g. for the colors of a theme itself). */
    public ColorSetting plain() {
        this.plain = true;
        return this;
    }

    public boolean isPlain() {
        return plain;
    }

    /** The color to draw with right now (accent, chroma and alpha applied). */
    public int color() {
        return color(0);
    }

    /** Like {@link #color()} but with a hue offset, so text can run a rainbow across lines. */
    public int color(float hueOffset) {
        int alpha = (value >>> 24) & 0xFF;
        if (chroma) {
            float hue = (float) ((System.currentTimeMillis() % 4000L) / 4000.0) + hueOffset;
            int rgb = ColorUtil.hsvToRgb(hue, 0.65f, 1f);
            return (alpha << 24) | (rgb & 0x00FFFFFF);
        }
        if (accent) return (alpha << 24) | (Theme.themeAccent() & 0x00FFFFFF);
        return value;
    }

    public int alpha() {
        return (value >>> 24) & 0xFF;
    }

    public boolean isAccent() { return accent; }
    public boolean isChroma() { return chroma; }

    public void setAccent(boolean on) {
        accent = on;
        if (on) chroma = false;
    }

    public void setChroma(boolean on) {
        chroma = on;
        if (on) accent = false;
    }

    /** Picking a concrete color turns accent and chroma off. */
    public void pick(int argb) {
        accent = false;
        chroma = false;
        set(argb);
    }

    @Override
    public void reset() {
        super.reset();
        accent = defaultAccent;
        chroma = defaultChroma;
    }

    @Override
    public boolean isDefault() {
        return super.isDefault() && accent == defaultAccent && chroma == defaultChroma;
    }

    public String hex() {
        return String.format("#%08X", value);
    }

    @Override
    public JsonElement toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("argb", value);
        o.addProperty("accent", accent);
        o.addProperty("chroma", chroma);
        return o;
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonObject()) return;
        JsonObject o = json.getAsJsonObject();
        if (o.has("argb")) set(o.get("argb").getAsInt());
        if (o.has("accent")) accent = o.get("accent").getAsBoolean();
        if (o.has("chroma")) chroma = o.get("chroma").getAsBoolean();
    }
}
