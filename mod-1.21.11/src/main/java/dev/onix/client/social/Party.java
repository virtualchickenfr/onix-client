package dev.onix.client.social;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.onix.client.OnixClient;
import dev.onix.client.gui.Notifications;
import dev.onix.client.network.OnixPresence;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

/**
 * Parties: invite friends; when the leader joins a server or opens a world, everyone in the party follows.
 * Servers are joined directly; the leader's own world is opened to LAN and reached through the Onix relay
 * ({@link PartyRelay}), so it works without port forwarding.
 */
public final class Party {
    private Party() {}

    public record Member(String uuid, String name, boolean leader, boolean online) {}
    public record Invite(String party, String from) {}

    public static volatile String id;
    public static volatile String leader = "";
    public static volatile List<Member> members = List.of();
    public static volatile Set<String> invited = Set.of();
    public static volatile List<Invite> invites = List.of();
    public static volatile String targetKind = "";
    public static volatile String targetValue = "";
    public static volatile int targetSeq;

    private static boolean busy;
    private static long lastPoll;
    private static boolean firstLoad = true;
    private static int followedSeq = -1;
    private static boolean followNext;
    private static String sentTarget;
    private static final Set<String> announced = new HashSet<>();

    public static boolean inParty() {
        return id != null;
    }

    public static boolean isLeader() {
        Minecraft mc = Minecraft.getInstance();
        return id != null && mc.getUser().getProfileId() != null
                && leader.equals(mc.getUser().getProfileId().toString().replace("-", ""));
    }

    public static boolean isMember(String uuid) {
        for (Member m : members) if (m.uuid().equals(uuid)) return true;
        return false;
    }

    // ------------------------------------------------------------------ every client tick

    public static void tick() {
        long now = System.currentTimeMillis();
        long every = id != null || !invites.isEmpty() ? 3_000 : 10_000;
        if (!busy && now - lastPoll > every) refresh();
        if (isLeader()) leaderTick();
        else PartyRelay.stopHost();
    }

    public static void refresh() {
        lastPoll = System.currentTimeMillis();
        busy = true;
        OnixPresence.call("/v1/party", new JsonObject(), r -> {
            busy = false;
            if (r.status() == 200 && r.json() != null) apply(r.json());
        });
    }

    private static void apply(JsonObject json) {
        List<Invite> inv = new ArrayList<>();
        for (JsonElement e : arr(json, "invites")) {
            JsonObject o = e.getAsJsonObject();
            Invite i = new Invite(str(o, "party"), str(o, "from"));
            inv.add(i);
            if (announced.add(i.party() + i.from())) {
                Notifications.show("Party invite from " + i.from(), "Open the Onix menu → Friends to join.");
            }
        }
        invites = inv;
        if (!json.has("party") || json.get("party").isJsonNull()) {
            if (id != null) Notifications.show("Party", "You are no longer in a party.");
            id = null;
            members = List.of();
            invited = Set.of();
            targetKind = "";
            followedSeq = -1;
            sentTarget = null;
            return;
        }
        JsonObject p = json.getAsJsonObject("party");
        id = str(p, "id");
        leader = str(p, "leader");
        List<Member> list = new ArrayList<>();
        for (JsonElement e : arr(p, "members")) {
            JsonObject o = e.getAsJsonObject();
            list.add(new Member(str(o, "uuid"), str(o, "name"), bool(o, "leader"), bool(o, "online")));
        }
        members = list;
        Set<String> pending = new HashSet<>();
        for (JsonElement e : arr(p, "invited")) pending.add(e.getAsString());
        invited = pending;
        JsonObject t = p.getAsJsonObject("target");
        targetKind = str(t, "kind");
        targetValue = str(t, "value");
        targetSeq = t.get("seq").getAsInt();
        if (isLeader()) {
            followedSeq = targetSeq;
        } else if (firstLoad && !followNext) {
            followedSeq = targetSeq; // don't pull anyone onto a server just because the game started
        } else if (targetSeq != followedSeq || followNext) {
            followedSeq = targetSeq;
            follow();
        }
        followNext = false;
        firstLoad = false;
    }

    // ------------------------------------------------------------------ leader: tell the party where you play

    private static void leaderTick() {
        Minecraft mc = Minecraft.getInstance();
        String kind = "";
        String value = "";
        if (mc.level != null && mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            kind = "world";
        } else if (mc.level != null && mc.getCurrentServer() != null && !mc.isLocalServer() && !PartyRelay.isGuestAddress(mc.getCurrentServer().ip)) {
            kind = "server";
            value = mc.getCurrentServer().ip;
        }
        if (kind.equals("world") && members.size() > 1) {
            PartyRelay.startHost();
            if (!PartyRelay.hostReady()) return; // tell the party once the world can be reached
        } else {
            PartyRelay.stopHost();
        }
        String key = kind + "|" + value;
        if (key.equals(sentTarget)) return;
        sentTarget = key;
        JsonObject body = new JsonObject();
        body.addProperty("kind", kind);
        body.addProperty("value", value);
        OnixPresence.call("/v1/party/target", body, r -> {
            if (r.status() != 200) sentTarget = null;
        });
    }

    // ------------------------------------------------------------------ members: follow the leader

    private static String leaderName() {
        for (Member m : members) if (m.leader()) return m.name();
        return "the leader";
    }

    private static void follow() {
        Minecraft mc = Minecraft.getInstance();
        if (targetKind.equals("server")) {
            if (mc.getCurrentServer() != null && mc.getCurrentServer().ip.equalsIgnoreCase(targetValue)) return;
            Notifications.show("Party", "Following " + leaderName() + " to " + targetValue);
            connect(targetValue, leaderName() + "'s server");
        } else if (targetKind.equals("world")) {
            if (mc.getCurrentServer() != null && PartyRelay.isGuestAddress(mc.getCurrentServer().ip)) return;
            Notifications.show("Party", "Joining " + leaderName() + "'s world…");
            PartyRelay.joinWorld(port -> connect("127.0.0.1:" + port, leaderName() + "'s world"),
                    error -> Notifications.show("Party", "Could not join the world: " + error));
        }
    }

    static void connect(String address, String name) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.level != null) mc.disconnectWithSavingScreen();
            ServerData data = new ServerData(name, address, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(address), data, false, null);
        });
    }

    // ------------------------------------------------------------------ actions

    public static void invite(String uuid, String name) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid);
        OnixPresence.call("/v1/party/invite", body, r -> {
            if (r.status() == 200) Notifications.show("Party", name + " is invited");
            else Notifications.show("Party", r.error());
            refresh();
        });
    }

    public static void accept(Invite invite) {
        JsonObject body = new JsonObject();
        body.addProperty("party", invite.party());
        followNext = true; // go where the leader is right away
        OnixPresence.call("/v1/party/accept", body, r -> {
            if (r.status() == 200 && r.json() != null) {
                apply(r.json());
                Notifications.show("Party", "You joined " + invite.from() + "'s party");
            } else {
                followNext = false;
                Notifications.show("Party", r.error());
                refresh();
            }
        });
    }

    public static void decline(Invite invite) {
        JsonObject body = new JsonObject();
        body.addProperty("party", invite.party());
        OnixPresence.call("/v1/party/decline", body, r -> {
            if (r.status() == 200 && r.json() != null) apply(r.json());
        });
    }

    public static void leave() {
        PartyRelay.stopHost();
        OnixPresence.call("/v1/party/leave", new JsonObject(), r -> {
            if (r.status() == 200 && r.json() != null) apply(r.json());
        });
    }

    public static void kick(String uuid) {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid);
        OnixPresence.call("/v1/party/kick", body, r -> {
            if (r.status() == 200 && r.json() != null) apply(r.json());
        });
    }

    // ------------------------------------------------------------------ json

    private static Iterable<JsonElement> arr(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : List.of();
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }

    private static boolean bool(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsBoolean();
    }
}
