package dev.onix.client.setting;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A single configurable value of a module or of the client.
 *
 * <p>Uses a self type so fluent calls keep the concrete type:
 * {@code new BoolSetting("shadow", "Text Shadow", true).describe("...").visibleIf(...)}.
 */
public abstract class Setting<T, S extends Setting<T, S>> {
    private final String key;
    private final String name;
    private String description = "";
    private String group = "General";
    private BooleanSupplier visibility = () -> true;
    private boolean hidden;
    private final List<Runnable> listeners = new ArrayList<>();

    protected T defaultValue;
    protected T value;

    protected Setting(String key, String name, T defaultValue) {
        this.key = key;
        this.name = name;
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    @SuppressWarnings("unchecked")
    protected final S self() {
        return (S) this;
    }

    // ---------------------------------------------------------------- fluent configuration

    public S describe(String text) {
        this.description = text;
        return self();
    }

    public S group(String group) {
        this.group = group;
        return self();
    }

    /** Only shown in the menu while the condition holds (e.g. "Background Color" only with a background). */
    public S visibleIf(BooleanSupplier condition) {
        this.visibility = condition;
        return self();
    }

    /** Saved with the profile but never shown in the menu (internal data). */
    public S hidden() {
        this.hidden = true;
        return self();
    }

    /** Changes the default (and current) value, e.g. when a module wants a different default than its base class. */
    public S withDefault(T newDefault) {
        this.defaultValue = newDefault;
        this.value = newDefault;
        return self();
    }

    public S onChange(Runnable listener) {
        listeners.add(listener);
        return self();
    }

    // ---------------------------------------------------------------- value

    public T get() {
        return value;
    }

    public void set(T newValue) {
        T sanitized = sanitize(newValue);
        if (sanitized == null || sanitized.equals(value)) return;
        value = sanitized;
        for (Runnable r : listeners) r.run();
    }

    /** Clamp or validate a value before it is stored. Return null to reject it. */
    protected T sanitize(T candidate) {
        return candidate;
    }

    public void reset() {
        set(defaultValue);
    }

    public boolean isDefault() {
        return value.equals(defaultValue);
    }

    // ---------------------------------------------------------------- metadata

    public String getKey() { return key; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getGroup() { return group; }
    public boolean isHidden() { return hidden; }
    public boolean isVisible() { return !hidden && visibility.getAsBoolean(); }

    // ---------------------------------------------------------------- persistence

    public abstract JsonElement toJson();

    /** Reads a stored value; unknown or broken data is ignored and the current value stays. */
    public abstract void fromJson(JsonElement json);
}
