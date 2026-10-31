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
                    OnixClient.LOGGER.info("Lyrics for {}: {}", key, lines.isEmpty() ? "none with timing on LRCLIB" : lines.size() + " lines");
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
        if (synced == null) synced = best(get("https://lrclib.net/api/search?q=" + enc(clean + " " + artist)), t);
        return synced == null ? List.of() : parse(synced, t.durationMs());
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
