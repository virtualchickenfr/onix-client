package net.minecraft.stat;

import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.network.packet.s2c.play.StatisticsS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Util;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

public class ServerStatHandler extends StatHandler {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Codec<Map<Stat<?>, Integer>> CODEC = Codec.dispatchedMap(Registries.STAT_TYPE.getCodec(), Util.memoize(ServerStatHandler::createCodec))
		.xmap(statsByTypes -> {
			Map<Stat<?>, Integer> map = new HashMap();
			statsByTypes.forEach((type, stats) -> map.putAll(stats));
			return map;
		}, stats -> (Map)stats.entrySet().stream().collect(Collectors.groupingBy(entry -> ((Stat)entry.getKey()).getType(), Util.toMap())));
	private final MinecraftServer server;
	private final File file;
	private final Set<Stat<?>> pendingStats = Sets.<Stat<?>>newHashSet();

	private static <T> Codec<Map<Stat<?>, Integer>> createCodec(StatType<T> statType) {
		Codec<T> codec = statType.getRegistry().getCodec();
		Codec<Stat<?>> codec2 = codec.flatComapMap(
			statType::getOrCreateStat,
			stat -> stat.getType() == statType
				? DataResult.success(stat.getValue())
				: DataResult.error(() -> "Expected type " + statType + ", but got " + stat.getType())
		);
		return Codec.unboundedMap(codec2, Codec.INT);
	}

	public ServerStatHandler(MinecraftServer server, File file) {
		this.server = server;
		this.file = file;
		if (file.isFile()) {
			try {
				this.parse(server.getDataFixer(), FileUtils.readFileToString(file));
			} catch (IOException var4) {
				LOGGER.error("Couldn't read statistics file {}", file, var4);
			} catch (JsonParseException var5) {
				LOGGER.error("Couldn't parse statistics file {}", file, var5);
			}
		}
	}

	public void save() {
		try {
			FileUtils.writeStringToFile(this.file, this.asString());
		} catch (IOException var2) {
			LOGGER.error("Couldn't save stats", (Throwable)var2);
		}
	}

	@Override
	public void setStat(PlayerEntity player, Stat<?> stat, int value) {
		super.setStat(player, stat, value);
		this.pendingStats.add(stat);
	}

	private Set<Stat<?>> takePendingStats() {
		Set<Stat<?>> set = Sets.<Stat<?>>newHashSet(this.pendingStats);
		this.pendingStats.clear();
		return set;
	}

	public void parse(DataFixer dataFixer, String json) {
		try {
			JsonReader jsonReader = new JsonReader(new StringReader(json));

			label35: {
				try {
					jsonReader.setLenient(false);
					JsonElement jsonElement = Streams.parse(jsonReader);
					if (!jsonElement.isJsonNull()) {
						Dynamic<JsonElement> dynamic = new Dynamic<>(JsonOps.INSTANCE, jsonElement);
						dynamic = DataFixTypes.STATS.update(dataFixer, dynamic, NbtHelper.getDataVersion(dynamic, 1343));
						this.statMap
							.putAll(
								(Map)CODEC.parse(dynamic.get("stats").orElseEmptyMap())
									.resultOrPartial(string -> LOGGER.error("Failed to parse statistics for {}: {}", this.file, string))
									.orElse(Map.of())
							);
						break label35;
					}

					LOGGER.error("Unable to parse Stat data from {}", this.file);
				} catch (Throwable var7) {
					try {
						jsonReader.close();
					} catch (Throwable var6) {
						var7.addSuppressed(var6);
					}

					throw var7;
				}

				jsonReader.close();
				return;
			}

			jsonReader.close();
		} catch (IOException | JsonParseException var8) {
			LOGGER.error("Unable to parse Stat data from {}", this.file, var8);
		}
	}

	protected String asString() {
		JsonObject jsonObject = new JsonObject();
		jsonObject.add("stats", CODEC.encodeStart(JsonOps.INSTANCE, this.statMap).getOrThrow());
		jsonObject.addProperty("DataVersion", SharedConstants.getGameVersion().getSaveVersion().getId());
		return jsonObject.toString();
	}

	public void updateStatSet() {
		this.pendingStats.addAll(this.statMap.keySet());
	}

	public void sendStats(ServerPlayerEntity player) {
		Object2IntMap<Stat<?>> object2IntMap = new Object2IntOpenHashMap<>();

		for (Stat<?> stat : this.takePendingStats()) {
			object2IntMap.put(stat, this.getStat(stat));
		}

		player.networkHandler.sendPacket(new StatisticsS2CPacket(object2IntMap));
	}
}
