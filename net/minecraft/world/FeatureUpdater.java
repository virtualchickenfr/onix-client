package net.minecraft.world;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import org.jetbrains.annotations.Nullable;

public class FeatureUpdater {
	private static final Map<String, String> OLD_TO_NEW = Util.make(Maps.<String, String>newHashMap(), map -> {
		map.put("Village", "Village");
		map.put("Mineshaft", "Mineshaft");
		map.put("Mansion", "Mansion");
		map.put("Igloo", "Temple");
		map.put("Desert_Pyramid", "Temple");
		map.put("Jungle_Pyramid", "Temple");
		map.put("Swamp_Hut", "Temple");
		map.put("Stronghold", "Stronghold");
		map.put("Monument", "Monument");
		map.put("Fortress", "Fortress");
		map.put("EndCity", "EndCity");
	});
	private static final Map<String, String> ANCIENT_TO_OLD = Util.make(Maps.<String, String>newHashMap(), map -> {
		map.put("Iglu", "Igloo");
		map.put("TeDP", "Desert_Pyramid");
		map.put("TeJP", "Jungle_Pyramid");
		map.put("TeSH", "Swamp_Hut");
	});
	private static final Set<String> NEW_STRUCTURE_NAMES = Set.of(
		"pillager_outpost",
		"mineshaft",
		"mansion",
		"jungle_pyramid",
		"desert_pyramid",
		"igloo",
		"ruined_portal",
		"shipwreck",
		"swamp_hut",
		"stronghold",
		"monument",
		"ocean_ruin",
		"fortress",
		"endcity",
		"buried_treasure",
		"village",
		"nether_fossil",
		"bastion_remnant"
	);
	private final boolean needsUpdate;
	private final Map<String, Long2ObjectMap<NbtCompound>> featureIdToChunkNbt = Maps.<String, Long2ObjectMap<NbtCompound>>newHashMap();
	private final Map<String, ChunkUpdateState> updateStates = Maps.<String, ChunkUpdateState>newHashMap();
	private final List<String> oldNames;
	private final List<String> newNames;

	public FeatureUpdater(@Nullable PersistentStateManager persistentStateManager, List<String> oldNames, List<String> newNames) {
		this.oldNames = oldNames;
		this.newNames = newNames;
		this.init(persistentStateManager);
		boolean bl = false;

		for (String string : this.newNames) {
			bl |= this.featureIdToChunkNbt.get(string) != null;
		}

		this.needsUpdate = bl;
	}

	public void markResolved(long chunkPos) {
		for (String string : this.oldNames) {
			ChunkUpdateState chunkUpdateState = (ChunkUpdateState)this.updateStates.get(string);
			if (chunkUpdateState != null && chunkUpdateState.isRemaining(chunkPos)) {
				chunkUpdateState.markResolved(chunkPos);
			}
		}
	}

	public NbtCompound getUpdatedReferences(NbtCompound nbt) {
		NbtCompound nbtCompound = nbt.getCompoundOrEmpty("Level");
		ChunkPos chunkPos = new ChunkPos(nbtCompound.getInt("xPos", 0), nbtCompound.getInt("zPos", 0));
		if (this.needsUpdate(chunkPos.x, chunkPos.z)) {
			nbt = this.getUpdatedStarts(nbt, chunkPos);
		}

		NbtCompound nbtCompound2 = nbtCompound.getCompoundOrEmpty("Structures");
		NbtCompound nbtCompound3 = nbtCompound2.getCompoundOrEmpty("References");

		for (String string : this.newNames) {
			boolean bl = NEW_STRUCTURE_NAMES.contains(string.toLowerCase(Locale.ROOT));
			if (!nbtCompound3.getLongArray(string).isPresent() && bl) {
				int i = 8;
				LongList longList = new LongArrayList();

				for (int j = chunkPos.x - 8; j <= chunkPos.x + 8; j++) {
					for (int k = chunkPos.z - 8; k <= chunkPos.z + 8; k++) {
						if (this.needsUpdate(j, k, string)) {
							longList.add(ChunkPos.toLong(j, k));
						}
					}
				}

				nbtCompound3.putLongArray(string, longList.toLongArray());
			}
		}

		nbtCompound2.put("References", nbtCompound3);
		nbtCompound.put("Structures", nbtCompound2);
		nbt.put("Level", nbtCompound);
		return nbt;
	}

	private boolean needsUpdate(int chunkX, int chunkZ, String id) {
		return !this.needsUpdate
			? false
			: this.featureIdToChunkNbt.get(id) != null && ((ChunkUpdateState)this.updateStates.get(OLD_TO_NEW.get(id))).contains(ChunkPos.toLong(chunkX, chunkZ));
	}

	private boolean needsUpdate(int chunkX, int chunkZ) {
		if (!this.needsUpdate) {
			return false;
		} else {
			for (String string : this.newNames) {
				if (this.featureIdToChunkNbt.get(string) != null
					&& ((ChunkUpdateState)this.updateStates.get(OLD_TO_NEW.get(string))).isRemaining(ChunkPos.toLong(chunkX, chunkZ))) {
					return true;
				}
			}

			return false;
		}
	}

	private NbtCompound getUpdatedStarts(NbtCompound nbt, ChunkPos pos) {
		NbtCompound nbtCompound = nbt.getCompoundOrEmpty("Level");
		NbtCompound nbtCompound2 = nbtCompound.getCompoundOrEmpty("Structures");
		NbtCompound nbtCompound3 = nbtCompound2.getCompoundOrEmpty("Starts");

		for (String string : this.newNames) {
			Long2ObjectMap<NbtCompound> long2ObjectMap = (Long2ObjectMap<NbtCompound>)this.featureIdToChunkNbt.get(string);
			if (long2ObjectMap != null) {
				long l = pos.toLong();
				if (((ChunkUpdateState)this.updateStates.get(OLD_TO_NEW.get(string))).isRemaining(l)) {
					NbtCompound nbtCompound4 = long2ObjectMap.get(l);
					if (nbtCompound4 != null) {
						nbtCompound3.put(string, nbtCompound4);
					}
				}
			}
		}

		nbtCompound2.put("Starts", nbtCompound3);
		nbtCompound.put("Structures", nbtCompound2);
		nbt.put("Level", nbtCompound);
		return nbt;
	}

	private void init(@Nullable PersistentStateManager persistentStateManager) {
		if (persistentStateManager != null) {
			for (String string : this.oldNames) {
				NbtCompound nbtCompound = new NbtCompound();

				try {
					nbtCompound = persistentStateManager.readNbt(string, DataFixTypes.SAVED_DATA_STRUCTURE_FEATURE_INDICES, 1493)
						.getCompoundOrEmpty("data")
						.getCompoundOrEmpty("Features");
					if (nbtCompound.isEmpty()) {
						continue;
					}
				} catch (IOException var8) {
				}

				nbtCompound.forEach(
					(key, nbt) -> {
						if (nbt instanceof NbtCompound nbtCompoundx) {
							long l = ChunkPos.toLong(nbtCompoundx.getInt("ChunkX", 0), nbtCompoundx.getInt("ChunkZ", 0));
							NbtList nbtList = nbtCompoundx.getListOrEmpty("Children");
							if (!nbtList.isEmpty()) {
								Optional<String> optional = nbtList.getCompound(0).flatMap(child -> child.getString("id"));
								optional.map(ANCIENT_TO_OLD::get).ifPresent(id -> nbtCompoundx.putString("id", id));
							}

							nbtCompoundx.getString("id")
								.ifPresent(id -> ((Long2ObjectMap)this.featureIdToChunkNbt.computeIfAbsent(id, featureId -> new Long2ObjectOpenHashMap())).put(l, nbtCompoundx));
						}
					}
				);
				String string2 = string + "_index";
				ChunkUpdateState chunkUpdateState = persistentStateManager.getOrCreate(ChunkUpdateState.createStateType(string2));
				if (chunkUpdateState.getAll().isEmpty()) {
					ChunkUpdateState chunkUpdateState2 = new ChunkUpdateState();
					this.updateStates.put(string, chunkUpdateState2);
					nbtCompound.forEach((key, nbt) -> {
						if (nbt instanceof NbtCompound nbtCompoundx) {
							chunkUpdateState2.add(ChunkPos.toLong(nbtCompoundx.getInt("ChunkX", 0), nbtCompoundx.getInt("ChunkZ", 0)));
						}
					});
				} else {
					this.updateStates.put(string, chunkUpdateState);
				}
			}
		}
	}

	public static FeatureUpdater create(RegistryKey<World> world, @Nullable PersistentStateManager persistentStateManager) {
		if (world == World.OVERWORLD) {
			return new FeatureUpdater(
				persistentStateManager,
				ImmutableList.of("Monument", "Stronghold", "Village", "Mineshaft", "Temple", "Mansion"),
				ImmutableList.of("Village", "Mineshaft", "Mansion", "Igloo", "Desert_Pyramid", "Jungle_Pyramid", "Swamp_Hut", "Stronghold", "Monument")
			);
		} else if (world == World.NETHER) {
			List<String> list = ImmutableList.of("Fortress");
			return new FeatureUpdater(persistentStateManager, list, list);
		} else if (world == World.END) {
			List<String> list = ImmutableList.of("EndCity");
			return new FeatureUpdater(persistentStateManager, list, list);
		} else {
			throw new RuntimeException(String.format(Locale.ROOT, "Unknown dimension type : %s", world));
		}
	}
}
