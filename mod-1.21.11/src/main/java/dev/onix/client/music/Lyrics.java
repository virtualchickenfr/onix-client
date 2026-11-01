package dev.onix.client.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.onix.client.OnixClient;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Time-synced lyrics from LRCLIB (lrclib.net – free and open, no account). Lines come with their start time; the
 * words inside a line are spread over it by length, so the current word can be highlighted.
 */
public final class Lyrics {
    public record Word(String text, long startMs) {}

    public record Line(long startMs, long endMs, String text, List<Word> words) {}

    /** Result per song; empty list = no synced lyrics found. */
    private static final Map<String, List<Line>> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NORMAL).build();
    /** Why the last song has no lyrics ("" = none found), for the bar. */
    private static final Map<String, String> PROBLEM = new ConcurrentHashMap<>();
    /** Songs whose request failed (network): asked again after this time. */
    private static final Map<String, Long> RETRY_AT = new ConcurrentHashMap<>();
    private static final Pattern TIME = Pattern.compile("\\[(\\d+):(\\d+(?:[.,]\\d+)?)]");
    /** Where the lyrics of a song came from ("LRCLIB", "NetEase", "timing estimated"). */
    private static final Map<String, String> SOURCE = new ConcurrentHashMap<>();
    /** Credit lines some sources put in front of the lyrics ("作词 : …", "Producer: …"). */
    private static final Pattern CREDIT = Pattern.compile("(?i)^\\s*(作词|作曲|编曲|制作人|制作|混音|母带|和声|吉他|贝斯|鼓|监制|出品|lyrics by|composed by|produced by|written by|arranged by|mixed by|mastered by|composer|lyricist|producer)\\s*[:：].*");
    /** Unsynced lyrics may be used, their timing spread over the song. Set by the module. */
    public static volatile boolean allowUnsynced = true;

    private Lyrics() {}

    /** Synced lines of a track, null while loading, an empty list when there are none. */
    public static List<Line> of(NowPlaying.Track t) {
        String key = t.key();
        List<Line> cached = CACHE.get(key);
        if (cached != null) {
            Long retry = RETRY_AT.get(key);
            if (retry == null || System.currentTimeMillis() < retry) return cached;
            CACHE.remove(key);
            RETRY_AT.remove(key);
        }
        if (LOADING.putIfAbsent(key, true) == null) {
            Thread th = new Thread(() -> {
                try {
                    List<Line> lines = fetch(t);
                    CACHE.put(key, lines);
                    PROBLEM.put(key, "");
                    OnixClient.LOGGER.info("Lyrics for {}: {}", key, lines.isEmpty() ? "none found" : lines.size() + " lines from " + SOURCE.get(key));
                } catch (Exception e) {
                    OnixClient.LOGGER.warn("Lyrics for {} failed: {}", key, e.toString());
                    PROBLEM.put(key, "Lyrics server not reachable");
                    CACHE.put(key, List.of());
                    RETRY_AT.put(key, System.currentTimeMillis() + 20_000);
                } finally {
                    LOADING.remove(key);
                }
            }, "Onix Lyrics");
            th.setDaemon(true);
            th.start();
        }
        return null;
    }

    /** The line playing at time ms (index), or -1 before the first one. */
    public static int lineAt(List<Line> lines, long ms) {
        int found = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startMs() <= ms) found = i;
            else break;
        }
        return found;
    }

    /** The word of a line playing at time ms. */
    public static int wordAt(Line line, long ms) {
        int found = 0;
        for (int i = 0; i < line.words().size(); i++) if (line.words().get(i).startMs() <= ms) found = i;
        return found;
    }

    /** Why there are no lyrics for this song ("" or null = simply none found). */
    public static String problem(NowPlaying.Track t) {
        return PROBLEM.get(t.key());
    }

    /** Where the lyrics came from, or null. */
    public static String source(NowPlaying.Track t) {
        return SOURCE.get(t.key());
    }

    /**
     * Tries several ways, from exact to loose: the exact song (title, artist, album, length), then a search by title
     * and artist, then a search with a cleaned title ("Song - Remastered 2011 (feat. X)" → "Song"), then free text.
     */
    private static List<Line> fetch(NowPlaying.Track t) throws Exception {
        String artist = firstArtist(t.artist());
        String title = t.title();
        String clean = cleanTitle(title);
        long seconds = Math.round(t.durationMs() / 1000.0);
        String synced = null;
        JsonElement got = get("https://lrclib.net/api/get?track_name=" + enc(title) + "&artist_name=" + enc(artist)
                + (t.album().isBlank() ? "" : "&album_name=" + enc(t.album())) + (seconds > 0 ? "&duration=" + seconds : ""));
        if (got != null && got.isJsonObject()) synced = syncedOf(got.getAsJsonObject());
        if (synced == null) synced = best(get("https://lrclib.net/api/search?track_name=" + enc(title) + "&artist_name=" + enc(artist)), t);
        if (synced == null && !clean.equals(title)) synced = best(get("https://lrclib.net/api/search?track_name=" + enc(clean) + "&artist_name=" + enc(artist)), t);
        JsonElement loose = null;
        if (synced == null) {
            loose = get("https://lrclib.net/api/search?q=" + enc(clean + " " + artist));
            synced = best(loose, t);
        }
        if (synced != null) {
            SOURCE.put(t.key(), "LRCLIB");
            return parse(synced, t.durationMs());
        }
        // NetEase Cloud Music has synced lyrics for many songs LRCLIB is missing
        try {
            String netease = netease(clean, artist, t.durationMs());
            if (netease != null) {
                List<Line> lines = parse(netease, t.durationMs());
                if (!lines.isEmpty()) {
                    SOURCE.put(t.key(), "NetEase");
                    return lines;
                }
            }
        } catch (Exception e) {
            OnixClient.LOGGER.info("NetEase lyrics for {}: {}", t.key(), e.toString());
        }
        // last resort: lyrics without timing, spread over the song
        if (allowUnsynced) {
            String plain = plainOf(got);
            if (plain == null) plain = plainBest(loose != null ? loose : get("https://lrclib.net/api/search?q=" + enc(clean + " " + artist)), t);
            if (plain != null && t.durationMs() > 0) {
                SOURCE.put(t.key(), "timing estimated");
                return estimate(plain, t.durationMs());
            }
        }
        return List.of();
    }

    private static String plainOf(JsonElement e) {
        if (e == null || !e.isJsonObject()) return null;
        JsonObject o = e.getAsJsonObject();
        if (!o.has("plainLyrics") || o.get("plainLyrics").isJsonNull()) return null;
        String s = o.get("plainLyrics").getAsString();
        return s.isBlank() ? null : s;
    }

    private static String plainBest(JsonElement found, NowPlaying.Track t) {
        if (found == null || !found.isJsonArray()) return null;
        for (JsonElement e : found.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            long diff = o.has("duration") && !o.get("duration").isJsonNull() && t.durationMs() > 0
                    ? Math.abs(Math.round(o.get("duration").getAsDouble() * 1000) - t.durationMs()) : 0;
            if (diff > 15_000) continue;
            String p = plainOf(o);
            if (p != null) return p;
        }
        return null;
    }

    /** Lines without timing: spread from 8 % to 95 % of the song, longer lines get more time. */
    static List<Line> estimate(String plain, long durationMs) {
        List<String> texts = new ArrayList<>();
        for (String l : plain.split("\\R")) if (!l.isBlank()) texts.add(l.trim());
        if (texts.isEmpty()) return List.of();
        long from = durationMs * 8 / 100;
        long span = durationMs * 87 / 100;
        int total = 0;
        for (String l : texts) total += l.length() + 8;
        List<Line> out = new ArrayList<>();
        long at = from;
        for (String l : texts) {
            long len = span * (l.length() + 8) / Math.max(1, total);
            out.add(new Line(at, at + len, l, words(l, at, at + len)));
            at += len;
        }
        return Collections.unmodifiableList(out);
    }

    /** Search NetEase by title and artist, pick the closest length, return its LRC (without credit lines). */
    private static String netease(String title, String artist, long durationMs) throws Exception {
        JsonElement found = get163("https://music.163.com/api/cloudsearch/pc?type=1&limit=10&s=" + enc(title + " " + artist));
        if (found == null || !found.isJsonObject()) return null;
        JsonObject result = found.getAsJsonObject().has("result") && found.getAsJsonObject().get("result").isJsonObject()
                ? found.getAsJsonObject().getAsJsonObject("result") : null;
        if (result == null || !result.has("songs")) return null;
        long bestId = -1;
        long best = Long.MAX_VALUE;
        String wantTitle = title.toLowerCase(java.util.Locale.ROOT);
        for (JsonElement e : result.getAsJsonArray("songs")) {
            JsonObject song = e.getAsJsonObject();
            String name = song.has("name") ? song.get("name").getAsString().toLowerCase(java.util.Locale.ROOT) : "";
            if (!name.contains(wantTitle) && !wantTitle.contains(name)) continue;
            if (name.contains("instrumental") || name.contains("karaoke")) continue;
            long dt = song.has("dt") ? song.get("dt").getAsLong() : 0;
            long diff = durationMs > 0 && dt > 0 ? Math.abs(dt - durationMs) : 0;
            if (durationMs > 0 && diff > 10_000) continue;
            if (diff < best) {
                best = diff;
                bestId = song.get("id").getAsLong();
            }
        }
        if (bestId < 0) return null;
        JsonElement lyr = get163("https://music.163.com/api/song/lyric?lv=1&kv=1&tv=-1&id=" + bestId);
        if (lyr == null || !lyr.isJsonObject() || !lyr.getAsJsonObject().has("lrc")) return null;
        JsonObject lrc = lyr.getAsJsonObject().getAsJsonObject("lrc");
        if (!lrc.has("lyric") || lrc.get("lyric").isJsonNull()) return null;
        StringBuilder out = new StringBuilder();
        for (String line : lrc.get("lyric").getAsString().split("\\R")) {
            String text = line.replaceAll("^(\\[[^]]*])+", "");
            if (CREDIT.matcher(text).matches()) continue;
            out.append(line).append('\n');
        }
        String s = out.toString().trim();
        return s.contains("[") ? s : null;
    }

    private static JsonElement get163(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) OnixClient/" + OnixClient.VERSION)
                .header("Referer", "https://music.163.com/").GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() != 200) return null;
        return JsonParser.parseString(res.body());
    }

    /** The synced lyrics of the search result closest in length (within 15 s when the length is known). */
    private static String best(JsonElement found, NowPlaying.Track t) {
        if (found == null || !found.isJsonArray()) return null;
        String bestLyrics = null;
        long best = Long.MAX_VALUE;
        for (JsonElement e : found.getAsJsonArray()) {
            if (!e.isJsonObject()) continue;
            JsonObject o = e.getAsJsonObject();
            String s = syncedOf(o);
            if (s == null) continue;
            long diff = o.has("duration") && !o.get("duration").isJsonNull() && t.durationMs() > 0
                    ? Math.abs(Math.round(o.get("duration").getAsDouble() * 1000) - t.durationMs()) : 0;
            if (t.durationMs() > 0 && diff > 15_000) continue;
            if (diff < best) {
                best = diff;
                bestLyrics = s;
            }
        }
        return bestLyrics;
    }

    private static String cleanTitle(String title) {
        String s = title.replaceAll("(?i)\\s*[(\\[](feat|ft|with|prod)\\.?[^)\\]]*[)\\]]", "");
        s = s.replaceAll("(?i)\\s+-\\s+.*(remaster|version|edit|mix|live|mono|stereo|from|radio|acoustic|sped|slowed).*$", "");
        return s.trim().isEmpty() ? title : s.trim();
    }

    private static String syncedOf(JsonObject o) {
        if (!o.has("syncedLyrics") || o.get("syncedLyrics").isJsonNull()) return null;
        String s = o.get("syncedLyrics").getAsString();
        return s.isBlank() ? null : s;
    }

    private static JsonElement get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(12))
                .header("User-Agent", "OnixClient/" + OnixClient.VERSION + " (https://github.com/virtualchickenfr/onix-client)")
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (res.statusCode() == 404) return null;
        if (res.statusCode() != 200) throw new java.io.IOException("LRCLIB answered " + res.statusCode());
        return JsonParser.parseString(res.body());
    }

    static List<Line> parse(String lrc, long durationMs) {
        List<long[]> times = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            Matcher m = TIME.matcher(raw);
            List<Long> stamps = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                stamps.add(Long.parseLong(m.group(1)) * 60_000 + Math.round(Double.parseDouble(m.group(2).replace(',', '.')) * 1000));
                end = m.end();
            }
            if (stamps.isEmpty()) continue;
            String text = raw.substring(end).trim();
            for (long s : stamps) {
                times.add(new long[]{s});
                texts.add(text);
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < times.size(); i++) order.add(i);
        order.sort((a, b) -> Long.compare(times.get(a)[0], times.get(b)[0]));
        List<Line> lines = new ArrayList<>();
        for (int k = 0; k < order.size(); k++) {
            int i = order.get(k);
            long start = times.get(i)[0];
            long end = k + 1 < order.size() ? times.get(order.get(k + 1))[0] : (durationMs > start ? durationMs : start + 5000);
            String text = texts.get(i);
            if (text.isEmpty()) continue; // instrumental gap
            lines.add(new Line(start, end, text, words(text, start, end)));
        }
        return Collections.unmodifiableList(lines);
    }

    /** Spreads the words over the line by their length (sung lines rarely use the whole gap: 85 %). */
    private static List<Word> words(String text, long start, long end) {
        String[] parts = text.split("\\s+");
        long span = Math.max(300, Math.min(end - start, 8000) * 85 / 100);
        int total = 0;
        for (String p : parts) total += p.length() + 1;
        List<Word> out = new ArrayList<>();
        int done = 0;
        for (String p : parts) {
            out.add(new Word(p, start + span * done / Math.max(1, total)));
            done += p.length() + 1;
        }
        return out;
    }

    private static String firstArtist(String artist) {
        if (artist == null) return "";
        String a = artist.split(",|;| & | feat\\.? | ft\\.? ", 2)[0];
        return a.trim();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
