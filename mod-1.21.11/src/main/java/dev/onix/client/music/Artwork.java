package dev.onix.client.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.onix.client.OnixClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * The colours of a song's cover (found through the iTunes search, free and without a key): a vivid main colour and a
 * second one, for the "Album" colour mode.
 */
public final class Artwork {
    /** {main, second} ARGB; {0, 0} = none found. */
    private static final Map<String, int[]> COLORS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LOADING = new ConcurrentHashMap<>();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private Artwork() {}

    /** The cover colours, or null while loading / when there is no cover. */
    public static int[] colors(NowPlaying.Track t) {
        String key = t.key();
        int[] c = COLORS.get(key);
        if (c != null) return c[0] == 0 ? null : c;
        if (LOADING.putIfAbsent(key, true) == null) {
            Thread th = new Thread(() -> {
                try {
                    COLORS.put(key, load(t));
                } catch (Exception e) {
                    OnixClient.LOGGER.info("Cover of {}: {}", key, e.toString());
                    COLORS.put(key, new int[]{0, 0});
                } finally {
                    LOADING.remove(key);
                }
            }, "Onix Artwork");
            th.setDaemon(true);
            th.start();
        }
        return null;
    }

    private static int[] load(NowPlaying.Track t) throws Exception {
        String term = URLEncoder.encode(t.title() + " " + t.artist(), StandardCharsets.UTF_8);
        HttpResponse<String> res = HTTP.send(HttpRequest.newBuilder(URI.create("https://itunes.apple.com/search?media=music&entity=song&limit=5&term=" + term))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) return new int[]{0, 0};
        JsonArray results = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("results");
        String url = null;
        for (JsonElement e : results) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("artworkUrl100")) {
                url = o.get("artworkUrl100").getAsString().replace("100x100", "64x64");
                break;
            }
        }
        if (url == null || !url.startsWith("https://")) return new int[]{0, 0};
        HttpResponse<byte[]> img = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(img.body()));
        if (image == null) return new int[]{0, 0};
        return dominant(image);
    }

    /** The most vivid colour (saturated, not too dark) and a contrasting second one, from a coarse colour histogram. */
    static int[] dominant(BufferedImage image) {
        int[] counts = new int[4096];
        long[][] sums = new long[4096][3];
        for (int y = 0; y < image.getHeight(); y += 2) {
            for (int x = 0; x < image.getWidth(); x += 2) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
                int bin = (r >> 4) << 8 | (g >> 4) << 4 | (b >> 4);
                counts[bin]++;
                sums[bin][0] += r;
                sums[bin][1] += g;
                sums[bin][2] += b;
            }
        }
        int best = -1;
        int second = -1;
        double bestScore = -1;
        double secondScore = -1;
        for (int i = 0; i < 4096; i++) {
            if (counts[i] == 0) continue;
            int r = (int) (sums[i][0] / counts[i]), g = (int) (sums[i][1] / counts[i]), b = (int) (sums[i][2] / counts[i]);
            float[] hsb = java.awt.Color.RGBtoHSB(r, g, b, null);
            double score = counts[i] * (0.25 + hsb[1]) * (hsb[2] < 0.25f ? 0.2 : 1);
            if (score > bestScore) {
                second = best;
                secondScore = bestScore;
                best = i;
                bestScore = score;
            } else if (score > secondScore) {
                second = i;
                secondScore = score;
            }
        }
        if (best < 0) return new int[]{0, 0};
        int main = brighten(avg(sums[best], counts[best]));
        int other = second < 0 ? 0xFFFFFFFF : brighten(avg(sums[second], counts[second]));
        return new int[]{main, other};
    }

    private static int avg(long[] s, int n) {
        return 0xFF000000 | (int) (s[0] / n) << 16 | (int) (s[1] / n) << 8 | (int) (s[2] / n);
    }

    /** Lyrics need light colours: lifts dark cover colours. */
    private static int brighten(int c) {
        float[] hsb = java.awt.Color.RGBtoHSB((c >> 16) & 255, (c >> 8) & 255, c & 255, null);
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hsb[0], Math.min(1f, hsb[1] * 1.1f), Math.max(0.85f, hsb[2]));
    }
}
