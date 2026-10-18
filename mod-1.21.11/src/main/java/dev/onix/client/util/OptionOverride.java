package dev.onix.client.util;

import java.util.Objects;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;

/**
 * Temporarily overrides a vanilla option and remembers the player's own value, so a module
 * can change e.g. the FOV while enabled and put everything back exactly when disabled.
 */
public final class OptionOverride<T> {
    private final Function<Options, OptionInstance<T>> getter;
    private T previous;
    private boolean active;

    public OptionOverride(Function<Options, OptionInstance<T>> getter) {
        this.getter = getter;
    }

    private OptionInstance<T> option() {
        Options options = Minecraft.getInstance().options;
        return options == null ? null : getter.apply(options);
    }

    /** Sets the option, saving the original value on first use. */
    public void apply(T value) {
        OptionInstance<T> option = option();
        if (option == null || value == null) return;
        if (!active) {
            previous = option.get();
            active = true;
        }
        if (!Objects.equals(option.get(), value)) option.set(value);
    }

    /** The player's own value (the current one while not overridden). */
    public T original() {
        if (active) return previous;
        OptionInstance<T> option = option();
        return option == null ? null : option.get();
    }

    public boolean isActive() {
        return active;
    }

    /** Puts the player's own value back. Safe to call any time. */
    public void restore() {
        if (!active) return;
        OptionInstance<T> option = option();
        if (option != null && previous != null) option.set(previous);
        previous = null;
        active = false;
    }
}
