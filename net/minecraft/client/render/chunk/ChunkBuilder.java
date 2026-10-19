package net.minecraft.client.render.chunk;

import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.buffers.BufferType;
import com.mojang.blaze3d.buffers.BufferUsage;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Util;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.util.profiler.ScopedProfiler;
import net.minecraft.util.thread.NameableExecutor;
import net.minecraft.util.thread.SimpleConsecutiveExecutor;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class ChunkBuilder {
	private final ChunkRenderTaskScheduler scheduler = new ChunkRenderTaskScheduler();
	final Queue<Runnable> uploadQueue = Queues.<Runnable>newConcurrentLinkedQueue();
	final BlockBufferAllocatorStorage buffers;
	private final BlockBufferBuilderPool buffersPool;
	private volatile int queuedTaskCount;
	volatile boolean stopped;
	private final SimpleConsecutiveExecutor consecutiveExecutor;
	private final NameableExecutor executor;
	ClientWorld world;
	final WorldRenderer worldRenderer;
	private Vec3d cameraPosition = Vec3d.ZERO;
	final SectionBuilder sectionBuilder;

	public ChunkBuilder(
		ClientWorld world,
		WorldRenderer worldRenderer,
		NameableExecutor executor,
		BufferBuilderStorage bufferBuilderStorage,
		BlockRenderManager blockRenderManager,
		BlockEntityRenderDispatcher blockEntityRenderDispatcher
	) {
		this.world = world;
		this.worldRenderer = worldRenderer;
		this.buffers = bufferBuilderStorage.getBlockBufferBuilders();
		this.buffersPool = bufferBuilderStorage.getBlockBufferBuildersPool();
		this.executor = executor;
		this.consecutiveExecutor = new SimpleConsecutiveExecutor(executor, "Section Renderer");
		this.consecutiveExecutor.send(this::scheduleRunTasks);
		this.sectionBuilder = new SectionBuilder(blockRenderManager, blockEntityRenderDispatcher);
	}

	public void setWorld(ClientWorld world) {
		this.world = world;
	}

	private void scheduleRunTasks() {
		if (!this.stopped && !this.buffersPool.hasNoAvailableBuilder()) {
			ChunkBuilder.BuiltChunk.Task task = this.scheduler.dequeueNearest(this.getCameraPosition());
			if (task != null) {
				BlockBufferAllocatorStorage blockBufferAllocatorStorage = (BlockBufferAllocatorStorage)Objects.requireNonNull(this.buffersPool.acquire());
				this.queuedTaskCount = this.scheduler.size();
				CompletableFuture.supplyAsync(() -> task.run(blockBufferAllocatorStorage), this.executor.named(task.getName()))
					.thenCompose(future -> future)
					.whenComplete((result, throwable) -> {
						if (throwable != null) {
							MinecraftClient.getInstance().setCrashReportSupplierAndAddDetails(CrashReport.create(throwable, "Batching sections"));
						} else {
							task.finished.set(true);
							this.consecutiveExecutor.send(() -> {
								if (result == ChunkBuilder.Result.SUCCESSFUL) {
									blockBufferAllocatorStorage.clear();
								} else {
									blockBufferAllocatorStorage.reset();
								}

								this.buffersPool.release(blockBufferAllocatorStorage);
								this.scheduleRunTasks();
							});
						}
					});
			}
		}
	}

	public String getDebugString() {
		return String.format(Locale.ROOT, "pC: %03d, pU: %02d, aB: %02d", this.queuedTaskCount, this.uploadQueue.size(), this.buffersPool.getAvailableBuilderCount());
	}

	public int getToBatchCount() {
		return this.queuedTaskCount;
	}

	public int getChunksToUpload() {
		return this.uploadQueue.size();
	}

	public int getFreeBufferCount() {
		return this.buffersPool.getAvailableBuilderCount();
	}

	public void setCameraPosition(Vec3d cameraPosition) {
		this.cameraPosition = cameraPosition;
	}

	public Vec3d getCameraPosition() {
		return this.cameraPosition;
	}

	public void upload() {
		Runnable runnable;
		while ((runnable = (Runnable)this.uploadQueue.poll()) != null) {
			runnable.run();
		}
	}

	public void rebuild(ChunkBuilder.BuiltChunk chunk, ChunkRendererRegionBuilder builder) {
		chunk.rebuild(builder);
	}

	public void reset() {
		this.clear();
	}

	public void send(ChunkBuilder.BuiltChunk.Task task) {
		if (!this.stopped) {
			this.consecutiveExecutor.send(() -> {
				if (!this.stopped) {
					this.scheduler.enqueue(task);
					this.queuedTaskCount = this.scheduler.size();
					this.scheduleRunTasks();
				}
			});
		}
	}

	private void clear() {
		this.scheduler.cancelAll();
		this.queuedTaskCount = 0;
	}

	public boolean isEmpty() {
		return this.queuedTaskCount == 0 && this.uploadQueue.isEmpty();
	}

	public void stop() {
		this.stopped = true;
		this.clear();
		this.upload();
	}

	@Environment(EnvType.CLIENT)
	public static final class Buffers implements AutoCloseable {
		GpuBuffer vertexBuffer;
		@Nullable
		GpuBuffer indexBuffer;
		private int indexCount;
		private VertexFormat.IndexType indexType;

		public Buffers(GpuBuffer vertexBuffer, @Nullable GpuBuffer indexBuffer, int indexCount, VertexFormat.IndexType indexType) {
			this.vertexBuffer = vertexBuffer;
			this.indexBuffer = indexBuffer;
			this.indexCount = indexCount;
			this.indexType = indexType;
		}

		public GpuBuffer getVertexBuffer() {
			return this.vertexBuffer;
		}

		@Nullable
		public GpuBuffer getIndexBuffer() {
			return this.indexBuffer;
		}

		public void setIndexBuffer(@Nullable GpuBuffer indexBuffer) {
			this.indexBuffer = indexBuffer;
		}

		public int getIndexCount() {
			return this.indexCount;
		}

		public VertexFormat.IndexType getIndexType() {
			return this.indexType;
		}

		public void setIndexType(VertexFormat.IndexType indexType) {
			this.indexType = indexType;
		}

		public void setIndexCount(int indexCount) {
			this.indexCount = indexCount;
		}

		public void setVertexBuffer(GpuBuffer vertexBuffer) {
			this.vertexBuffer = vertexBuffer;
		}

		public void close() {
			this.vertexBuffer.close();
			if (this.indexBuffer != null) {
				this.indexBuffer.close();
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public class BuiltChunk {
		public static final int CHUNK_SIZE = 16;
		public final int index;
		public final AtomicReference<ChunkBuilder.ChunkData> data = new AtomicReference(ChunkBuilder.ChunkData.UNPROCESSED);
		public final AtomicReference<ChunkBuilder.NormalizedRelativePos> relativePos = new AtomicReference(null);
		@Nullable
		private ChunkBuilder.BuiltChunk.RebuildTask rebuildTask;
		@Nullable
		private ChunkBuilder.BuiltChunk.SortTask sortTask;
		private final Set<BlockEntity> blockEntities = Sets.<BlockEntity>newHashSet();
		private final Map<RenderLayer, ChunkBuilder.Buffers> buffers = new HashMap();
		private Box boundingBox;
		private boolean needsRebuild = true;
		volatile long sectionPos = ChunkSectionPos.asLong(-1, -1, -1);
		final BlockPos.Mutable origin = new BlockPos.Mutable(-1, -1, -1);
		private boolean needsImportantRebuild;

		public BuiltChunk(final int index, final long sectionPos) {
			this.index = index;
			this.setSectionPos(sectionPos);
		}

		private boolean isChunkNonEmpty(long sectionPos) {
			Chunk chunk = ChunkBuilder.this.world.getChunk(ChunkSectionPos.unpackX(sectionPos), ChunkSectionPos.unpackZ(sectionPos), ChunkStatus.FULL, false);
			return chunk != null && ChunkBuilder.this.world.getLightingProvider().isLightingEnabled(ChunkSectionPos.withZeroY(sectionPos));
		}

		public boolean shouldBuild() {
			int i = 24;
			return !(this.getSquaredCameraDistance() > 576.0)
				? true
				: this.isChunkNonEmpty(ChunkSectionPos.offset(this.sectionPos, Direction.WEST))
					&& this.isChunkNonEmpty(ChunkSectionPos.offset(this.sectionPos, Direction.NORTH))
					&& this.isChunkNonEmpty(ChunkSectionPos.offset(this.sectionPos, Direction.EAST))
					&& this.isChunkNonEmpty(ChunkSectionPos.offset(this.sectionPos, Direction.SOUTH))
					&& this.isChunkNonEmpty(ChunkSectionPos.offset(this.sectionPos, -1, 0, -1))
					&& this.isChunkNonEmpty(ChunkSectionPos.offset(this.sectionPos, -1, 0, 1))
					&& this.isChunkNonEmpty(ChunkSectionPos.offset(this.sectionPos, 1, 0, -1))
					&& this.isChunkNonEmpty(ChunkSectionPos.offset(this.sectionPos, 1, 0, 1));
		}

		public Box getBoundingBox() {
			return this.boundingBox;
		}

		@Nullable
		public ChunkBuilder.Buffers getBuffers(RenderLayer layer) {
			return (ChunkBuilder.Buffers)this.buffers.get(layer);
		}

		public CompletableFuture<Void> uploadLayer(RenderLayer layer, BuiltBuffer buffer) {
			if (ChunkBuilder.this.stopped) {
				buffer.close();
				return CompletableFuture.completedFuture(null);
			} else {
				return CompletableFuture.runAsync(
					() -> {
						try (ScopedProfiler scopedProfiler = Profilers.get().scoped("Upload Section Layer")) {
							CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
							if (this.buffers.containsKey(layer)) {
								ChunkBuilder.Buffers buffers = (ChunkBuilder.Buffers)this.buffers.get(layer);
								if (buffers.vertexBuffer.size() < buffer.getBuffer().remaining()) {
									buffers.vertexBuffer.close();
									buffers.setVertexBuffer(
										RenderSystem.getDevice()
											.createBuffer(
												() -> "Section vertex buffer - layer: "
													+ layer.getName()
													+ "; cords: "
													+ ChunkSectionPos.unpackX(this.sectionPos)
													+ ", "
													+ ChunkSectionPos.unpackY(this.sectionPos)
													+ ", "
													+ ChunkSectionPos.unpackZ(this.sectionPos),
												BufferType.VERTICES,
												BufferUsage.STATIC_WRITE,
												buffer.getBuffer()
											)
									);
								} else if (!buffers.vertexBuffer.isClosed()) {
									commandEncoder.writeToBuffer(buffers.vertexBuffer, buffer.getBuffer(), 0);
								}

								if (buffer.getSortedBuffer() != null) {
									if (buffers.indexBuffer != null && buffers.indexBuffer.size() >= buffer.getSortedBuffer().remaining()) {
										if (!buffers.indexBuffer.isClosed()) {
											commandEncoder.writeToBuffer(buffers.indexBuffer, buffer.getSortedBuffer(), 0);
										}
									} else {
										if (buffers.indexBuffer != null) {
											buffers.indexBuffer.close();
										}

										buffers.setIndexBuffer(
											RenderSystem.getDevice()
												.createBuffer(
													() -> "Section index buffer - layer: "
														+ layer.getName()
														+ "; cords: "
														+ ChunkSectionPos.unpackX(this.sectionPos)
														+ ", "
														+ ChunkSectionPos.unpackY(this.sectionPos)
														+ ", "
														+ ChunkSectionPos.unpackZ(this.sectionPos),
													BufferType.INDICES,
													BufferUsage.STATIC_WRITE,
													buffer.getSortedBuffer()
												)
										);
									}
								} else if (buffers.indexBuffer != null) {
									buffers.indexBuffer.close();
									buffers.setIndexBuffer(null);
								}

								buffers.setIndexCount(buffer.getDrawParameters().indexCount());
								buffers.setIndexType(buffer.getDrawParameters().indexType());
							} else {
								GpuBuffer gpuBuffer = RenderSystem.getDevice()
									.createBuffer(
										() -> "Section vertex buffer - layer: "
											+ layer.getName()
											+ "; cords: "
											+ ChunkSectionPos.unpackX(this.sectionPos)
											+ ", "
											+ ChunkSectionPos.unpackY(this.sectionPos)
											+ ", "
											+ ChunkSectionPos.unpackZ(this.sectionPos),
										BufferType.VERTICES,
										BufferUsage.STATIC_WRITE,
										buffer.getBuffer()
									);
								GpuBuffer gpuBuffer2 = buffer.getSortedBuffer() != null
									? RenderSystem.getDevice()
										.createBuffer(
											() -> "Section index buffer - layer: "
												+ layer.getName()
												+ "; cords: "
												+ ChunkSectionPos.unpackX(this.sectionPos)
												+ ", "
												+ ChunkSectionPos.unpackY(this.sectionPos)
												+ ", "
												+ ChunkSectionPos.unpackZ(this.sectionPos),
											BufferType.INDICES,
											BufferUsage.STATIC_WRITE,
											buffer.getSortedBuffer()
										)
									: null;
								ChunkBuilder.Buffers buffers2 = new ChunkBuilder.Buffers(
									gpuBuffer, gpuBuffer2, buffer.getDrawParameters().indexCount(), buffer.getDrawParameters().indexType()
								);
								this.buffers.put(layer, buffers2);
							}

							buffer.close();
						}
					},
					ChunkBuilder.this.uploadQueue::add
				);
			}
		}

		public CompletableFuture<Void> uploadIndices(BufferAllocator.CloseableBuffer buffer, RenderLayer layer) {
			if (ChunkBuilder.this.stopped) {
				buffer.close();
				return CompletableFuture.completedFuture(null);
			} else {
				return CompletableFuture.runAsync(
					() -> {
						try (ScopedProfiler scopedProfiler = Profilers.get().scoped("Upload Section Indices")) {
							ChunkBuilder.Buffers buffers = this.getBuffers(layer);
							if (buffers != null && !ChunkBuilder.this.stopped) {
								if (buffers.indexBuffer == null) {
									buffers.setIndexBuffer(
										RenderSystem.getDevice()
											.createBuffer(
												() -> "Section index buffer - layer: "
													+ layer.getName()
													+ "; cords: "
													+ ChunkSectionPos.unpackX(this.sectionPos)
													+ ", "
													+ ChunkSectionPos.unpackY(this.sectionPos)
													+ ", "
													+ ChunkSectionPos.unpackZ(this.sectionPos),
												BufferType.INDICES,
												BufferUsage.STATIC_WRITE,
												buffer.getBuffer()
											)
									);
								} else {
									CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
									if (!buffers.indexBuffer.isClosed()) {
										commandEncoder.writeToBuffer(buffers.indexBuffer, buffer.getBuffer(), 0);
									}
								}

								buffer.close();
								return;
							}

							buffer.close();
						}
					},
					ChunkBuilder.this.uploadQueue::add
				);
			}
		}

		public void setSectionPos(long sectionPos) {
			this.clear();
			this.sectionPos = sectionPos;
			int i = ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackX(sectionPos));
			int j = ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackY(sectionPos));
			int k = ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackZ(sectionPos));
			this.origin.set(i, j, k);
			this.boundingBox = new Box(i, j, k, i + 16, j + 16, k + 16);
		}

		protected double getSquaredCameraDistance() {
			Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
			double d = this.boundingBox.minX + 8.0 - camera.getPos().x;
			double e = this.boundingBox.minY + 8.0 - camera.getPos().y;
			double f = this.boundingBox.minZ + 8.0 - camera.getPos().z;
			return d * d + e * e + f * f;
		}

		public ChunkBuilder.ChunkData getData() {
			return (ChunkBuilder.ChunkData)this.data.get();
		}

		public void clear() {
			this.cancel();
			this.data.set(ChunkBuilder.ChunkData.UNPROCESSED);
			this.relativePos.set(null);
			this.needsRebuild = true;
			this.buffers.values().forEach(ChunkBuilder.Buffers::close);
			this.buffers.clear();
		}

		public BlockPos getOrigin() {
			return this.origin;
		}

		public long getSectionPos() {
			return this.sectionPos;
		}

		public void scheduleRebuild(boolean important) {
			boolean bl = this.needsRebuild;
			this.needsRebuild = true;
			this.needsImportantRebuild = important | (bl && this.needsImportantRebuild);
		}

		public void cancelRebuild() {
			this.needsRebuild = false;
			this.needsImportantRebuild = false;
		}

		public boolean needsRebuild() {
			return this.needsRebuild;
		}

		public boolean needsImportantRebuild() {
			return this.needsRebuild && this.needsImportantRebuild;
		}

		public long getOffsetSectionPos(Direction direction) {
			return ChunkSectionPos.offset(this.sectionPos, direction);
		}

		public void scheduleSort(ChunkBuilder builder) {
			this.sortTask = new ChunkBuilder.BuiltChunk.SortTask(this.getData());
			builder.send(this.sortTask);
		}

		public boolean hasTranslucentLayer() {
			return this.getData().nonEmptyLayers.contains(RenderLayer.getTranslucent());
		}

		public boolean isCurrentlySorting() {
			return this.sortTask != null && !this.sortTask.finished.get();
		}

		protected void cancel() {
			if (this.rebuildTask != null) {
				this.rebuildTask.cancel();
				this.rebuildTask = null;
			}

			if (this.sortTask != null) {
				this.sortTask.cancel();
				this.sortTask = null;
			}
		}

		public ChunkBuilder.BuiltChunk.Task createRebuildTask(ChunkRendererRegionBuilder builder) {
			this.cancel();
			ChunkRendererRegion chunkRendererRegion = builder.build(ChunkBuilder.this.world, ChunkSectionPos.from(this.sectionPos));
			boolean bl = this.data.get() != ChunkBuilder.ChunkData.UNPROCESSED;
			this.rebuildTask = new ChunkBuilder.BuiltChunk.RebuildTask(chunkRendererRegion, bl);
			return this.rebuildTask;
		}

		public void scheduleRebuild(ChunkBuilder chunkRenderer, ChunkRendererRegionBuilder builder) {
			ChunkBuilder.BuiltChunk.Task task = this.createRebuildTask(builder);
			chunkRenderer.send(task);
		}

		void setNoCullingBlockEntities(Collection<BlockEntity> blockEntities) {
			Set<BlockEntity> set = Sets.<BlockEntity>newHashSet(blockEntities);
			Set<BlockEntity> set2;
			synchronized (this.blockEntities) {
				set2 = Sets.<BlockEntity>newHashSet(this.blockEntities);
				set.removeAll(this.blockEntities);
				set2.removeAll(blockEntities);
				this.blockEntities.clear();
				this.blockEntities.addAll(blockEntities);
			}

			ChunkBuilder.this.worldRenderer.updateNoCullingBlockEntities(set2, set);
		}

		public void rebuild(ChunkRendererRegionBuilder builder) {
			ChunkBuilder.BuiltChunk.Task task = this.createRebuildTask(builder);
			task.run(ChunkBuilder.this.buffers);
		}

		void setData(ChunkBuilder.ChunkData chunkData) {
			this.data.set(chunkData);
			ChunkBuilder.this.worldRenderer.addBuiltChunk(this);
		}

		VertexSorter getVertexSorter(ChunkSectionPos sectionPos) {
			Vec3d vec3d = ChunkBuilder.this.getCameraPosition();
			return VertexSorter.byDistance((float)(vec3d.x - sectionPos.getMinX()), (float)(vec3d.y - sectionPos.getMinY()), (float)(vec3d.z - sectionPos.getMinZ()));
		}

		@Environment(EnvType.CLIENT)
		class RebuildTask extends ChunkBuilder.BuiltChunk.Task {
			@Nullable
			protected volatile ChunkRendererRegion region;

			public RebuildTask(@Nullable final ChunkRendererRegion region, final boolean prioritized) {
				super(prioritized);
				this.region = region;
			}

			@Override
			protected String getName() {
				return "rend_chk_rebuild";
			}

			@Override
			public CompletableFuture<ChunkBuilder.Result> run(BlockBufferAllocatorStorage buffers) {
				if (this.cancelled.get()) {
					return CompletableFuture.completedFuture(ChunkBuilder.Result.CANCELLED);
				} else {
					ChunkRendererRegion chunkRendererRegion = this.region;
					this.region = null;
					if (chunkRendererRegion == null) {
						BuiltChunk.this.setData(ChunkBuilder.ChunkData.EMPTY);
						return CompletableFuture.completedFuture(ChunkBuilder.Result.SUCCESSFUL);
					} else {
						long l = BuiltChunk.this.sectionPos;
						ChunkSectionPos chunkSectionPos = ChunkSectionPos.from(l);
						if (this.cancelled.get()) {
							return CompletableFuture.completedFuture(ChunkBuilder.Result.CANCELLED);
						} else {
							SectionBuilder.RenderData renderData;
							try (ScopedProfiler scopedProfiler = Profilers.get().scoped("Compile Section")) {
								renderData = ChunkBuilder.this.sectionBuilder.build(chunkSectionPos, chunkRendererRegion, BuiltChunk.this.getVertexSorter(chunkSectionPos), buffers);
							}

							ChunkBuilder.NormalizedRelativePos normalizedRelativePos = ChunkBuilder.NormalizedRelativePos.of(ChunkBuilder.this.getCameraPosition(), l);
							BuiltChunk.this.setNoCullingBlockEntities(renderData.noCullingBlockEntities);
							if (this.cancelled.get()) {
								renderData.close();
								return CompletableFuture.completedFuture(ChunkBuilder.Result.CANCELLED);
							} else {
								ChunkBuilder.ChunkData chunkData = new ChunkBuilder.ChunkData();
								chunkData.occlusionGraph = renderData.chunkOcclusionData;
								chunkData.blockEntities.addAll(renderData.blockEntities);
								chunkData.transparentSortingData = renderData.translucencySortingData;
								List<CompletableFuture<Void>> list = new ArrayList(renderData.buffers.size());
								renderData.buffers.forEach((renderLayer, buffer) -> {
									list.add(BuiltChunk.this.uploadLayer(renderLayer, buffer));
									chunkData.nonEmptyLayers.add(renderLayer);
								});
								return Util.combine(list).handle((v, throwable) -> {
									if (throwable != null && !(throwable instanceof CancellationException) && !(throwable instanceof InterruptedException)) {
										MinecraftClient.getInstance().setCrashReportSupplierAndAddDetails(CrashReport.create(throwable, "Rendering section"));
									}

									if (this.cancelled.get()) {
										return ChunkBuilder.Result.CANCELLED;
									} else {
										BuiltChunk.this.setData(chunkData);
										BuiltChunk.this.relativePos.set(normalizedRelativePos);
										return ChunkBuilder.Result.SUCCESSFUL;
									}
								});
							}
						}
					}
				}
			}

			@Override
			public void cancel() {
				this.region = null;
				if (this.cancelled.compareAndSet(false, true)) {
					BuiltChunk.this.scheduleRebuild(false);
				}
			}
		}

		@Environment(EnvType.CLIENT)
		class SortTask extends ChunkBuilder.BuiltChunk.Task {
			private final ChunkBuilder.ChunkData data;

			public SortTask(final ChunkBuilder.ChunkData data) {
				super(true);
				this.data = data;
			}

			@Override
			protected String getName() {
				return "rend_chk_sort";
			}

			@Override
			public CompletableFuture<ChunkBuilder.Result> run(BlockBufferAllocatorStorage buffers) {
				if (this.cancelled.get()) {
					return CompletableFuture.completedFuture(ChunkBuilder.Result.CANCELLED);
				} else {
					BuiltBuffer.SortState sortState = this.data.transparentSortingData;
					if (sortState != null && !this.data.isEmpty(RenderLayer.getTranslucent())) {
						long l = BuiltChunk.this.sectionPos;
						VertexSorter vertexSorter = BuiltChunk.this.getVertexSorter(ChunkSectionPos.from(l));
						ChunkBuilder.NormalizedRelativePos normalizedRelativePos = ChunkBuilder.NormalizedRelativePos.of(ChunkBuilder.this.getCameraPosition(), l);
						if (normalizedRelativePos.equals(BuiltChunk.this.relativePos.get()) && !normalizedRelativePos.isOnCameraAxis()) {
							return CompletableFuture.completedFuture(ChunkBuilder.Result.CANCELLED);
						} else {
							BufferAllocator.CloseableBuffer closeableBuffer = sortState.sortAndStore(buffers.get(RenderLayer.getTranslucent()), vertexSorter);
							if (closeableBuffer == null) {
								return CompletableFuture.completedFuture(ChunkBuilder.Result.CANCELLED);
							} else if (this.cancelled.get()) {
								closeableBuffer.close();
								return CompletableFuture.completedFuture(ChunkBuilder.Result.CANCELLED);
							} else {
								CompletableFuture<ChunkBuilder.Result> completableFuture = BuiltChunk.this.uploadIndices(closeableBuffer, RenderLayer.getTranslucent())
									.thenApply(v -> ChunkBuilder.Result.CANCELLED);
								return completableFuture.handle((result, throwable) -> {
									if (throwable != null && !(throwable instanceof CancellationException) && !(throwable instanceof InterruptedException)) {
										MinecraftClient.getInstance().setCrashReportSupplierAndAddDetails(CrashReport.create(throwable, "Rendering section"));
									}

									if (this.cancelled.get()) {
										return ChunkBuilder.Result.CANCELLED;
									} else {
										BuiltChunk.this.relativePos.set(normalizedRelativePos);
										return ChunkBuilder.Result.SUCCESSFUL;
									}
								});
							}
						}
					} else {
						return CompletableFuture.completedFuture(ChunkBuilder.Result.CANCELLED);
					}
				}
			}

			@Override
			public void cancel() {
				this.cancelled.set(true);
			}
		}

		@Environment(EnvType.CLIENT)
		public abstract class Task {
			protected final AtomicBoolean cancelled = new AtomicBoolean(false);
			protected final AtomicBoolean finished = new AtomicBoolean(false);
			protected final boolean prioritized;

			public Task(final boolean prioritized) {
				this.prioritized = prioritized;
			}

			public abstract CompletableFuture<ChunkBuilder.Result> run(BlockBufferAllocatorStorage buffers);

			public abstract void cancel();

			protected abstract String getName();

			public boolean isPrioritized() {
				return this.prioritized;
			}

			public BlockPos getOrigin() {
				return BuiltChunk.this.origin;
			}
		}
	}

	@Environment(EnvType.CLIENT)
	public static class ChunkData {
		public static final ChunkBuilder.ChunkData UNPROCESSED = new ChunkBuilder.ChunkData() {
			@Override
			public boolean isVisibleThrough(Direction from, Direction to) {
				return false;
			}
		};
		public static final ChunkBuilder.ChunkData EMPTY = new ChunkBuilder.ChunkData() {
			@Override
			public boolean isVisibleThrough(Direction from, Direction to) {
				return true;
			}
		};
		final Set<RenderLayer> nonEmptyLayers = new ObjectArraySet<>(RenderLayer.getBlockLayers().size());
		final List<BlockEntity> blockEntities = Lists.<BlockEntity>newArrayList();
		ChunkOcclusionData occlusionGraph = new ChunkOcclusionData();
		@Nullable
		BuiltBuffer.SortState transparentSortingData;

		public boolean hasNonEmptyLayers() {
			return !this.nonEmptyLayers.isEmpty();
		}

		public boolean isEmpty(RenderLayer layer) {
			return !this.nonEmptyLayers.contains(layer);
		}

		public List<BlockEntity> getBlockEntities() {
			return this.blockEntities;
		}

		public boolean isVisibleThrough(Direction from, Direction to) {
			return this.occlusionGraph.isVisibleThrough(from, to);
		}
	}

	@Environment(EnvType.CLIENT)
	public static final class NormalizedRelativePos {
		private int x;
		private int y;
		private int z;

		public static ChunkBuilder.NormalizedRelativePos of(Vec3d cameraPos, long sectionPos) {
			return new ChunkBuilder.NormalizedRelativePos().with(cameraPos, sectionPos);
		}

		public ChunkBuilder.NormalizedRelativePos with(Vec3d cameraPos, long sectionPos) {
			this.x = normalize(cameraPos.getX(), ChunkSectionPos.unpackX(sectionPos));
			this.y = normalize(cameraPos.getY(), ChunkSectionPos.unpackY(sectionPos));
			this.z = normalize(cameraPos.getZ(), ChunkSectionPos.unpackZ(sectionPos));
			return this;
		}

		private static int normalize(double cameraCoord, int sectionCoord) {
			int i = ChunkSectionPos.getSectionCoordFloored(cameraCoord) - sectionCoord;
			return MathHelper.clamp(i, -1, 1);
		}

		public boolean isOnCameraAxis() {
			return this.x == 0 || this.y == 0 || this.z == 0;
		}

		public boolean equals(Object o) {
			if (o == this) {
				return true;
			} else {
				return !(o instanceof ChunkBuilder.NormalizedRelativePos normalizedRelativePos)
					? false
					: this.x == normalizedRelativePos.x && this.y == normalizedRelativePos.y && this.z == normalizedRelativePos.z;
			}
		}
	}

	@Environment(EnvType.CLIENT)
	static enum Result {
		SUCCESSFUL,
		CANCELLED;
	}
}
