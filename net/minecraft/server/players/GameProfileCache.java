package net.minecraft.server.players;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.GameProfileRepository;
import com.mojang.logging.LogUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class GameProfileCache {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int GAMEPROFILES_MRU_LIMIT = 1000;
	private static final int GAMEPROFILES_EXPIRATION_MONTHS = 1;
	private static boolean usesAuthentication;
	private final Map<String, GameProfileCache.GameProfileInfo> profilesByName = Maps.newConcurrentMap();
	private final Map<UUID, GameProfileCache.GameProfileInfo> profilesByUUID = Maps.newConcurrentMap();
	private final Map<String, CompletableFuture<Optional<GameProfile>>> requests = Maps.newConcurrentMap();
	private final GameProfileRepository profileRepository;
	private final Gson gson = new GsonBuilder().create();
	private final File file;
	private final AtomicLong operationCount = new AtomicLong();
	@Nullable
	private Executor executor;

	public GameProfileCache(GameProfileRepository gameProfileRepository, File file) {
		this.profileRepository = gameProfileRepository;
		this.file = file;
		Lists.reverse(this.load()).forEach(this::safeAdd);
	}

	private void safeAdd(GameProfileCache.GameProfileInfo gameProfileInfo) {
		GameProfile gameProfile = gameProfileInfo.getProfile();
		gameProfileInfo.setLastAccess(this.getNextOperation());
		this.profilesByName.put(gameProfile.getName().toLowerCase(Locale.ROOT), gameProfileInfo);
		this.profilesByUUID.put(gameProfile.getId(), gameProfileInfo);
	}

	private static Optional<GameProfile> lookupGameProfile(GameProfileRepository gameProfileRepository, String string) {
		if (!StringUtil.isValidPlayerName(string)) {
			return createUnknownProfile(string);
		}

		Optional<GameProfile> optional = gameProfileRepository.findProfileByName(string);
		return optional.isEmpty() ? createUnknownProfile(string) : optional;
	}

	private static Optional<GameProfile> createUnknownProfile(String string) {
		return usesAuthentication() ? Optional.empty() : Optional.of(UUIDUtil.createOfflineProfile(string));
	}

	public static void setUsesAuthentication(boolean bl) {
		usesAuthentication = bl;
	}

	private static boolean usesAuthentication() {
		return usesAuthentication;
	}

	public void add(GameProfile gameProfile) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(new Date());
		calendar.add(2, 1);
		Date date = calendar.getTime();
		GameProfileCache.GameProfileInfo gameProfileInfo = new GameProfileCache.GameProfileInfo(gameProfile, date);
		this.safeAdd(gameProfileInfo);
		this.save();
	}

	private long getNextOperation() {
		return this.operationCount.incrementAndGet();
	}

	public Optional<GameProfile> get(String string) {
		String string2 = string.toLowerCase(Locale.ROOT);
		GameProfileCache.GameProfileInfo gameProfileInfo = this.profilesByName.get(string2);
		boolean bl = false;
		if (gameProfileInfo != null && new Date().getTime() >= gameProfileInfo.expirationDate.getTime()) {
			this.profilesByUUID.remove(gameProfileInfo.getProfile().getId());
			this.profilesByName.remove(gameProfileInfo.getProfile().getName().toLowerCase(Locale.ROOT));
			bl = true;
			gameProfileInfo = null;
		}

		Optional<GameProfile> optional;
		if (gameProfileInfo != null) {
			gameProfileInfo.setLastAccess(this.getNextOperation());
			optional = Optional.of(gameProfileInfo.getProfile());
		} else {
			optional = lookupGameProfile(this.profileRepository, string2);
			if (optional.isPresent()) {
				this.add(optional.get());
				bl = false;
			}
		}

		if (bl) {
			this.save();
		}

		return optional;
	}

	public CompletableFuture<Optional<GameProfile>> getAsync(String string) {
		if (this.executor == null) {
			throw new IllegalStateException("No executor");
		}

		CompletableFuture<Optional<GameProfile>> completableFuture = this.requests.get(string);
		if (completableFuture != null) {
			return completableFuture;
		}

		CompletableFuture<Optional<GameProfile>> completableFuture2 = CompletableFuture.<Optional<GameProfile>>supplyAsync(
				() -> this.get(string), Util.backgroundExecutor().forName("getProfile")
			)
			.whenCompleteAsync((optional, throwable) -> this.requests.remove(string), this.executor);
		this.requests.put(string, completableFuture2);
		return completableFuture2;
	}

	public Optional<GameProfile> get(UUID uUID) {
		GameProfileCache.GameProfileInfo gameProfileInfo = this.profilesByUUID.get(uUID);
		if (gameProfileInfo == null) {
			return Optional.empty();
		}

		gameProfileInfo.setLastAccess(this.getNextOperation());
		return Optional.of(gameProfileInfo.getProfile());
	}

	public void setExecutor(Executor executor) {
		this.executor = executor;
	}

	public void clearExecutor() {
		this.executor = null;
	}

	private static DateFormat createDateFormat() {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT);
	}

	public List<GameProfileCache.GameProfileInfo> load() {
		List<GameProfileCache.GameProfileInfo> list = Lists.newArrayList();

		try (Reader reader = Files.newReader(this.file, StandardCharsets.UTF_8)) {
			JsonArray jsonArray = this.gson.fromJson(reader, JsonArray.class);
			if (jsonArray == null) {
				return list;
			}

			DateFormat dateFormat = createDateFormat();
			jsonArray.forEach(jsonElement -> readGameProfile(jsonElement, dateFormat).ifPresent(list::add));
		} catch (FileNotFoundException var7) {
		} catch (IOException | JsonParseException exception) {
			LOGGER.warn("Failed to load profile cache {}", this.file, exception);
		}

		return list;
	}

	public void save() {
		JsonArray jsonArray = new JsonArray();
		DateFormat dateFormat = createDateFormat();
		this.getTopMRUProfiles(1000).forEach(gameProfileInfo -> jsonArray.add(writeGameProfile(gameProfileInfo, dateFormat)));
		String string = this.gson.toJson(jsonArray);

		try (Writer writer = Files.newWriter(this.file, StandardCharsets.UTF_8)) {
			writer.write(string);
		} catch (IOException var9) {
		}
	}

	private Stream<GameProfileCache.GameProfileInfo> getTopMRUProfiles(int i) {
		return ImmutableList.copyOf(this.profilesByUUID.values())
			.stream()
			.sorted(Comparator.comparing(GameProfileCache.GameProfileInfo::getLastAccess).reversed())
			.limit(i);
	}

	private static JsonElement writeGameProfile(GameProfileCache.GameProfileInfo gameProfileInfo, DateFormat dateFormat) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("name", gameProfileInfo.getProfile().getName());
		jsonObject.addProperty("uuid", gameProfileInfo.getProfile().getId().toString());
		jsonObject.addProperty("expiresOn", dateFormat.format(gameProfileInfo.getExpirationDate()));
		return jsonObject;
	}

	private static Optional<GameProfileCache.GameProfileInfo> readGameProfile(JsonElement jsonElement, DateFormat dateFormat) {
		if (jsonElement.isJsonObject()) {
			JsonObject jsonObject = jsonElement.getAsJsonObject();
			JsonElement jsonElement2 = jsonObject.get("name");
			JsonElement jsonElement3 = jsonObject.get("uuid");
			JsonElement jsonElement4 = jsonObject.get("expiresOn");
			if (jsonElement2 != null && jsonElement3 != null) {
				String string = jsonElement3.getAsString();
				String string2 = jsonElement2.getAsString();
				Date date = null;
				if (jsonElement4 != null) {
					try {
						date = dateFormat.parse(jsonElement4.getAsString());
					} catch (ParseException var12) {
					}
				}

				if (string2 != null && string != null && date != null) {
					UUID uUID;
					try {
						uUID = UUID.fromString(string);
					} catch (Throwable throwable) {
						return Optional.empty();
					}

					return Optional.of(new GameProfileCache.GameProfileInfo(new GameProfile(uUID, string2), date));
				} else {
					return Optional.empty();
				}
			} else {
				return Optional.empty();
			}
		} else {
			return Optional.empty();
		}
	}

	static class GameProfileInfo {
		private final GameProfile profile;
		final Date expirationDate;
		private volatile long lastAccess;

		GameProfileInfo(GameProfile gameProfile, Date date) {
			this.profile = gameProfile;
			this.expirationDate = date;
		}

		public GameProfile getProfile() {
			return this.profile;
		}

		public Date getExpirationDate() {
			return this.expirationDate;
		}

		public void setLastAccess(long l) {
			this.lastAccess = l;
		}

		public long getLastAccess() {
			return this.lastAccess;
		}
	}
}
