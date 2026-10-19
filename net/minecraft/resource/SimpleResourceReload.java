package net.minecraft.resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.util.Unit;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

/**
 * A simple implementation of resource reload.
 * 
 * @param <S> the result type for each reloader in the reload
 */
public class SimpleResourceReload<S> implements ResourceReload {
	/**
	 * The weight of either prepare or apply stages' progress in the total progress
	 * calculation. Has value {@value}.
	 */
	private static final int FIRST_PREPARE_APPLY_WEIGHT = 2;
	/**
	 * The weight of either prepare or apply stages' progress in the total progress
	 * calculation. Has value {@value}.
	 */
	private static final int SECOND_PREPARE_APPLY_WEIGHT = 2;
	/**
	 * The weight of reloaders' progress in the total progress calculation. Has value {@value}.
	 */
	private static final int RELOADER_WEIGHT = 1;
	final CompletableFuture<Unit> prepareStageFuture = new CompletableFuture();
	@Nullable
	private CompletableFuture<List<S>> applyStageFuture;
	final Set<ResourceReloader> waitingReloaders;
	private final int reloaderCount;
	private final AtomicInteger toPrepareCount = new AtomicInteger();
	private final AtomicInteger preparedCount = new AtomicInteger();
	private final AtomicInteger toApplyCount = new AtomicInteger();
	private final AtomicInteger appliedCount = new AtomicInteger();

	/**
	 * Creates a simple resource reload without additional results.
	 */
	public static ResourceReload create(
		ResourceManager manager, List<ResourceReloader> reloaders, Executor prepareExecutor, Executor applyExecutor, CompletableFuture<Unit> initialStage
	) {
		SimpleResourceReload<Void> simpleResourceReload = new SimpleResourceReload<>(reloaders);
		simpleResourceReload.start(prepareExecutor, applyExecutor, manager, reloaders, SimpleResourceReload.Factory.SIMPLE, initialStage);
		return simpleResourceReload;
	}

	protected SimpleResourceReload(List<ResourceReloader> waitingReloaders) {
		this.reloaderCount = waitingReloaders.size();
		this.waitingReloaders = new HashSet(waitingReloaders);
	}

	protected void start(
		Executor prepareExecutor,
		Executor applyExecutor,
		ResourceManager manager,
		List<ResourceReloader> reloaders,
		SimpleResourceReload.Factory<S> factory,
		CompletableFuture<?> initialStage
	) {
		this.applyStageFuture = this.startAsync(prepareExecutor, applyExecutor, manager, reloaders, factory, initialStage);
	}

	protected CompletableFuture<List<S>> startAsync(
		Executor prepareExecutor,
		Executor applyExecutor,
		ResourceManager manager,
		List<ResourceReloader> reloaders,
		SimpleResourceReload.Factory<S> factory,
		CompletableFuture<?> initialStage
	) {
		Executor executor = runnable -> {
			this.toPrepareCount.incrementAndGet();
			prepareExecutor.execute(() -> {
				runnable.run();
				this.preparedCount.incrementAndGet();
			});
		};
		Executor executor2 = runnable -> {
			this.toApplyCount.incrementAndGet();
			applyExecutor.execute(() -> {
				runnable.run();
				this.appliedCount.incrementAndGet();
			});
		};
		this.toPrepareCount.incrementAndGet();
		initialStage.thenRun(this.preparedCount::incrementAndGet);
		CompletableFuture<?> completableFuture = initialStage;
		List<CompletableFuture<S>> list = new ArrayList();

		for (ResourceReloader resourceReloader : reloaders) {
			ResourceReloader.Synchronizer synchronizer = this.createSynchronizer(resourceReloader, completableFuture, applyExecutor);
			CompletableFuture<S> completableFuture2 = factory.create(synchronizer, manager, resourceReloader, executor, executor2);
			list.add(completableFuture2);
			completableFuture = completableFuture2;
		}

		return Util.combine(list);
	}

	private ResourceReloader.Synchronizer createSynchronizer(ResourceReloader reloader, CompletableFuture<?> future, Executor applyExecutor) {
		return new ResourceReloader.Synchronizer() {
			@Override
			public <T> CompletableFuture<T> whenPrepared(T preparedObject) {
				applyExecutor.execute(() -> {
					SimpleResourceReload.this.waitingReloaders.remove(reloader);
					if (SimpleResourceReload.this.waitingReloaders.isEmpty()) {
						SimpleResourceReload.this.prepareStageFuture.complete(Unit.INSTANCE);
					}
				});
				return SimpleResourceReload.this.prepareStageFuture.thenCombine(future, (unit, object2) -> preparedObject);
			}
		};
	}

	@Override
	public CompletableFuture<?> whenComplete() {
		return (CompletableFuture<?>)Objects.requireNonNull(this.applyStageFuture, "not started");
	}

	@Override
	public float getProgress() {
		int i = this.reloaderCount - this.waitingReloaders.size();
		float f = toWeighted(this.preparedCount.get(), this.appliedCount.get(), i);
		float g = toWeighted(this.toPrepareCount.get(), this.toApplyCount.get(), this.reloaderCount);
		return f / g;
	}

	private static int toWeighted(int prepare, int apply, int total) {
		return prepare * 2 + apply * 2 + total * 1;
	}

	/**
	 * Starts a resource reload with the content from the {@code manager} supplied
	 * to the {@code reloaders}.
	 * 
	 * @apiNote In vanilla, this is respectively called by {@link ReloadableResourceManagerImpl}
	 * on the client and {@link net.minecraft.server.DataPackContents} on the server.
	 * 
	 * @param reloaders the reloaders performing the reload
	 * @param manager the resource manager, providing resources to the reloaders
	 * @param applyExecutor the executor for the apply stage, synchronous with the game engine
	 * @param prepareExecutor the executor for the prepare stage, often asynchronous
	 * @param profiled whether to profile this reload and log the statistics
	 * @param initialStage the initial stage, must be completed before the reloaders can prepare resources
	 */
	public static ResourceReload start(
		ResourceManager manager,
		List<ResourceReloader> reloaders,
		Executor prepareExecutor,
		Executor applyExecutor,
		CompletableFuture<Unit> initialStage,
		boolean profiled
	) {
		return profiled
			? ProfiledResourceReload.start(manager, reloaders, prepareExecutor, applyExecutor, initialStage)
			: create(manager, reloaders, prepareExecutor, applyExecutor, initialStage);
	}

	/**
	 * A factory that creates a completable future for each reloader in the
	 * resource reload.
	 */
	@FunctionalInterface
	protected interface Factory<S> {
		SimpleResourceReload.Factory<Void> SIMPLE = (synchronizer, manager, reloader, prepareExecutor, applyExecutor) -> reloader.reload(
			synchronizer, manager, prepareExecutor, applyExecutor
		);

		CompletableFuture<S> create(
			ResourceReloader.Synchronizer synchronizer, ResourceManager manager, ResourceReloader reloader, Executor prepareExecutor, Executor applyExecutor
		);
	}
}
