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
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
    private static final Pattern TIME = Pattern.compile("\\[(\\d+):(\\d+(?:[.,]\\d+)?)]");

    private Lyrics() {}

    /** Synced lines of a track, null while loading, an empty list when there are none. */
    public static List<Line> of(NowPlaying.Track t) {
        String key = t.key();
        List<Line> cached = CACHE.get(key);
        if (cached != null) return cached;
        if (LOADING.putIfAbsent(key, true) == null) {
            Thread th = new Thread(() -> {
                try {
                    CACHE.put(key, fetch(t));
                } catch (Exception e) {
                    OnixClient.LOGGER.warn("Lyrics for {} failed: {}", key, e.toString());
                    CACHE.put(key, List.of());
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

    private static List<Line> fetch(NowPlaying.Track t) throws Exception {
        String artist = firstArtist(t.artist());
        String q = "?track_name=" + enc(t.title()) + "&artist_name=" + enc(artist)
                + (t.album().isBlank() ? "" : "&album_name=" + enc(t.album()))
                + (t.durationMs() > 0 ? "&duration=" + Math.round(t.durationMs() / 1000.0) : "");
        JsonElement got = get("https://lrclib.net/api/get" + q);
        String synced = got != null && got.isJsonObject() ? syncedOf(got.getAsJsonObject()) : null;
        if (synced == null) {
            // looser search: same title and artist, any album, closest length
            JsonElement found = get("https://lrclib.net/api/search?track_name=" + enc(t.title()) + "&artist_name=" + enc(artist));
            if (found != null && found.isJsonArray()) {
                JsonArray arr = found.getAsJsonArray();
                long best = Long.MAX_VALUE;
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    String s = syncedOf(o);
                    if (s == null) continue;
                    long diff = o.has("duration") && t.durationMs() > 0 ? Math.abs(Math.round(o.get("duration").getAsDouble() * 1000) - t.durationMs()) : 0;
                    if (diff < best) {
                        best = diff;
                        synced = s;
                    }
                }
            }
        }
        return synced == null ? List.of() : parse(synced, t.durationMs());
    }

    private static String syncedOf(JsonObject o) {
        if (!o.has("syncedLyrics") || o.get("syncedLyrics").isJsonNull()) return null;
        String s = o.get("syncedLyrics").getAsString();
        return s.isBlank() ? null : s;
    }

    private static JsonElement get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                .header("User-Agent", "OnixClient/" + OnixClient.VERSION + " (https://github.com/virtualchickenfr/onix-client)").GET().build();
        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return null;
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
