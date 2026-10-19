package net.minecraft.world.chunk.light;

import java.util.Objects;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkProvider;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public final class ChunkSkyLightProvider extends ChunkLightProvider<SkyLightStorage.Data, SkyLightStorage> {
	private static final long field_44743 = ChunkLightProvider.class_8531.packWithAllDirectionsSet(15);
	private static final long field_44744 = ChunkLightProvider.class_8531.packWithOneDirectionCleared(15, Direction.UP);
	private static final long field_44745 = ChunkLightProvider.class_8531.packWithOneDirectionCleared(15, false, Direction.UP);
	private final BlockPos.Mutable field_44746 = new BlockPos.Mutable();
	private final ChunkSkyLight defaultSkyLight;

	public ChunkSkyLightProvider(ChunkProvider chunkProvider) {
		this(chunkProvider, new SkyLightStorage(chunkProvider));
	}

	@VisibleForTesting
	protected ChunkSkyLightProvider(ChunkProvider chunkProvider, SkyLightStorage lightStorage) {
		super(chunkProvider, lightStorage);
		this.defaultSkyLight = new ChunkSkyLight(chunkProvider.getWorld());
	}

	private static boolean isMaxLightLevel(int lightLevel) {
		return lightLevel == 15;
	}

	private int getSkyLightOrDefault(int x, int z, int defaultValue) {
		ChunkSkyLight chunkSkyLight = this.getSkyLight(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z));
		return chunkSkyLight == null ? defaultValue : chunkSkyLight.get(ChunkSectionPos.getLocalCoord(x), ChunkSectionPos.getLocalCoord(z));
	}

	@Nullable
	private ChunkSkyLight getSkyLight(int chunkX, int chunkZ) {
		LightSourceView lightSourceView = this.chunkProvider.getChunk(chunkX, chunkZ);
		return lightSourceView != null ? lightSourceView.getChunkSkyLight() : null;
	}

	@Override
	protected void method_51529(long blockPos) {
		int i = BlockPos.unpackLongX(blockPos);
		int j = BlockPos.unpackLongY(blockPos);
		int k = BlockPos.unpackLongZ(blockPos);
		long l = ChunkSectionPos.fromBlockPos(blockPos);
		int m = this.lightStorage.isSectionInEnabledColumn(l) ? this.getSkyLightOrDefault(i, k, Integer.MAX_VALUE) : Integer.MAX_VALUE;
		if (m != Integer.MAX_VALUE) {
			this.method_51590(i, k, m);
		}

		if (this.lightStorage.hasSection(l)) {
			boolean bl = j >= m;
			if (bl) {
				this.method_51565(blockPos, field_44744);
				this.method_51566(blockPos, field_44745);
			} else {
				int n = this.lightStorage.get(blockPos);
				if (n > 0) {
					this.lightStorage.set(blockPos, 0);
					this.method_51565(blockPos, ChunkLightProvider.class_8531.packWithAllDirectionsSet(n));
				} else {
					this.method_51565(blockPos, field_44731);
				}
			}
		}
	}

	private void method_51590(int i, int j, int k) {
		int l = ChunkSectionPos.getBlockCoord(this.lightStorage.getMinSectionY());
		this.method_51586(i, j, k, l);
		this.method_51591(i, j, k, l);
	}

	private void method_51586(int x, int z, int i, int j) {
		if (i > j) {
			int k = ChunkSectionPos.getSectionCoord(x);
			int l = ChunkSectionPos.getSectionCoord(z);
			int m = i - 1;

			for (int n = ChunkSectionPos.getSectionCoord(m); this.lightStorage.isAboveMinHeight(n); n--) {
				if (this.lightStorage.hasSection(ChunkSectionPos.asLong(k, n, l))) {
					int o = ChunkSectionPos.getBlockCoord(n);
					int p = o + 15;

					for (int q = Math.min(p, m); q >= o; q--) {
						long r = BlockPos.asLong(x, q, z);
						if (!isMaxLightLevel(this.lightStorage.get(r))) {
							return;
						}

						this.lightStorage.set(r, 0);
						this.method_51565(r, q == i - 1 ? field_44743 : field_44744);
					}
				}
			}
		}
	}

	private void method_51591(int x, int z, int i, int j) {
		int k = ChunkSectionPos.getSectionCoord(x);
		int l = ChunkSectionPos.getSectionCoord(z);
		int m = Math.max(
			Math.max(this.getSkyLightOrDefault(x - 1, z, Integer.MIN_VALUE), this.getSkyLightOrDefault(x + 1, z, Integer.MIN_VALUE)),
			Math.max(this.getSkyLightOrDefault(x, z - 1, Integer.MIN_VALUE), this.getSkyLightOrDefault(x, z + 1, Integer.MIN_VALUE))
		);
		int n = Math.max(i, j);

		for (long o = ChunkSectionPos.asLong(k, ChunkSectionPos.getSectionCoord(n), l);
			!this.lightStorage.isAtOrAboveTopmostSection(o);
			o = ChunkSectionPos.offset(o, Direction.UP)
		) {
			if (this.lightStorage.hasSection(o)) {
				int p = ChunkSectionPos.getBlockCoord(ChunkSectionPos.unpackY(o));
				int q = p + 15;

				for (int r = Math.max(p, n); r <= q; r++) {
					long s = BlockPos.asLong(x, r, z);
					if (isMaxLightLevel(this.lightStorage.get(s))) {
						return;
					}

					this.lightStorage.set(s, 15);
					if (r < m || r == i) {
						this.method_51566(s, field_44745);
					}
				}
			}
		}
	}

	@Override
	protected void method_51531(long blockPos, long packed, int lightLevel) {
		BlockState blockState = null;
		int i = this.getNumberOfSectionsBelowPos(blockPos);

		for (Direction direction : DIRECTIONS) {
			if (ChunkLightProvider.class_8531.isDirectionBitSet(packed, direction)) {
				long l = BlockPos.offset(blockPos, direction);
				if (this.lightStorage.hasSection(ChunkSectionPos.fromBlockPos(l))) {
					int j = this.lightStorage.get(l);
					int k = lightLevel - 1;
					if (k > j) {
						this.field_44746.set(l);
						BlockState blockState2 = this.getStateForLighting(this.field_44746);
						int m = lightLevel - this.getOpacity(blockState2);
						if (m > j) {
							if (blockState == null) {
								blockState = ChunkLightProvider.class_8531.isTrivial(packed) ? Blocks.AIR.getDefaultState() : this.getStateForLighting(this.field_44746.set(blockPos));
							}

							if (!this.shapesCoverFullCube(blockState, blockState2, direction)) {
								this.lightStorage.set(l, m);
								if (m > 1) {
									this.method_51566(l, ChunkLightProvider.class_8531.packWithOneDirectionCleared(m, isTrivialForLighting(blockState2), direction.getOpposite()));
								}

								this.method_51587(l, direction, m, true, i);
							}
						}
					}
				}
			}
		}
	}

	@Override
	protected void method_51530(long blockPos, long packed) {
		int i = this.getNumberOfSectionsBelowPos(blockPos);
		int j = ChunkLightProvider.class_8531.getLightLevel(packed);

		for (Direction direction : DIRECTIONS) {
			if (ChunkLightProvider.class_8531.isDirectionBitSet(packed, direction)) {
				long l = BlockPos.offset(blockPos, direction);
				if (this.lightStorage.hasSection(ChunkSectionPos.fromBlockPos(l))) {
					int k = this.lightStorage.get(l);
					if (k != 0) {
						if (k <= j - 1) {
							this.lightStorage.set(l, 0);
							this.method_51565(l, ChunkLightProvider.class_8531.packWithOneDirectionCleared(k, direction.getOpposite()));
							this.method_51587(l, direction, k, false, i);
						} else {
							this.method_51566(l, ChunkLightProvider.class_8531.method_51579(k, false, direction.getOpposite()));
						}
					}
				}
			}
		}
	}

	private int getNumberOfSectionsBelowPos(long blockPos) {
		int i = BlockPos.unpackLongY(blockPos);
		int j = ChunkSectionPos.getLocalCoord(i);
		if (j != 0) {
			return 0;
		} else {
			int k = BlockPos.unpackLongX(blockPos);
			int l = BlockPos.unpackLongZ(blockPos);
			int m = ChunkSectionPos.getLocalCoord(k);
			int n = ChunkSectionPos.getLocalCoord(l);
			if (m != 0 && m != 15 && n != 0 && n != 15) {
				return 0;
			} else {
				int o = ChunkSectionPos.getSectionCoord(k);
				int p = ChunkSectionPos.getSectionCoord(i);
				int q = ChunkSectionPos.getSectionCoord(l);
				int r = 0;

				while (!this.lightStorage.hasSection(ChunkSectionPos.asLong(o, p - r - 1, q)) && this.lightStorage.isAboveMinHeight(p - r - 1)) {
					r++;
				}

				return r;
			}
		}
	}

	private void method_51587(long blockPos, Direction direction, int lightLevel, boolean bl, int i) {
		if (i != 0) {
			int j = BlockPos.unpackLongX(blockPos);
			int k = BlockPos.unpackLongZ(blockPos);
			if (exitsChunkXZ(direction, ChunkSectionPos.getLocalCoord(j), ChunkSectionPos.getLocalCoord(k))) {
				int l = BlockPos.unpackLongY(blockPos);
				int m = ChunkSectionPos.getSectionCoord(j);
				int n = ChunkSectionPos.getSectionCoord(k);
				int o = ChunkSectionPos.getSectionCoord(l) - 1;
				int p = o - i + 1;

				while (o >= p) {
					if (!this.lightStorage.hasSection(ChunkSectionPos.asLong(m, o, n))) {
						o--;
					} else {
						int q = ChunkSectionPos.getBlockCoord(o);

						for (int r = 15; r >= 0; r--) {
							long s = BlockPos.asLong(j, q + r, k);
							if (bl) {
								this.lightStorage.set(s, lightLevel);
								if (lightLevel > 1) {
									this.method_51566(s, ChunkLightProvider.class_8531.packWithOneDirectionCleared(lightLevel, true, direction.getOpposite()));
								}
							} else {
								this.lightStorage.set(s, 0);
								this.method_51565(s, ChunkLightProvider.class_8531.packWithOneDirectionCleared(lightLevel, direction.getOpposite()));
							}
						}

						o--;
					}
				}
			}
		}
	}

	private static boolean exitsChunkXZ(Direction direction, int localX, int localZ) {
		return switch (direction) {
			case NORTH -> localZ == 15;
			case SOUTH -> localZ == 0;
			case WEST -> localX == 15;
			case EAST -> localX == 0;
			default -> false;
		};
	}

	@Override
	public void setColumnEnabled(ChunkPos pos, boolean retainData) {
		super.setColumnEnabled(pos, retainData);
		if (retainData) {
			ChunkSkyLight chunkSkyLight = (ChunkSkyLight)Objects.requireNonNullElse(this.getSkyLight(pos.x, pos.z), this.defaultSkyLight);
			int i = chunkSkyLight.getMaxSurfaceY() - 1;
			int j = ChunkSectionPos.getSectionCoord(i) + 1;
			long l = ChunkSectionPos.withZeroY(pos.x, pos.z);
			int k = this.lightStorage.getTopSectionForColumn(l);
			int m = Math.max(this.lightStorage.getMinSectionY(), j);

			for (int n = k - 1; n >= m; n--) {
				ChunkNibbleArray chunkNibbleArray = this.lightStorage.method_51547(ChunkSectionPos.asLong(pos.x, n, pos.z));
				if (chunkNibbleArray != null && chunkNibbleArray.isUninitialized()) {
					chunkNibbleArray.clear(15);
				}
			}
		}
	}

	@Override
	public void propagateLight(ChunkPos chunkPos) {
		long l = ChunkSectionPos.withZeroY(chunkPos.x, chunkPos.z);
		this.lightStorage.setColumnEnabled(l, true);
		ChunkSkyLight chunkSkyLight = (ChunkSkyLight)Objects.requireNonNullElse(this.getSkyLight(chunkPos.x, chunkPos.z), this.defaultSkyLight);
		ChunkSkyLight chunkSkyLight2 = (ChunkSkyLight)Objects.requireNonNullElse(this.getSkyLight(chunkPos.x, chunkPos.z - 1), this.defaultSkyLight);
		ChunkSkyLight chunkSkyLight3 = (ChunkSkyLight)Objects.requireNonNullElse(this.getSkyLight(chunkPos.x, chunkPos.z + 1), this.defaultSkyLight);
		ChunkSkyLight chunkSkyLight4 = (ChunkSkyLight)Objects.requireNonNullElse(this.getSkyLight(chunkPos.x - 1, chunkPos.z), this.defaultSkyLight);
		ChunkSkyLight chunkSkyLight5 = (ChunkSkyLight)Objects.requireNonNullElse(this.getSkyLight(chunkPos.x + 1, chunkPos.z), this.defaultSkyLight);
		int i = this.lightStorage.getTopSectionForColumn(l);
		int j = this.lightStorage.getMinSectionY();
		int k = ChunkSectionPos.getBlockCoord(chunkPos.x);
		int m = ChunkSectionPos.getBlockCoord(chunkPos.z);

		for (int n = i - 1; n >= j; n--) {
			long o = ChunkSectionPos.asLong(chunkPos.x, n, chunkPos.z);
			ChunkNibbleArray chunkNibbleArray = this.lightStorage.method_51547(o);
			if (chunkNibbleArray != null) {
				int p = ChunkSectionPos.getBlockCoord(n);
				int q = p + 15;
				boolean bl = false;

				for (int r = 0; r < 16; r++) {
					for (int s = 0; s < 16; s++) {
						int t = chunkSkyLight.get(s, r);
						if (t <= q) {
							int u = r == 0 ? chunkSkyLight2.get(s, 15) : chunkSkyLight.get(s, r - 1);
							int v = r == 15 ? chunkSkyLight3.get(s, 0) : chunkSkyLight.get(s, r + 1);
							int w = s == 0 ? chunkSkyLight4.get(15, r) : chunkSkyLight.get(s - 1, r);
							int x = s == 15 ? chunkSkyLight5.get(0, r) : chunkSkyLight.get(s + 1, r);
							int y = Math.max(Math.max(u, v), Math.max(w, x));

							for (int z = q; z >= Math.max(p, t); z--) {
								chunkNibbleArray.set(s, ChunkSectionPos.getLocalCoord(z), r, 15);
								if (z == t || z < y) {
									long aa = BlockPos.asLong(k + s, z, m + r);
									this.method_51566(aa, ChunkLightProvider.class_8531.method_51578(z == t, z < u, z < v, z < w, z < x));
								}
							}

							if (t < p) {
								bl = true;
							}
						}
					}
				}

				if (!bl) {
					break;
				}
			}
		}
	}
}
