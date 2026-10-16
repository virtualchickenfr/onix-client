package dev.onix.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.function.Supplier;

/**
 * One choice out of a list, shown as a segmented control or a dropdown. The list is usually
 * fixed; {@link #dynamic} creates one whose choices can change (e.g. skies found in a folder).
 */
public final class ModeSetting extends Setting<String, ModeSetting> {
    private final String[] modes;
    private final Supplier<String[]> source;

    public ModeSetting(String key, String name, String... modes) {
        super(key, name, modes[0]);
        this.modes = modes;
        this.source = null;
    }

    private ModeSetting(String key, String name, String defaultValue, Supplier<String[]> source) {
        super(key, name, defaultValue);
        this.modes = null;
        this.source = source;
    }

    /** Choices are read from the supplier each time; unknown saved values are kept until they show up. */
    public static ModeSetting dynamic(String key, String name, String defaultValue, Supplier<String[]> source) {
        return new ModeSetting(key, name, defaultValue, source);
    }

    public String[] modes() {
        return source != null ? source.get() : modes.clone();
    }

    public int index() {
        String[] all = modes();
        for (int i = 0; i < all.length; i++) if (all[i].equals(value)) return i;
        return 0;
    }

    public boolean is(String mode) {
        return value.equals(mode);
    }

    public void cycle(int direction) {
        String[] all = modes();
        if (all.length > 0) set(all[Math.floorMod(index() + direction, all.length)]);
    }

    @Override
    protected String sanitize(String candidate) {
        if (candidate == null || candidate.isEmpty()) return null;
        if (source != null) return candidate;
        for (String m : modes) if (m.equals(candidate)) return m;
        return null;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) set(json.getAsString());
    }
}
