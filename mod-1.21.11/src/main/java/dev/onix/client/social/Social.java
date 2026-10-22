package dev.onix.client.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import dev.onix.client.OnixClient;
import dev.onix.client.gui.Notifications;
import dev.onix.client.network.OnixPresence;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Onix social features (all through the Onix server): friends with online status and the server they play on,
 * chat with friends, and McReal – one screenshot a day that only your friends see.
 */
public final class Social {
    private Social() {}

    public record Friend(String uuid, String name, boolean online, String server, int unread) {}
    public record Request(String uuid, String name) {}
    public record Message(String id, String from, String text, long at) {}
    public record Post(String uuid, String name, String day, String caption, long at) {}

    public static volatile List<Friend> friends = List.of();
    public static volatile List<Request> incoming = List.of();
    public static volatile List<Request> outgoing = List.of();
    public static volatile String error = null;
    public static volatile boolean loaded = false;

    private static final Map<String, List<Message>> CHATS = new HashMap<>();
    private static long lastFriends;
    private static long lastInbox;
    private static long inboxAfter = System.currentTimeMillis();
    private static boolean busyFriends;
    private static boolean busyInbox;

    // ------------------------------------------------------------------ background (every client tick)

    /** Keeps the friend list fresh and shows new messages as notifications. */
    public static void tick(boolean menuOpen) {
        long now = System.currentTimeMillis();
        if (!busyFriends && now - lastFriends > (menuOpen ? 8_000 : 60_000)) refreshFriends();
        if (!busyInbox && now - lastInbox > 20_000) {
            lastInbox = now;
            busyInbox = true;
            JsonObject body = new JsonObject();
            body.addProperty("after", inboxAfter);
            OnixPresence.call("/v1/chat/inbox", body, r -> {
                busyInbox = false;
                if (r.status() != 200 || r.json() == null) return;
                for (JsonElement e : arr(r.json(), "messages")) {
                    JsonObject m = e.getAsJsonObject();
                    long at = m.get("at").getAsLong();
                    inboxAfter = Math.max(inboxAfter, at);
                    String from = str(m, "from");
                    if (from.equals(openChat)) continue; // the chat is open: it shows up there
                    Notifications.show(str(m, "name"), str(m, "text"));
                }
            });
        }
        if (openChat != null && now - lastChatPoll > 3_000) pollChat();
    }

    public static void refreshFriends() {
        lastFriends = System.currentTimeMillis();
        busyFriends = true;
        OnixPresence.call("/v1/friends", new JsonObject(), r -> {
            busyFriends = false;
            if (r.status() != 200 || r.json() == null) {
                error = r.error();
                return;
            }
            error = null;
            loaded = true;
            List<Friend> f = new ArrayList<>();
            for (JsonElement e : arr(r.json(), "friends")) {
                JsonObject o = e.getAsJsonObject();
                f.add(new Friend(str(o, "uuid"), str(o, "name"), o.has("online") && o.get("online").getAsBoolean(), str(o, "server"),
                        o.has("unread") ? o.get("unread").getAsInt() : 0));
            }
            friends = f;
            incoming = requests(r.json(), "incoming");
            outgoing = requests(r.json(), "outgoing");
        });
    }

    // ------------------------------------------------------------------ friend actions

    public static void add(String name, Consumer<String> done) {
        JsonObject body = new JsonObject();
        body.addProperty("name", name.trim());
        OnixPresence.call("/v1/friends/add", body, r -> {
            if (r.status() != 200) {
                done.accept(r.error());
                return;
            }
            String state = str(r.json(), "state");
            Notifications.show(state.equals("friends") ? "Friend added" : "Request sent", str(r.json(), "name"));
            refreshFriends();
            done.accept(null);
        });
    }

    public static void accept(String uuid) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid);
        OnixPresence.call("/v1/friends/accept", body, r -> refreshFriends());
    }

    /** Removes a friend, declines a request or cancels your own. */
    public static void remove(String uuid) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid);
        OnixPresence.call("/v1/friends/remove", body, r -> refreshFriends());
        CHATS.remove(uuid);
        if (uuid.equals(openChat)) openChat = null;
    }

    /** Joins the server a friend plays on. */
    public static void join(Friend f, Screen parent) {
        if (f.server().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.disconnectWithSavingScreen();
        ServerData data = new ServerData(f.name() + "'s server", f.server(), ServerData.Type.OTHER);
        ConnectScreen.startConnecting(parent, mc, ServerAddress.parseString(f.server()), data, false, null);
    }

    // ------------------------------------------------------------------ chat

    public static volatile String openChat = null;
    private static long lastChatPoll;

    public static List<Message> chat(String uuid) {
        synchronized (CHATS) {
            return new ArrayList<>(CHATS.getOrDefault(uuid, List.of()));
        }
    }

    public static void openChat(String uuid) {
        openChat = uuid;
        lastChatPoll = 0;
        pollChat();
    }

    private static void pollChat() {
        String with = openChat;
        if (with == null) return;
        lastChatPoll = System.currentTimeMillis();
        JsonObject body = new JsonObject();
        body.addProperty("with", with);
        OnixPresence.call("/v1/chat/history", body, r -> {
            if (r.status() != 200 || r.json() == null) return;
            List<Message> list = new ArrayList<>();
            for (JsonElement e : arr(r.json(), "messages")) {
                JsonObject m = e.getAsJsonObject();
                list.add(new Message(str(m, "id"), str(m, "from"), str(m, "text"), m.get("at").getAsLong()));
            }
            synchronized (CHATS) {
                CHATS.put(with, list);
            }
        });
    }

    public static void send(String to, String text, Consumer<String> done) {
        String t = text.trim();
        if (t.isEmpty()) return;
        JsonObject body = new JsonObject();
        body.addProperty("to", to);
        body.addProperty("text", t);
        OnixPresence.call("/v1/chat/send", body, r -> {
            if (r.status() != 200) {
                done.accept(r.error());
                return;
            }
            JsonObject m = r.json().getAsJsonObject("message");
            synchronized (CHATS) {
                List<Message> list = new ArrayList<>(CHATS.getOrDefault(to, List.of()));
                list.add(new Message(str(m, "id"), str(m, "from"), str(m, "text"), m.get("at").getAsLong()));
                CHATS.put(to, list);
            }
            done.accept(null);
        });
    }

    // ------------------------------------------------------------------ McReal

    public static volatile List<Post> posts = List.of();
    public static volatile boolean postedToday = false;
    public static volatile String mcrealStatus = null;
    private static final Map<String, Identifier> IMAGES = new HashMap<>();
    private static final Map<String, int[]> SIZES = new HashMap<>();
    private static final java.util.Set<String> LOADING = new java.util.HashSet<>();

    public static void refreshFeed() {
        OnixPresence.call("/v1/mcreal/feed", new JsonObject(), r -> {
            if (r.status() != 200 || r.json() == null) {
                mcrealStatus = r.error();
                return;
            }
            mcrealStatus = null;
            postedToday = r.json().has("posted") && r.json().get("posted").getAsBoolean();
            List<Post> list = new ArrayList<>();
            for (JsonElement e : arr(r.json(), "posts")) {
                JsonObject o = e.getAsJsonObject();
                list.add(new Post(str(o, "uuid"), str(o, "name"), str(o, "day"), str(o, "caption"), o.get("at").getAsLong()));
            }
            posts = list;
        });
    }

    /** The picture of a post as a texture (null while it loads); size in {width, height}. */
    public static Identifier image(Post p, int[] size) {
        String key = p.uuid() + "/" + p.day();
        Identifier id = IMAGES.get(key);
        if (id != null) {
            int[] s = SIZES.get(key);
            size[0] = s[0];
            size[1] = s[1];
            return id;
        }
        if (LOADING.add(key)) {
            JsonObject body = new JsonObject();
            body.addProperty("uuid", p.uuid());
            body.addProperty("day", p.day());
            OnixPresence.call("/v1/mcreal/image", body, r -> {
                if (r.status() != 200 || r.json() == null) return;
                try {
                    byte[] bytes = Base64.getDecoder().decode(str(r.json(), "image"));
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                    if (img == null) return;
                    NativeImage ni = new NativeImage(img.getWidth(), img.getHeight(), false);
                    for (int y = 0; y < img.getHeight(); y++) {
                        for (int x = 0; x < img.getWidth(); x++) ni.setPixel(x, y, 0xFF000000 | img.getRGB(x, y));
                    }
                    Identifier tex = Identifier.fromNamespaceAndPath("onix", "mcreal/" + p.uuid() + "_" + p.day().replace("-", ""));
                    Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(() -> "onix mcreal", ni));
                    IMAGES.put(key, tex);
                    SIZES.put(key, new int[]{img.getWidth(), img.getHeight()});
                } catch (Exception e) {
                    OnixClient.LOGGER.warn("McReal image could not be read", e);
                }
            });
        }
        return null;
    }

    /**
     * Takes today's McReal: hides the HUD for a moment, grabs the frame, shrinks it to 640 px and sends it as JPEG.
     * Call with no screen open (the menu closes first).
     */
    public static void takeMcReal(String caption) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            Notifications.show("McReal", "Join a world first – McReal shows where you play.");
            return;
        }
        boolean hidden = mc.options.hideGui;
        mc.options.hideGui = true;
        pendingShot = () -> Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
            mc.options.hideGui = hidden;
            try (image) {
                byte[] jpeg = encode(image, 640);
                JsonObject body = new JsonObject();
                body.addProperty("image", Base64.getEncoder().encodeToString(jpeg));
                body.addProperty("caption", caption == null ? "" : caption);
                Notifications.show("McReal", "Sending…");
                OnixPresence.call("/v1/mcreal/post", body, r -> {
                    if (r.status() == 200) {
                        Notifications.show("McReal shared", "Your friends can see it today.");
                        refreshFeed();
                    } else {
                        Notifications.show("McReal", r.error());
                    }
                });
            } catch (Exception e) {
                OnixClient.LOGGER.warn("McReal failed", e);
                Notifications.show("McReal", "The screenshot could not be taken.");
            }
        });
        shotDelay = 3;
    }

    private static Runnable pendingShot;
    private static int shotDelay;

    /** Called every client tick: the screenshot is taken a few frames after the HUD was hidden. */
    public static void tickShot() {
        if (pendingShot != null && --shotDelay <= 0) {
            Runnable r = pendingShot;
            pendingShot = null;
            r.run();
        }
    }

    private static byte[] encode(NativeImage image, int maxWidth) throws java.io.IOException {
        int w = image.getWidth();
        int h = image.getHeight();
        float scale = Math.min(1f, maxWidth / (float) w);
        int ow = Math.max(1, Math.round(w * scale));
        int oh = Math.max(1, Math.round(h * scale));
        BufferedImage out = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < oh; y++) {
            int sy = Math.min(h - 1, (int) (y / scale));
            for (int x = 0; x < ow; x++) {
                int sx = Math.min(w - 1, (int) (x / scale));
                out.setRGB(x, y, image.getPixel(sx, sy) & 0xFFFFFF);
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(out, "jpg", bytes);
        return bytes.toByteArray();
    }

    // ------------------------------------------------------------------ json helpers

    private static List<Request> requests(JsonObject o, String key) {
        List<Request> out = new ArrayList<>();
        for (JsonElement e : arr(o, key)) out.add(new Request(str(e.getAsJsonObject(), "uuid"), str(e.getAsJsonObject(), "name")));
        return out;
    }

    private static JsonArray arr(JsonObject o, String key) {
        return o != null && o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
    }

    private static String str(JsonObject o, String key) {
        return o != null && o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : "";
    }
}
