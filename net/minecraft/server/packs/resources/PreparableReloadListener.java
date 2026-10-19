package net.minecraft.server.packs.resources;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@FunctionalInterface
public interface PreparableReloadListener {
	CompletableFuture<Void> reload(
		PreparableReloadListener.PreparationBarrier preparationBarrier, ResourceManager resourceManager, Executor executor, Executor executor2
	);

	default String getName() {
		return this.getClass().getSimpleName();
	}

	@FunctionalInterface
	interface PreparationBarrier {
		<T> CompletableFuture<T> wait(T object);
	}
}
