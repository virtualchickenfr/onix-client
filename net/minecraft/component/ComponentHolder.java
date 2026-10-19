package net.minecraft.component;

import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;

/**
 * An object that holds components. Note that this interface does not expose
 * methods to modify the held components.
 * 
 * <p>Component holders usually have "base" components and the overrides to the base
 * (usually referred to as "changes"). The overrides may set additional components,
 * modify the values from the base-provided default, or "unset"/remove base values.
 * Methods in this interface expose the final value, after applying the changes.
 * 
 * @see ComponentMap
 * @see ComponentChanges
 */
public interface ComponentHolder extends ComponentsAccess {
	ComponentMap getComponents();

	@Nullable
	@Override
	default <T> T get(ComponentType<? extends T> type) {
		return this.getComponents().get(type);
	}

	default <T> Stream<T> streamAll(Class<? extends T> valueClass) {
		return this.getComponents().stream().map(Component::value).filter(value -> valueClass.isAssignableFrom(value.getClass())).map(value -> value);
	}

	@Override
	default <T> T getOrDefault(ComponentType<? extends T> type, T fallback) {
		return this.getComponents().getOrDefault(type, fallback);
	}

	/**
	 * {@return whether the held components include {@code type}}
	 * 
	 * @implNote This is implemented as {@code get(type) != null}.
	 */
	default boolean contains(ComponentType<?> type) {
		return this.getComponents().contains(type);
	}
}
