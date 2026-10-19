package net.minecraft.world.chunk.light;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkProvider;

public final class ChunkBlockLightProvider extends ChunkLightProvider<BlockLightStorage.Data, BlockLightStorage> {
	private final BlockPos.Mutable mutablePos = new BlockPos.Mutable();

	public ChunkBlockLightProvider(ChunkProvider chunkProvider) {
		this(chunkProvider, new BlockLightStorage(chunkProvider));
	}

	@VisibleForTesting
	public ChunkBlockLightProvider(ChunkProvider chunkProvider, BlockLightStorage blockLightStorage) {
		super(chunkProvider, blockLightStorage);
	}

	@Override
	protected void method_51529(long blockPos) {
		long l = ChunkSectionPos.fromBlockPos(blockPos);
		if (this.lightStorage.hasSection(l)) {
			BlockState blockState = this.getStateForLighting(this.mutablePos.set(blockPos));
			int i = this.getLightSourceLuminance(blockPos, blockState);
			int j = this.lightStorage.get(blockPos);
			if (i < j) {
				this.lightStorage.set(blockPos, 0);
				this.method_51565(blockPos, ChunkLightProvider.class_8531.packWithAllDirectionsSet(j));
			} else {
				this.method_51565(blockPos, field_44731);
			}

			if (i > 0) {
				this.method_51566(blockPos, ChunkLightProvider.class_8531.method_51573(i, isTrivialForLighting(blockState)));
			}
		}
	}

	@Override
	protected void method_51531(long blockPos, long packed, int lightLevel) {
		BlockState blockState = null;

		for (Direction direction : DIRECTIONS) {
			if (ChunkLightProvider.class_8531.isDirectionBitSet(packed, direction)) {
				long l = BlockPos.offset(blockPos, direction);
				if (this.lightStorage.hasSection(ChunkSectionPos.fromBlockPos(l))) {
					int i = this.lightStorage.get(l);
					int j = lightLevel - 1;
					if (j > i) {
						this.mutablePos.set(l);
						BlockState blockState2 = this.getStateForLighting(this.mutablePos);
						int k = lightLevel - this.getOpacity(blockState2);
						if (k > i) {
							if (blockState == null) {
								blockState = ChunkLightProvider.class_8531.isTrivial(packed) ? Blocks.AIR.getDefaultState() : this.getStateForLighting(this.mutablePos.set(blockPos));
							}

							if (!this.shapesCoverFullCube(blockState, blockState2, direction)) {
								this.lightStorage.set(l, k);
								if (k > 1) {
									this.method_51566(l, ChunkLightProvider.class_8531.packWithOneDirectionCleared(k, isTrivialForLighting(blockState2), direction.getOpposite()));
								}
							}
						}
					}
				}
			}
		}
	}

	@Override
	protected void method_51530(long blockPos, long packed) {
		int i = ChunkLightProvider.class_8531.getLightLevel(packed);

		for (Direction direction : DIRECTIONS) {
			if (ChunkLightProvider.class_8531.isDirectionBitSet(packed, direction)) {
				long l = BlockPos.offset(blockPos, direction);
				if (this.lightStorage.hasSection(ChunkSectionPos.fromBlockPos(l))) {
					int j = this.lightStorage.get(l);
					if (j != 0) {
						if (j <= i - 1) {
							BlockState blockState = this.getStateForLighting(this.mutablePos.set(l));
							int k = this.getLightSourceLuminance(l, blockState);
							this.lightStorage.set(l, 0);
							if (k < j) {
								this.method_51565(l, ChunkLightProvider.class_8531.packWithOneDirectionCleared(j, direction.getOpposite()));
							}

							if (k > 0) {
								this.method_51566(l, ChunkLightProvider.class_8531.method_51573(k, isTrivialForLighting(blockState)));
							}
						} else {
							this.method_51566(l, ChunkLightProvider.class_8531.method_51579(j, false, direction.getOpposite()));
						}
					}
				}
			}
		}
	}

	private int getLightSourceLuminance(long blockPos, BlockState blockState) {
		int i = blockState.getLuminance();
		return i > 0 && this.lightStorage.isSectionInEnabledColumn(ChunkSectionPos.fromBlockPos(blockPos)) ? i : 0;
	}

	@Override
	public void propagateLight(ChunkPos chunkPos) {
		this.setColumnEnabled(chunkPos, true);
		LightSourceView lightSourceView = this.chunkProvider.getChunk(chunkPos.x, chunkPos.z);
		if (lightSourceView != null) {
			lightSourceView.forEachLightSource((blockPos, blockState) -> {
				int i = blockState.getLuminance();
				this.method_51566(blockPos.asLong(), ChunkLightProvider.class_8531.method_51573(i, isTrivialForLighting(blockState)));
			});
		}
	}
}
