package dev.onix.client.module.impl.combat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.TextColor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Tier Tagger: the PvP tier of a player from a public tier list (MCTiers or SubTiers) next to the name – in nametags
 * and the tab list, for example "HT3" (high tier 3) in Sword. "Best" shows the player's best tier over all modes with
 * the mode's short name. Retired players show their peak tier, marked with an R.
 *
 * Tiers are fetched once per player per session ({api}/v2/profile/{uuid}/rankings; 404 = not on the list), at most
 * four requests at a time, on background threads; failed requests are tried again after a few minutes.
 */
public class TierTaggerModule extends Module implements dev.onix.client.module.OverlayRenderer {
    private static final String[] LISTS = {"MCTiers", "SubTiers"};
    private static final Map<String, String> API = Map.of("MCTiers", "https://mctiers.net/api", "SubTiers", "https://subtiers.net/api");
    /** Mode ids on the tier lists → names in the setting (and short names next to the tier). */
    private static final String[][] MODES = {
            {"best", "Best", ""}, {"sword", "Sword", "Sword"}, {"vanilla", "Vanilla", "Van"}, {"uhc", "UHC", "UHC"},
            {"pot", "Pot", "Pot"}, {"nethop", "NethPot", "Neth"}, {"smp", "SMP", "SMP"}, {"axe", "Axe", "Axe"},
            {"mace", "Mace", "Mace"}, {"spear", "Spear", "Spear"},
    };

    private final ModeSetting list = add(new ModeSetting("list", "Tier List", LISTS));
    private final ModeSetting mode = add(new ModeSetting("mode", "Game Mode", names()));
    private final BoolSetting nametags = add(new BoolSetting("nametags", "Nametags", true));
    private final BoolSetting tabList = add(new BoolSetting("tabList", "Tab List", true));
    private final BoolSetting showMode = add(new BoolSetting("showMode", "Show Mode Name", true)
            .describe("With Best: the mode the tier is from, for example \"HT3 Sword\"."));
    private final BoolSetting self = add(new BoolSetting("self", "Show on Yourself", true));
    private final ModeSetting position = add(new ModeSetting("position", "Position", "After Name", "Before Name"));

    {
        group("Tier Card");
    }

    private final dev.onix.client.setting.KeybindSetting cardKey = add(new dev.onix.client.setting.KeybindSetting("cardKey", "Tier Card Key",
            org.lwjgl.glfw.GLFW.GLFW_KEY_C).describe("Shows every tier of the last player you hit or who hit you."));
    private final dev.onix.client.setting.NumberSetting cardSeconds = add(new dev.onix.client.setting.NumberSetting("cardSeconds", "Show Card For", 6, 2, 20, 1).unit("s"));


    private record Ranking(int tier, int pos, int peakTier, int peakPos, boolean retired) {
        int shown() { return retired && peakTier > 0 ? peakTier * 2 + peakPos : tier * 2 + pos; }
    }

    /** One player's rankings; an empty map = not on the list. */
    private record Entry(Map<String, Ranking> rankings, long fetched, boolean failed) {}

    private static final long RETRY_MS = 5 * 60 * 1000;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pending = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ExecutorService WORKERS = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "Onix Tier Tagger");
        t.setDaemon(true);
        return t;
    });

    public TierTaggerModule() {
        super("tiertagger", "Tier Tagger", "Shows the PvP tier (MCTiers) of every player next to their name – for example HT3 in Sword.", Category.COMBAT, false);
        tags("tiers", "mctiers", "subtiers", "pvp rank", "ht1", "lt3", "tier list");
    }

    private static String[] names() {
        String[] out = new String[MODES.length];
        for (int i = 0; i < MODES.length; i++) out[i] = MODES[i][1];
        return out;
    }

    @Override
    protected void onDisable() {
        cache.clear();
        cardUntil = 0;
    }

    // ------------------------------------------------------------------ the last opponent and the tier card

    private UUID opponent;
    private String opponentName;
    private long cardUntil;
    private long cardShown;
    private int lastHurt;

    @Override
    public void init() {
        // you hit a player: they are your opponent
        dev.onix.client.util.CombatTracker.onHit(entity -> {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity p && p != mc().player) remember(p);
        });
    }

    private void remember(net.minecraft.entity.player.PlayerEntity p) {
        opponent = p.getUuid();
        opponentName = p.getNameForScoreboard();
        entry(opponent);                         // look the tiers up now, so the card is ready when you press the key
    }

    @Override
    public void onTick() {
        var mc = mc();
        if (mc.player == null) return;
        // a player hit you: the closest player around you who is swinging (or simply the closest one) hit you
        int hurt = mc.player.hurtTime;
        if (hurt > lastHurt && mc.world != null) {
            net.minecraft.entity.player.PlayerEntity best = null;
            double bestScore = Double.MAX_VALUE;
            for (net.minecraft.entity.player.PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || p.isSpectator()) continue;
                double d = p.distanceTo(mc.player);
                if (d > 7) continue;
                double score = d - (p.handSwinging ? 3 : 0);
                if (score < bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
            if (best != null) remember(best);
        }
        lastHurt = hurt;
        if (cardKey.poll()) {
            long now = System.currentTimeMillis();
            if (now < cardUntil) cardUntil = 0;      // pressed again: hide
            else {
                cardUntil = now + Math.round(cardSeconds.get() * 1000);
                cardShown = now;
            }
        }
    }

    private static final String[][] CARD_MODES = {
            {"sword", "Sword"}, {"vanilla", "Vanilla"}, {"uhc", "UHC"}, {"pot", "Pot"}, {"nethop", "NethPot"},
            {"smp", "SMP"}, {"axe", "Axe"}, {"mace", "Mace"}, {"spear", "Spear"},
    };

    /** The tier card: the opponent's face and name, then one row per mode they are ranked in. */
    @Override
    public void renderOverlay(net.minecraft.client.gui.DrawContext ctx) {
        long now = System.currentTimeMillis();
        if (now >= cardUntil || mc().options.hudHidden) return;
        float in = Math.min(1f, (now - cardShown) / 180f);
        float out = Math.min(1f, (cardUntil - now) / 220f);
        float a = Math.min(in, out);
        int w = 168;
        // centred above the hotbar: the one spot no other HUD element uses
        int x = (ctx.getScaledWindowWidth() - w) / 2;
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        String status = null;
        if (opponent == null) {
            status = "Hit a player first – the last one you hit or who hit you shows here.";
        } else {
            Entry e = entry(opponent);
            if (e == null) status = opponent.version() != 4 ? "This player is on no tier list (offline account)." : "Looking up " + list.get() + "…";
            else if (e.failed) status = list.get() + " could not be reached.";
            else if (e.rankings.isEmpty()) status = "Not on " + list.get() + ".";
            else {
                for (String[] m : CARD_MODES) {
                    Ranking r = e.rankings.get(m[0]);
                    if (r == null) continue;
                    int tier = r.retired && r.peakTier > 0 ? r.peakTier : r.tier;
                    int pos = r.retired && r.peakTier > 0 ? r.peakPos : r.pos;
                    String text = (r.retired ? "R" : "") + (pos == 0 ? "H" : "L") + "T" + tier;
                    String peak = !r.retired && r.peakTier > 0 && r.peakTier * 2 + r.peakPos < r.tier * 2 + r.pos
                            ? "peak " + (r.peakPos == 0 ? "H" : "L") + "T" + r.peakTier : "";
                    rows.add(new String[]{m[1], text, String.valueOf(r.retired ? 0xFFA0A0A0 : 0xFF000000 | color(tier, pos)), peak});
                }
            }
        }
        int h = 34 + (status != null ? 26 : rows.size() * 14 + 6);
        int y = ctx.getScaledWindowHeight() - 66 - h + Math.round((1 - in) * 10);
        int alpha = Math.round(255 * a);
        dev.onix.client.gui.Theme.shadow(ctx, x, y, x + w, y + h, 10);
        dev.onix.client.gui.Theme.card(ctx, x, y, x + w, y + h, 10, dev.onix.client.gui.Theme.withAlpha(dev.onix.client.gui.Theme.SURFACE, Math.round(0xF0 * a)),
                dev.onix.client.gui.Theme.withAlpha(dev.onix.client.gui.Theme.BORDER_STRONG, alpha));
        // the opponent's face and name
        if (opponent != null) {
            try {
                var info = mc().getNetworkHandler() == null ? null : mc().getNetworkHandler().getPlayerListEntry(opponent);
                net.minecraft.client.util.SkinTextures skin = info != null ? info.getSkinTextures() : net.minecraft.client.util.DefaultSkinHelper.getSkinTextures(opponent);
                net.minecraft.client.gui.PlayerSkinDrawer.draw(ctx, skin, x + 10, y + 9, 16);
            } catch (RuntimeException ignored) {
                // no face: the name still shows
            }
        }
        int tx = x + 32;
        dev.onix.client.gui.Theme.textBold(ctx, opponent == null ? "Tier Card" : opponentName, tx, y + 9, dev.onix.client.gui.Theme.withAlpha(dev.onix.client.gui.Theme.TEXT, alpha));
        dev.onix.client.gui.Theme.small(ctx, list.get() + " · last opponent", tx, y + 20, dev.onix.client.gui.Theme.withAlpha(dev.onix.client.gui.Theme.TEXT_3, alpha));
        int ry = y + 34;
        ctx.fill(x + 8, ry - 4, x + w - 8, ry - 3, dev.onix.client.gui.Theme.withAlpha(dev.onix.client.gui.Theme.BORDER, alpha));
        if (status != null) {
            var lines = mc().textRenderer.wrapLines(net.minecraft.text.Text.literal(status), w - 20);
            int ly = ry + 2;
            for (int i = 0; i < Math.min(2, lines.size()); i++, ly += 10) {
                ctx.drawText(mc().textRenderer, lines.get(i), x + 10, ly, dev.onix.client.gui.Theme.withAlpha(dev.onix.client.gui.Theme.TEXT_2, alpha), false);
            }
            return;
        }
        for (String[] row : rows) {
            dev.onix.client.gui.Theme.text(ctx, row[0], x + 10, ry + 2, dev.onix.client.gui.Theme.withAlpha(dev.onix.client.gui.Theme.TEXT_2, alpha));
            if (!row[3].isEmpty()) {
                int pw = dev.onix.client.gui.Theme.smallWidth(row[3]);
                dev.onix.client.gui.Theme.small(ctx, row[3], x + w - 46 - pw, ry + 3, dev.onix.client.gui.Theme.withAlpha(dev.onix.client.gui.Theme.TEXT_3, alpha));
            }
            int col = (int) Long.parseLong(row[2]);
            int bw = dev.onix.client.gui.Theme.boldWidth(row[1]) + 10;
            int bx = x + w - 10 - bw;
            dev.onix.client.gui.Theme.round(ctx, bx, ry, bx + bw, ry + 12, 4, dev.onix.client.gui.Theme.withAlpha(col, Math.round(40 * a)));
            dev.onix.client.gui.Theme.textBold(ctx, row[1], bx + 5, ry + 2, dev.onix.client.gui.Theme.withAlpha(col, alpha));
            ry += 14;
        }
    }

    /** Dev test: show the card for a player with the given rankings. */
    public void debugCard(UUID id, String name) {
        opponent = id;
        opponentName = name;
        cardShown = System.currentTimeMillis();
        cardUntil = cardShown + 8000;
    }

    @Override
    public String statusLine() {
        long found = cache.values().stream().filter(e -> !e.rankings.isEmpty()).count();
        return found + " ranked player" + (found == 1 ? "" : "s") + " seen · " + list.get();
    }

    // ------------------------------------------------------------------ fetching

    private String key(UUID id) {
        return list.get() + "|" + id;
    }

    /** Dev test: rankings for a player given straight away, without the tier list. */
    public void debugPut(UUID id, String json) {
        debug.put(id, new Entry(parse(json), System.currentTimeMillis(), false));
    }

    /** Dev test: whether a player was looked up already, and whether they are ranked. */
    public String debugState(UUID id) {
        Entry e = cache.get(key(id));
        return e == null ? "pending" : e.failed ? "failed" : e.rankings.isEmpty() ? "not ranked" : "ranked " + e.rankings.keySet();
    }

    private final Map<UUID, Entry> debug = new ConcurrentHashMap<>();

    private Entry entry(UUID id) {
        if (id != null && debug.containsKey(id)) return debug.get(id);
        if (id == null || id.version() != 4) return null;   // offline-mode and NPC players are on no list
        String k = key(id);
        Entry e = cache.get(k);
        if (e != null && !(e.failed && System.currentTimeMillis() - e.fetched > RETRY_MS)) return e;
        if (pending.putIfAbsent(k, Boolean.TRUE) == null) {
            String url = API.getOrDefault(list.get(), API.get("MCTiers")) + "/v2/profile/" + id + "/rankings";
            WORKERS.execute(() -> {
                try {
                    HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                            .header("User-Agent", "OnixClient").GET().build(), HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() == 404) cache.put(k, new Entry(Map.of(), System.currentTimeMillis(), false));
                    else if (res.statusCode() == 200) cache.put(k, new Entry(parse(res.body()), System.currentTimeMillis(), false));
                    else cache.put(k, new Entry(Map.of(), System.currentTimeMillis(), true));
                } catch (Exception ex) {
                    cache.put(k, new Entry(Map.of(), System.currentTimeMillis(), true));
                } finally {
                    pending.remove(k);
                }
            });
        }
        return e;
    }

    static Map<String, Ranking> parse(String body) {
        Map<String, Ranking> out = new java.util.HashMap<>();
        JsonObject o = JsonParser.parseString(body).getAsJsonObject();
        for (var en : o.entrySet()) {
            if (!en.getValue().isJsonObject()) continue;
            JsonObject r = en.getValue().getAsJsonObject();
            int tier = num(r.get("tier"));
            if (tier <= 0) continue;
            out.put(en.getKey(), new Ranking(tier, num(r.get("pos")), num(r.get("peak_tier")), num(r.get("peak_pos")),
                    r.has("retired") && !r.get("retired").isJsonNull() && r.get("retired").getAsBoolean()));
        }
        return out;
    }

    private static int num(JsonElement e) {
        return e == null || e.isJsonNull() ? 0 : e.getAsInt();
    }

    // ------------------------------------------------------------------ the tag

    /** The tag for a player, or null when the player has no tier in the chosen mode (or it is still loading). */
    public MutableText tag(UUID id) {
        Entry e = entry(id);
        if (e == null || e.rankings.isEmpty()) return null;
        int m = Math.max(0, mode.index());
        String modeId = MODES[m][0];
        String shortName = MODES[m][2];
        Ranking r;
        if (modeId.equals("best")) {
            r = null;
            for (int i = 1; i < MODES.length; i++) {
                Ranking c = e.rankings.get(MODES[i][0]);
                if (c != null && (r == null || c.shown() < r.shown())) {
                    r = c;
                    shortName = MODES[i][2];
                }
            }
            if (!showMode.isOn()) shortName = "";
        } else {
            r = e.rankings.get(modeId);
            shortName = "";
        }
        if (r == null) return null;
        int tier = r.retired && r.peakTier > 0 ? r.peakTier : r.tier;
        int pos = r.retired && r.peakTier > 0 ? r.peakPos : r.pos;
        final int rgb = r.retired ? 0xA0A0A0 : color(tier, pos);
        String text = (r.retired ? "R" : "") + (pos == 0 ? "H" : "L") + "T" + tier;
        MutableText c = Text.literal(text).styled(s -> s.withColor(TextColor.fromRgb(rgb)).withBold(false).withItalic(false));
        if (!shortName.isEmpty()) c.append(Text.literal(" " + shortName).styled(s -> s.withColor(TextColor.fromRgb(0x9A9AA6))));
        return c;
    }

    /** Gold for tier 1 down to grey for tier 5; high tiers a little brighter than low ones. */
    static int color(int tier, int pos) {
        int[][] colors = {
                {0xFFCF4A, 0xE8B33C}, // 1
                {0xE0E6F0, 0xB9C2D0}, // 2
                {0xF0A060, 0xC98048}, // 3
                {0x7FE08A, 0x5FB86A}, // 4
                {0x9FC6E8, 0x8496A8}, // 5
        };
        int[] c = colors[Math.min(4, Math.max(0, tier - 1))];
        return pos == 0 ? c[0] : c[1];
    }

    private Text attach(UUID id, Text name) {
        if (!isEnabled()) return name;
        if (!self.isOn() && mc().getSession() != null && id.equals(mc().getSession().getUuidOrNull())) return name;
        MutableText t = tag(id);
        if (t == null) return name;
        MutableText sep = Text.literal(" | ").styled(s -> s.withColor(TextColor.fromRgb(0x5A5A64)));
        return position.is("Before Name")
                ? Text.empty().append(t).append(sep).append(name)
                : Text.empty().append(name).append(sep).append(t);
    }

    /** Nametags (EntityRendererMixin). */
    public Text decorateNametag(Entity entity, Text text) {
        if (!isEnabled() || !nametags.isOn() || !(entity instanceof PlayerEntity player)) return text;
        return attach(player.getUuid(), text);
    }

    /** Tab list names (PlayerListHudMixin). */
    public Text decorateTabName(PlayerListEntry entry, Text name) {
        if (!isEnabled() || !tabList.isOn() || entry == null) return name;
        return attach(entry.getProfile().getId(), name);
    }
}
