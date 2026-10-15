package dev.onix.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

import java.util.Objects;
import java.util.function.Function;

/**
 * Temporarily overrides a vanilla option and remembers the player's own value, so a module
 * can change e.g. the FOV while enabled and put everything back exactly when disabled.
 */
public final class OptionOverride<T> {
    private final Function<GameOptions, SimpleOption<T>> getter;
    private T previous;
    private boolean active;

    public OptionOverride(Function<GameOptions, SimpleOption<T>> getter) {
        this.getter = getter;
    }

    private SimpleOption<T> option() {
        GameOptions options = MinecraftClient.getInstance().options;
        return options == null ? null : getter.apply(options);
    }

    /** Sets the option, saving the original value on first use. */
    public void apply(T value) {
        SimpleOption<T> option = option();
        if (option == null || value == null) return;
        if (!active) {
            previous = option.getValue();
            active = true;
        }
        if (!Objects.equals(option.getValue(), value)) option.setValue(value);
    }

    /** The player's own value (the current one while not overridden). */
    public T original() {
        if (active) return previous;
        SimpleOption<T> option = option();
        return option == null ? null : option.getValue();
    }

    public boolean isActive() {
        return active;
    }

    /** Puts the player's own value back. Safe to call any time. */
    public void restore() {
        if (!active) return;
        SimpleOption<T> option = option();
        if (option != null && previous != null) option.setValue(previous);
        previous = null;
        active = false;
    }
}
