package dev.onix.client.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.onix.client.OnixClient;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.ProfileKeyPair;
import net.minecraft.world.entity.player.ProfilePublicKey;

/**
 * Talks to the Onix server (see /server in the repository): announces that you play with Onix,
 * asks which of the players around you do too, and carries the coin heartbeats and the shop and
 * gift requests of {@link OnixWallet}. All requests run on a background thread; the game thread
 * only reads the cached results.
 */
public final class OnixPresence {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Onix Presence");
        t.setDaemon(true);
        return t;
    });

    private static final long HEARTBEAT_MS = 60_000;
    private static final long RESULT_TTL_MS = 120_000;

    /** Players known to use Onix → until when that answer is valid. */
    private static final Map<UUID, Long> ONIX_USERS = new ConcurrentHashMap<>();
    /** Players already asked about → when to ask again. */
    private static final Map<UUID, Long> NEXT_CHECK = new ConcurrentHashMap<>();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    /** Name style, tag and custom tag text of Onix players around you (from /v1/lookup). */
    private static final Map<UUID, String[]> LOOKS = new ConcurrentHashMap<>();
    /** The look last accepted by the server, so the heartbeat only sends it when it changes. */
    private static volatile String sentLook;

    private static volatile String server = "";
    private static volatile String token;
    private static volatile String tokenFor;
    private static volatile long nextHeartbeat;
    private static volatile long nextLookup;
    private static volatile String status = "No server set";
    private static volatile boolean announcedConnect;

    private OnixPresence() {}

    public static String status() {
        return status;
    }

    /** True if the player is known to use Onix right now (not including yourself). */
    public static boolean isOnixUser(UUID id) {
        if (id == null) return false;
        Long until = ONIX_USERS.get(id);
        return until != null && until > System.currentTimeMillis();
    }

    /** {style id, tag id, custom text} of another player (entries may be null), or null. */
    public static String[] look(UUID id) {
        return isOnixUser(id) ? LOOKS.get(id) : null;
    }

    /** Your name style or tag changed: tell the server within a few seconds instead of at the next minute. */
    public static void lookChanged() {
        nextHeartbeat = Math.min(nextHeartbeat, System.currentTimeMillis() + 3000);
    }

    public static String server() {
        return server;
    }

    /**
     * Called every client tick. share = others may see your badge; lookups = ask which players around
     * you use Onix (only while the badge module is on). Heartbeats run either way, for the coins.
     */
    public static void tick(String serverUrl, boolean share, boolean lookups, int lookupSeconds) {
        String url = serverUrl == null ? "" : serverUrl.trim().replaceAll("/+$", "");
        if (!url.equals(server)) {
            server = url;
            token = null;
            ONIX_USERS.clear();
            NEXT_CHECK.clear();
            LOOKS.clear();
            sentLook = null;
            nextHeartbeat = 0;
            nextLookup = 0;
            announcedConnect = false;
            status = url.isEmpty() ? "No server set" : "Connecting…";
            OnixWallet.serverChanged();
        }
        if (url.isEmpty() || BUSY.get()) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        if (now >= nextHeartbeat) {
            nextHeartbeat = now + HEARTBEAT_MS;
            User session = mc.getUser();
            UUID self = session.getProfileId();
            String name = session.getName();
            String accessToken = session.getAccessToken();
            boolean playing = mc.level != null;
            // the server you play on, for friends' "Join" button (never LAN or singleplayer worlds)
            var current = mc.getCurrentServer();
            String addr = OnixClient.CONFIG.shareServer.isOn() && OnixClient.CONFIG.socialOn.isOn() && current != null && !current.isLan()
                    && !mc.isLocalServer() ? current.ip : "";
            JsonObject look = ownLook();
            submit(() -> heartbeat(url, self, name, accessToken, share, playing, addr, look));
            return;
        }

        if (lookups && now >= nextLookup && mc.getConnection() != null) {
            nextLookup = now + Math.max(5, lookupSeconds) * 1000L;
            List<String> ask = new ArrayList<>();
            UUID self = mc.getUser().getProfileId();
            for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
                UUID id = entry.getProfile().id();
                if (id == null || id.equals(self)) continue;
                Long next = NEXT_CHECK.get(id);
                if (next != null && next > now) continue;
                NEXT_CHECK.put(id, now + RESULT_TTL_MS / 2);
                ask.add(id.toString());
                if (ask.size() >= 400) break;
            }
            if (!ask.isEmpty()) submit(() -> lookup(url, ask));
        }
    }

    /** Tells the server we left (game closing or sharing turned off). */
    public static void leave() {
        String url = server;
        String t = token;
        if (url.isEmpty() || t == null) return;
        JsonObject body = new JsonObject();
        body.addProperty("token", t);
        try {
            post(url + "/v1/leave", body, 2);
        } catch (Exception ignored) {
            // best effort while shutting down
        }
    }

    private static void submit(Runnable job) {
        if (!BUSY.compareAndSet(false, true)) return;
        WORKER.execute(() -> {
            try {
                job.run();
            } catch (Exception e) {
                status = "Offline – " + e.getClass().getSimpleName();
                nextHeartbeat = System.currentTimeMillis() + 30_000;
            } finally {
                BUSY.set(false);
            }
        });
    }

    // ------------------------------------------------------------------ requests (worker thread)

    /** Your name style and tag as the server stores them (only what you wear, never what you try on). */
    private static JsonObject ownLook() {
        JsonObject o = new JsonObject();
        var style = dev.onix.client.cosmetics.CosmeticCatalog.item(dev.onix.client.cosmetics.Cosmetics.selected(dev.onix.client.cosmetics.Cosmetics.Slot.NAMETAG));
        var tag = dev.onix.client.cosmetics.CosmeticCatalog.item(dev.onix.client.cosmetics.Cosmetics.selected(dev.onix.client.cosmetics.Cosmetics.Slot.TAG));
        o.addProperty("style", style != null && OnixWallet.owns(style) ? style.id() : "");
        o.addProperty("tag", tag != null && OnixWallet.owns(tag) ? tag.id() : "");
        o.addProperty("text", tag != null && tag.custom() ? dev.onix.client.cosmetics.Cosmetics.tagText() : "");
        return o;
    }

    private static void heartbeat(String url, UUID self, String name, String accessToken, boolean share, boolean playing, String serverAddress, JsonObject look) {
        if (self == null) {
            status = "Not signed in";
            return;
        }
        if (!ensureToken(url, self, name, accessToken)) return;
        JsonObject body = new JsonObject();
        body.addProperty("token", token);
        body.addProperty("visible", share);
        body.addProperty("playing", playing);
        body.addProperty("social", OnixClient.CONFIG.socialOn.isOn());
        if (!serverAddress.isEmpty()) body.addProperty("server", serverAddress);
        String lookKey = look.toString();
        if (!lookKey.equals(sentLook)) body.add("look", look);
        Response r = post(url + "/v1/heartbeat", body, 6);
        if (r.status == 401) {
            token = null;
            nextHeartbeat = 0;
            return;
        }
        if (r.status != 200) {
            status = "Server error " + r.status;
            return;
        }
        if (body.has("look")) {
            sentLook = lookKey;
            if (r.json != null && r.json.has("lookError")) {
                String why = r.json.get("lookError").getAsString();
                Minecraft.getInstance().execute(() -> dev.onix.client.gui.Notifications.show("Name tag not shared", why));
            }
        }
        int count = r.json != null && r.json.has("online") ? r.json.get("online").getAsInt() : 0;
        status = "Connected · " + count + " online";
        if (r.json != null && r.json.has("wallet") && r.json.get("wallet").isJsonObject()) OnixWallet.received(r.json.getAsJsonObject("wallet"));
        if (!announcedConnect) {
            announcedConnect = true;
            OnixClient.LOGGER.info("Onix presence: connected to {}", url);
        }
    }

    private static boolean ensureToken(String url, UUID self, String name, String accessToken) {
        if (token == null || !url.equals(tokenFor)) {
            token = signIn(url, self, name, accessToken);
            tokenFor = url;
        }
        return token != null;
    }

    /** Result of {@link #call}: HTTP status (0 = no connection) and the JSON answer. */
    public record Reply(int status, JsonObject json) {
        public String error() {
            if (status == 0) return "The Onix server cannot be reached.";
            return json != null && json.has("error") ? json.get("error").getAsString() : "Server error " + status;
        }
    }

    /**
     * Sends an authenticated request (the session token is added) on the background thread and hands
     * the reply to done on the game thread. Signs in again once if the server forgot the session.
     */
    public static void call(String path, JsonObject body, java.util.function.Consumer<Reply> done) {
        String url = server;
        Minecraft mc = Minecraft.getInstance();
        User session = mc.getUser();
        UUID self = session.getProfileId();
        String name = session.getName();
        String accessToken = session.getAccessToken();
        WORKER.execute(() -> {
            Reply reply;
            if (url.isEmpty()) {
                reply = new Reply(0, null);
            } else if (self == null || !ensureToken(url, self, name, accessToken)) {
                reply = new Reply(401, error("Could not sign in to the Onix server (" + status + ")."));
            } else {
                body.addProperty("token", token);
                Response r = post(url + path, body, 10);
                if (r.status == 401) {
                    token = null;
                    if (ensureToken(url, self, name, accessToken)) {
                        body.addProperty("token", token);
                        r = post(url + path, body, 10);
                    }
                }
                reply = new Reply(r.status, r.json);
            }
            Reply result = reply;
            mc.execute(() -> done.accept(result));
        });
    }

    private static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return o;
    }

    /** Gets a session token, proving account ownership through Mojang if the server asks for it. */
    private static String signIn(String url, UUID self, String name, String accessToken) {
        JsonObject start = new JsonObject();
        start.addProperty("uuid", self.toString());
        start.addProperty("name", name);
        Response r = post(url + "/v1/session/start", start, 6);
        if (r.status != 200 || r.json == null) {
            status = "Server error " + r.status;
            return null;
        }
        if (r.json.has("token")) return r.json.get("token").getAsString();
        String serverId = r.json.get("serverId").getAsString();
        // Servers that ask Mojang (hasJoined) need the join; the hosted server checks the player certificate,
        // because Mojang refuses requests from Cloudflare. Doing both works with either.
        boolean joined;
        try {
            Minecraft.getInstance().services().sessionService().joinServer(self, accessToken, serverId);
            joined = true;
        } catch (Exception e) {
            joined = false;
        }
        JsonObject finish = new JsonObject();
        finish.addProperty("uuid", self.toString());
        finish.addProperty("serverId", serverId);
        boolean proved = addCertificateProof(finish, self, serverId);
        if (!joined && !proved) {
            status = "Could not verify account";
            nextHeartbeat = System.currentTimeMillis() + 5 * 60_000;
            return null;
        }
        Response f = post(url + "/v1/session/finish", finish, 12);
        if (f.status != 200 || f.json == null || !f.json.has("token")) {
            status = f.json != null && f.json.has("error") ? f.json.get("error").getAsString() : "Verification failed (" + f.status + ")";
            OnixClient.LOGGER.warn("Onix server sign-in failed: {}", status);
            return null;
        }
        return f.json.get("token").getAsString();
    }

    /**
     * Adds the account's player certificate (the chat signing key Mojang hands out) and a signature of the
     * challenge made with it. False when the game has no key (offline account, or Mojang did not answer).
     */
    private static boolean addCertificateProof(JsonObject finish, UUID self, String serverId) {
        try {
            Optional<ProfileKeyPair> keys = Minecraft.getInstance().getProfileKeyPairManager().prepareKeyPair().get(15, TimeUnit.SECONDS);
            if (keys.isEmpty()) return false;
            ProfileKeyPair pair = keys.get();
            ProfilePublicKey.Data data = pair.publicKey().data();
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(pair.privateKey());
            String plainUuid = self.toString().replace("-", "");
            signer.update(("onix-session:" + serverId + ":" + plainUuid).getBytes(StandardCharsets.UTF_8));
            JsonObject cert = new JsonObject();
            cert.addProperty("publicKey", Base64.getEncoder().encodeToString(data.key().getEncoded()));
            cert.addProperty("expiresAt", data.expiresAt().toEpochMilli());
            cert.addProperty("signature", Base64.getEncoder().encodeToString(data.keySignature()));
            finish.add("cert", cert);
            finish.addProperty("proof", Base64.getEncoder().encodeToString(signer.sign()));
            return true;
        } catch (Exception e) {
            OnixClient.LOGGER.warn("No player certificate for the Onix server: {}", e.toString());
            return false;
        }
    }

    private static void lookup(String url, List<String> uuids) {
        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        uuids.forEach(arr::add);
        body.add("uuids", arr);
        Response r = post(url + "/v1/lookup", body, 8);
        if (r.status != 200 || r.json == null || !r.json.has("online")) return;
        long until = System.currentTimeMillis() + RESULT_TTL_MS;
        java.util.Set<String> online = new java.util.HashSet<>();
        for (JsonElement e : r.json.getAsJsonArray("online")) online.add(e.getAsString());
        JsonObject looks = r.json.has("looks") && r.json.get("looks").isJsonObject() ? r.json.getAsJsonObject("looks") : new JsonObject();
        for (String raw : uuids) {
            UUID id = UUID.fromString(raw);
            String plain = raw.replace("-", "").toLowerCase();
            if (online.contains(plain)) ONIX_USERS.put(id, until);
            else ONIX_USERS.remove(id);
            if (looks.has(plain) && looks.get(plain).isJsonObject()) {
                JsonObject l = looks.getAsJsonObject(plain);
                LOOKS.put(id, new String[]{text(l, "style"), text(l, "tag"), text(l, "text")});
            } else {
                LOOKS.remove(id);
            }
        }
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : null;
    }

    private record Response(int status, JsonObject json) {}

    private static Response post(String url, JsonObject body, int timeoutSeconds) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("content-type", "application/json")
                    .header("user-agent", "OnixClient/" + OnixClient.VERSION)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .build();
            HttpResponse<String> res = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            JsonObject json = null;
            try {
                json = GSON.fromJson(res.body(), JsonObject.class);
            } catch (Exception ignored) {
                // non-JSON error page
            }
            return new Response(res.statusCode(), json);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Response(0, null);
        } catch (Exception e) {
            status = "Offline";
            return new Response(0, null);
        }
    }
}
