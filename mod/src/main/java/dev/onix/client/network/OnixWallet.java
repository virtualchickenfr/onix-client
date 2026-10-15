package dev.onix.client.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.onix.client.config.ConfigFiles;
import dev.onix.client.cosmetics.CosmeticCatalog;
import dev.onix.client.gui.Notifications;
import net.minecraft.client.MinecraftClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Your Onix coins and the cosmetics you own. The server is the source of truth (1 coin per minute
 * played with Onix, counted from the heartbeats); this class shows its last answer, keeps a copy per
 * account in config/onix/wallet.json for offline starts, and sends buy and gift requests.
 */
public final class OnixWallet {
    /** Last answer from the server; null fields = not known yet. */
    private static volatile boolean known;
    private static volatile long coins;
    private static volatile boolean unlimited;
    private static volatile int playMinutes;
    private static volatile long nextCoinAt;
    private static volatile Set<String> owned = Set.of();
    private static final Set<String> NOTIFIED = Collections.synchronizedSet(new HashSet<>());
    private static volatile String cacheFor;

    private OnixWallet() {}

    // ------------------------------------------------------------------ state

    /** True once the server answered (or a saved copy was loaded). */
    public static boolean known() {
        return known;
    }

    public static boolean connected() {
        return known && !OnixPresence.server().isEmpty();
    }

    public static long coins() {
        return coins;
    }

    public static boolean unlimited() {
        return unlimited;
    }

    public static int playMinutes() {
        return playMinutes;
    }

    /** Seconds until the next coin while you keep playing. */
    public static int secondsToNextCoin() {
        return (int) Math.max(0, (nextCoinAt - System.currentTimeMillis()) / 1000);
    }

    public static boolean owns(CosmeticCatalog.Item item) {
        return item != null && (item.price() == 0 || unlimited || owned.contains(item.id()));
    }

    public static int ownedCount() {
        return owned.size();
    }

    public static boolean canAfford(CosmeticCatalog.Item item) {
        return unlimited || coins >= item.price();
    }

    /** "1,234" or "∞". */
    public static String balanceText() {
        if (!known) return "–";
        return unlimited ? "∞" : String.format(java.util.Locale.ROOT, "%,d", coins);
    }

    // ------------------------------------------------------------------ server answers

    /** A wallet object from the server (heartbeat, wallet, buy, gift); may come from the worker thread. */
    static void received(JsonObject w) {
        MinecraftClient.getInstance().execute(() -> apply(w));
    }

    private static void apply(JsonObject w) {
        unlimited = w.has("unlimited") && w.get("unlimited").getAsBoolean();
        coins = w.has("coins") && !w.get("coins").isJsonNull() ? w.get("coins").getAsLong() : 0;
        playMinutes = w.has("playMinutes") ? w.get("playMinutes").getAsInt() : 0;
        if (w.has("nextCoinMs")) nextCoinAt = System.currentTimeMillis() + w.get("nextCoinMs").getAsLong();
        Set<String> set = new HashSet<>();
        if (w.has("owned")) for (JsonElement e : w.getAsJsonArray("owned")) set.add(e.getAsString());
        owned = Set.copyOf(set);
        known = true;
        if (w.has("gifts")) announceGifts(w.getAsJsonArray("gifts"));
        saveCache();
    }

    /** Shows a notification for every new gift, then tells the server they were seen. */
    private static void announceGifts(JsonArray gifts) {
        List<String> ids = new ArrayList<>();
        for (JsonElement e : gifts) {
            JsonObject g = e.getAsJsonObject();
            String id = g.get("id").getAsString();
            ids.add(id);
            if (!NOTIFIED.add(id)) continue;
            CosmeticCatalog.Item item = CosmeticCatalog.item(g.get("item").getAsString());
            String from = g.has("from") ? g.get("from").getAsString() : "Someone";
            String message = g.has("message") ? g.get("message").getAsString() : "";
            String what = item != null ? item.name() : "a cosmetic";
            Notifications.show("Gift from " + from, message.isEmpty() ? what : what + " – \"" + message + "\"");
        }
        if (ids.isEmpty()) return;
        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        ids.forEach(arr::add);
        body.add("ids", arr);
        OnixPresence.call("/v1/gifts/seen", body, r -> {
            if (r.status() == 200 && r.json() != null) apply(withoutGifts(r.json()));
        });
    }

    private static JsonObject withoutGifts(JsonObject w) {
        JsonObject copy = w.deepCopy();
        copy.remove("gifts");
        return copy;
    }

    /** The server address changed: forget the old answer until the new server replies. */
    static void serverChanged() {
        known = false;
        loadCache();
    }

    /** Asks the server for the current wallet now (the heartbeat does it every minute anyway). */
    public static void refresh() {
        OnixPresence.call("/v1/wallet", new JsonObject(), r -> {
            if (r.status() == 200 && r.json() != null) apply(r.json());
        });
    }

    // ------------------------------------------------------------------ actions

    /** Buys an item; done gets null on success or a message to show. */
    public static void buy(CosmeticCatalog.Item item, Consumer<String> done) {
        JsonObject body = new JsonObject();
        body.addProperty("item", item.id());
        OnixPresence.call("/v1/shop/buy", body, r -> {
            if (r.status() == 200 && r.json() != null) {
                apply(r.json());
                done.accept(null);
            } else {
                done.accept(r.error());
            }
        });
    }

    /** Gifts an item to another player (by Minecraft name) with an optional short message. */
    public static void gift(CosmeticCatalog.Item item, String to, String message, Consumer<String> done) {
        JsonObject body = new JsonObject();
        body.addProperty("item", item.id());
        body.addProperty("to", to.trim());
        body.addProperty("message", message);
        OnixPresence.call("/v1/gift", body, r -> {
            if (r.status() == 200 && r.json() != null) {
                apply(r.json());
                done.accept(null);
            } else {
                done.accept(r.error());
            }
        });
    }

    // ------------------------------------------------------------------ offline copy

    private static Path cacheFile() {
        return ConfigFiles.dir().resolve("wallet.json");
    }

    private static String accountKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        UUID id = mc == null || mc.getSession() == null ? null : mc.getSession().getUuidOrNull();
        return (id == null ? "offline" : id.toString()) + "@" + OnixPresence.server();
    }

    /** Loads the saved copy for the current account and server, so owned cosmetics show before the server answers. */
    public static void loadCache() {
        String key = accountKey();
        cacheFor = key;
        JsonObject all = ConfigFiles.read(cacheFile());
        if (all == null || !all.has(key) || !all.get(key).isJsonObject()) {
            owned = Set.of();
            unlimited = false;
            coins = 0;
            known = false;
            return;
        }
        JsonObject w = all.getAsJsonObject(key);
        Set<String> set = new HashSet<>();
        if (w.has("owned")) for (JsonElement e : w.getAsJsonArray("owned")) set.add(e.getAsString());
        owned = Set.copyOf(set);
        unlimited = w.has("unlimited") && w.get("unlimited").getAsBoolean();
        coins = w.has("coins") ? w.get("coins").getAsLong() : 0;
        known = true;
    }

    private static void saveCache() {
        String key = accountKey();
        if (!key.equals(cacheFor)) cacheFor = key;
        JsonObject all = ConfigFiles.read(cacheFile());
        if (all == null) all = new JsonObject();
        JsonObject w = new JsonObject();
        w.addProperty("coins", coins);
        w.addProperty("unlimited", unlimited);
        JsonArray arr = new JsonArray();
        owned.stream().sorted().forEach(arr::add);
        w.add("owned", arr);
        all.add(key, w);
        ConfigFiles.write(cacheFile(), all);
    }
}
