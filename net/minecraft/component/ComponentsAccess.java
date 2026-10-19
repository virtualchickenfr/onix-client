package net.minecraft.component;

import org.jetbrains.annotations.Nullable;

public interface ComponentsAccess {
	@Nullable
	<T> T get(ComponentType<? extends T> type);

	default <T> T getOrDefault(ComponentType<? extends T> type, T fallback) {
		T object = this.get(type);
		return object != null ? object : fallback;
	}

	@Nullable
	default <T> Component<T> getTyped(ComponentType<T> type) {
		T object = this.get(type);
		return object != null ? new Component<>(type, object) : null;
	}
}
