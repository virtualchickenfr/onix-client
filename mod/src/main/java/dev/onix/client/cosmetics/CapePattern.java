package dev.onix.client.cosmetics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.Map;

/**
 * Turns a cape pattern from the catalog into palette indices for the 20 x 32 cape face.
 * Must stay identical to capeIndices() in launcher/src/js/cosrender.js, so previews match the game
 * (StrictMath for the same trigonometry as JavaScript, the same mulberry32 random numbers).
 */
public final class CapePattern {
    public static final int W = 20;
    public static final int H = 32;
    private static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

    private CapePattern() {}

    /** mulberry32, as in the launcher. */
    private static final class Rng {
        private int a;

        Rng(int seed) {
            a = seed;
        }

        double next() {
            a += 0x6d2b79f5;
            int t = (a ^ (a >>> 15)) * (1 | a);
            t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
            return ((t ^ (t >>> 14)) & 0xFFFFFFFFL) / 4294967296.0;
        }
    }

    private static int mod(int v, int p) {
        return ((v % p) + p) % p;
    }

    private static double mod(double v, double p) {
        return ((v % p) + p) % p;
    }

    private static double num(JsonObject o, String key, double fallback) {
        return o.has(key) ? o.get(key).getAsDouble() : fallback;
    }

    private static int[] pair(JsonObject o) {
        JsonArray a = o.getAsJsonArray("c");
        return new int[]{a.get(0).getAsInt(), a.get(1).getAsInt()};
    }

    public static int[] indices(JsonArray ops, Map<String, String[]> emblems) {
        return indices(ops, emblems, 0);
    }

    /** One animation frame (0 for still capes). */
    public static int[] indices(JsonArray ops, Map<String, String[]> emblems, int frame) {
        int[] px = new int[W * H];
        for (JsonElement el : ops) {
            JsonObject op = el.getAsJsonObject();
            if (op.has("on") && !contains(op.getAsJsonArray("on"), frame)) continue;
            int off = (int) num(op, "o", 0) + frame * (int) num(op, "mv", 0); // scrolling offset
            String t = op.get("t").getAsString();
            switch (t) {
                case "fill" -> Arrays.fill(px, op.get("c").getAsInt());
                case "grad" -> {
                    int[] c = pair(op);
                    double from = num(op, "from", 0);
                    double to = num(op, "to", 1);
                    String a = op.has("a") ? op.get("a").getAsString() : "v";
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            double v = a.equals("h") ? x / (double) (W - 1) : a.equals("d") ? (x + y) / (double) (W + H - 2) : y / (double) (H - 1);
                            if (v < from || v > to) continue;
                            double u = (v - from) / Math.max(1e-6, to - from);
                            set(px, x, y, u > (BAYER[y & 3][x & 3] + 0.5) / 16 ? c[1] : c[0]);
                        }
                    }
                }
                case "rect" -> {
                    int x0 = op.get("x").getAsInt(), y0 = op.get("y").getAsInt();
                    int c = op.get("c").getAsInt();
                    for (int y = y0; y < y0 + op.get("h").getAsInt(); y++) {
                        for (int x = x0; x < x0 + op.get("w").getAsInt(); x++) set(px, x, y, c);
                    }
                }
                case "border" -> {
                    int w = op.get("w").getAsInt(), c = op.get("c").getAsInt();
                    for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (x < w || y < w || x >= W - w || y >= H - w) set(px, x, y, c);
                }
                case "stripes" -> {
                    String a = op.get("a").getAsString();
                    int w = op.get("w").getAsInt(), p = op.get("p").getAsInt(), o = off, c = op.get("c").getAsInt();
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            int pos = switch (a) {
                                case "h" -> y;
                                case "v" -> x;
                                case "d" -> x + y;
                                default -> x - y + 64;
                            };
                            if (mod(pos + o, p) < w) set(px, x, y, c);
                        }
                    }
                }
                case "chevron" -> {
                    int w = op.get("w").getAsInt(), p = op.get("p").getAsInt(), o = off, c = op.get("c").getAsInt();
                    for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (mod((int) Math.floor(y + Math.abs(x - 9.5)) + o, p) < w) set(px, x, y, c);
                }
                case "checker" -> {
                    int s = op.get("s").getAsInt();
                    int[] c = pair(op);
                    for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) set(px, x, y, (Math.floorDiv(x, s) + Math.floorDiv(y + off, s)) % 2 != 0 ? c[1] : c[0]);
                }
                case "dots" -> {
                    int s = op.get("s").getAsInt(), c = op.get("c").getAsInt();
                    double r = op.get("r").getAsDouble();
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            int yy = y + off;
                            int row = Math.floorDiv(yy, s);
                            int xx = x + mod(row, 2) * (s / 2);
                            double dx = mod(xx, s) - (s - 1) / 2.0;
                            double dy = mod(yy, s) - (s - 1) / 2.0;
                            if (dx * dx + dy * dy <= r * r) set(px, x, y, c);
                        }
                    }
                }
                case "scatter" -> {
                    int seed = op.get("seed").getAsInt();
                    Rng r = new Rng(seed);
                    int n = op.get("n").getAsInt(), c = op.get("c").getAsInt();
                    boolean plus = num(op, "plus", 0) != 0;
                    double tw = num(op, "tw", 0);
                    Rng hide = tw != 0 ? new Rng(seed * 7919 + frame * 104729) : null; // twinkle: some stars are off each frame
                    for (int i = 0; i < n; i++) {
                        int x = (int) Math.floor(r.next() * W);
                        int y = (int) Math.floor(r.next() * H);
                        if (hide != null && hide.next() < tw) continue;
                        set(px, x, y, c);
                        if (plus) {
                            set(px, x - 1, y, c);
                            set(px, x + 1, y, c);
                            set(px, x, y - 1, c);
                            set(px, x, y + 1, c);
                        }
                    }
                }
                case "waves" -> {
                    int w = op.get("w").getAsInt(), p = op.get("p").getAsInt(), o = off, c = op.get("c").getAsInt();
                    double amp = op.get("amp").getAsDouble(), len = op.get("len").getAsDouble();
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            if (mod((int) Math.floor(y + amp * StrictMath.sin((2 * Math.PI * x) / len)) + o, p) < w) set(px, x, y, c);
                        }
                    }
                }
                case "zigzag" -> {
                    int w = op.get("w").getAsInt(), p = op.get("p").getAsInt(), o = off, c = op.get("c").getAsInt();
                    double amp = op.get("amp").getAsDouble();
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            int ph = mod(x, 8);
                            int tri = ph < 4 ? ph : 8 - ph;
                            if (mod((int) Math.floor(y + (tri * amp) / 4) + o, p) < w) set(px, x, y, c);
                        }
                    }
                }
                case "argyle" -> {
                    int sx = op.get("sx").getAsInt(), sy = op.get("sy").getAsInt(), c = op.get("c").getAsInt();
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            double u = (mod(x, sx) - (sx - 1) / 2.0) / (sx / 2.0);
                            double v = (mod(y + off, sy) - (sy - 1) / 2.0) / (sy / 2.0);
                            if (Math.abs(u) + Math.abs(v) <= 1) set(px, x, y, c);
                        }
                    }
                }
                case "flames" -> {
                    int[] c = pair(op);
                    int seed = op.get("seed").getAsInt();
                    Rng r = new Rng(seed);
                    Rng jitter = num(op, "fr", 0) != 0 ? new Rng(seed + frame * 101) : r; // flickering flames get new heights each frame
                    double phase = r.next() * 6.283;
                    for (int x = 0; x < W; x++) {
                        double h = 7 + 5 * Math.abs(StrictMath.sin(x * 0.8 + phase + frame * 0.785398)) + Math.floor(jitter.next() * 4);
                        for (int y = 0; y < H; y++) {
                            if (y >= H - h) set(px, x, y, c[0]);
                            if (y >= H - h * 0.55) set(px, x, y, c[1]);
                        }
                    }
                }
                case "rays" -> {
                    int n = op.get("n").getAsInt(), c = op.get("c").getAsInt();
                    double cx = op.get("cx").getAsDouble(), cy = op.get("cy").getAsDouble();
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            double a = StrictMath.atan2(y + 0.5 - cy, x + 0.5 - cx) + frame * num(op, "rot", 0);
                            if (mod((int) Math.floor(((a + Math.PI) / (2 * Math.PI)) * n), 2) == 1) set(px, x, y, c);
                        }
                    }
                }
                case "disc" -> {
                    double cx = op.get("cx").getAsDouble(), cy = op.get("cy").getAsDouble(), r = op.get("r").getAsDouble();
                    int c = op.get("c").getAsInt();
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                            if (dx * dx + dy * dy <= r * r) set(px, x, y, c);
                        }
                    }
                }
                case "noise" -> {
                    int seed = op.get("seed").getAsInt();
                    Rng r = new Rng(num(op, "fr", 0) != 0 ? seed + frame * 101 : seed);
                    double d = op.get("d").getAsDouble();
                    int c = op.get("c").getAsInt();
                    for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) if (r.next() < d) set(px, x, y, c);
                }
                case "emblem" -> {
                    String[] mask = emblems.get(op.get("m").getAsString());
                    if (mask == null) break;
                    int ox = op.get("x").getAsInt(), oy = op.get("y").getAsInt(), c = op.get("c").getAsInt();
                    if (op.has("outline")) {
                        int oc = op.get("outline").getAsInt();
                        for (int my = -1; my <= mask.length; my++) {
                            for (int mx = -1; mx <= 10; mx++) {
                                if (in(mask, mx, my)) continue;
                                if (in(mask, mx - 1, my) || in(mask, mx + 1, my) || in(mask, mx, my - 1) || in(mask, mx, my + 1)) set(px, ox + mx, oy + my, oc);
                            }
                        }
                    }
                    for (int my = 0; my < mask.length; my++) {
                        for (int mx = 0; mx < mask[my].length(); mx++) if (mask[my].charAt(mx) == '#') set(px, ox + mx, oy + my, c);
                    }
                }
                case "sheen" -> {
                    // a diagonal band of light that sweeps across the cape
                    int w = op.get("w").getAsInt(), c = op.get("c").getAsInt();
                    int pos = frame * op.get("step").getAsInt() - w;
                    int only = op.has("only") ? op.get("only").getAsInt() : -1;
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            int d = x + Math.floorDiv(y, 2) - pos;
                            if (d >= 0 && d < w && (only < 0 || px[y * W + x] == only)) set(px, x, y, c);
                        }
                    }
                }
                case "drops" -> {
                    // falling streaks (rain, code rain); speed * frames is a multiple of the height, so it loops
                    Rng r = new Rng(op.get("seed").getAsInt());
                    int n = op.get("n").getAsInt(), c = op.get("c").getAsInt(), speed = op.get("speed").getAsInt();
                    int head = op.has("c2") ? op.get("c2").getAsInt() : c;
                    for (int i = 0; i < n; i++) {
                        int x = (int) Math.floor(r.next() * W);
                        int y0 = (int) Math.floor(r.next() * H);
                        int len = op.get("len").getAsInt() + (int) Math.floor(r.next() * 3);
                        int hy = mod(y0 + frame * speed, H);
                        for (int k = 1; k < len; k++) set(px, x, mod(hy - k, H), c);
                        set(px, x, hy, head);
                    }
                }
                case "scales" -> {
                    int s = op.get("s").getAsInt(), c = op.get("c").getAsInt();
                    int c2 = op.has("c2") ? op.get("c2").getAsInt() : -1;
                    for (int y = 0; y < H; y++) {
                        for (int x = 0; x < W; x++) {
                            int yy = y + off;
                            int row = Math.floorDiv(yy, s);
                            double dx = mod(x + mod(row, 2) * (s / 2), s) - s / 2.0 + 0.5;
                            double dy = mod(yy, s) + 0.5;
                            double dist = Math.sqrt(dx * dx + dy * dy);
                            if (Math.abs(dist - s / 2.0) < 0.75) set(px, x, y, c);
                            else if (c2 >= 0 && dist < s / 2.0 - 1.5 && dy < s / 2.0) set(px, x, y, c2);
                        }
                    }
                }
                case "blobs" -> {
                    Rng r = new Rng(op.get("seed").getAsInt());
                    int n = op.get("n").getAsInt(), c = op.get("c").getAsInt();
                    double radius = op.get("r").getAsDouble();
                    for (int i = 0; i < n; i++) {
                        double cx = r.next() * W;
                        double cy = r.next() * H;
                        double rad = radius * (0.5 + r.next());
                        for (int y = 0; y < H; y++) {
                            for (int x = 0; x < W; x++) {
                                double dx = x + 0.5 - cx, dy = y + 0.5 - cy;
                                double q = (dx * dx + dy * dy) / (rad * rad);
                                if (q <= 1 && q < 1 - (BAYER[y & 3][x & 3] + 0.5) / 32) set(px, x, y, c);
                            }
                        }
                    }
                }
                default -> {
                    // unknown operation from a newer catalog: skip it
                }
            }
        }
        return px;
    }

    private static boolean contains(JsonArray frames, int frame) {
        for (JsonElement e : frames) if (e.getAsInt() == frame) return true;
        return false;
    }

    private static boolean in(String[] mask, int x, int y) {
        return y >= 0 && y < mask.length && x >= 0 && x < mask[y].length() && mask[y].charAt(x) == '#';
    }

    private static void set(int[] px, int x, int y, int c) {
        if (c >= 0 && x >= 0 && y >= 0 && x < W && y < H) px[y * W + x] = c;
    }
}
