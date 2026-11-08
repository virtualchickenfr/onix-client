package dev.onix.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Loading screen in the style of eDEX-UI: black with scanlines and corner brackets, a terminal boot log that
 * streams the real start (Java, memory, every loaded mod, the loading stages), ONIX in big glitching letters and a
 * thin progress bar. Minecraft's own fonts are not loaded yet while the game starts, so all text is drawn with a
 * small built-in 5x7 pixel font.
 */
public final class EdexLoading {
    private EdexLoading() {}

    private static final int BG = 0xFF030305;
    private static final int FG = 0xFFC8F1F8;
    private static final int DIM = 0xFF5F8A93;
    private static final int OK = 0xFF6EE7A8;
    private static final int RED = 0xFFFF2A6D;
    private static final int CYAN = 0xFF05D9E8;
    private static final long START = System.currentTimeMillis();

    private static List<String[]> boot; // {tag, text}
    private static long doneAt = -1;
    private static float shown;
    // world / server loading: the details seen so far become log lines
    private static final List<String> history = new ArrayList<>();
    private static String lastTitle;

    // ------------------------------------------------------------------ boot log content

    private static List<String[]> bootLines() {
        if (boot != null) return boot;
        List<String[]> l = new ArrayList<>();
        Runtime rt = Runtime.getRuntime();
        String mc = FabricLoader.getInstance().getModContainer("minecraft").map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
        l.add(new String[]{"", "ONIX BOOT SEQUENCE // MINECRAFT " + mc});
        l.add(new String[]{"OK", "JAVA " + System.getProperty("java.version") + " · " + rt.availableProcessors() + " CORES · " + rt.maxMemory() / 1048576 + " MB HEAP"});
        l.add(new String[]{"OK", "OS " + System.getProperty("os.name") + " " + System.getProperty("os.arch")});
        List<ModContainer> mods = new ArrayList<>(FabricLoader.getInstance().getAllMods());
        mods.removeIf(m -> m.getMetadata().getId().startsWith("fabric-") && !m.getMetadata().getId().equals("fabric-api"));
        mods.sort((a, b) -> a.getMetadata().getId().compareTo(b.getMetadata().getId()));
        l.add(new String[]{"OK", "FABRIC LOADER · " + FabricLoader.getInstance().getAllMods().size() + " MODS"});
        for (ModContainer m : mods) {
            String v = m.getMetadata().getVersion().getFriendlyString();
            if (v.length() > 24) v = v.substring(0, 24);
            l.add(new String[]{"OK", "LOAD " + m.getMetadata().getId() + " " + v});
        }
        l.add(new String[]{"", "MOUNTING RESOURCE PACKS"});
        l.add(new String[]{"", "STITCHING TEXTURE ATLASES"});
        l.add(new String[]{"", "BAKING BLOCK AND ITEM MODELS"});
        l.add(new String[]{"", "COMPILING SHADERS"});
        l.add(new String[]{"", "STARTING SOUND ENGINE"});
        l.add(new String[]{"OK", "ONIX CLIENT ONLINE"});
        boot = l;
        return l;
    }

    // ------------------------------------------------------------------ the resource loading overlay

    /** Game start, F3 + T and resource pack changes. progress is 0..1 or negative while unknown. */
    public static void splash(GuiGraphics ctx, float progress, boolean done, float alpha) {
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        long now = System.currentTimeMillis();
        if (done && doneAt < 0) doneAt = now;
        if (!done) doneAt = -1;
        frame(ctx, w, h, alpha);

        List<String[]> lines = bootLines();
        float p = progress < 0 ? 0 : progress;
        shown += (p - shown) * 0.08f;
        if (p < shown - 0.3f) shown = p;
        // lines appear with the real progress, but also at least at terminal speed so the log never stands still
        int byProgress = Math.round(lines.size() * (done ? 1f : Math.min(0.97f, shown)));
        int byTime = (int) ((now - START) / 45);
        int visible = done ? lines.size() : Math.min(lines.size() - 1, Math.max(byProgress, Math.min(byTime, lines.size() - 6)));
        log(ctx, lines, visible, w, h, alpha);

        // title
        float t = (now - START) / 1000f;
        int scale = Math.max(3, Math.min(8, Math.min(w, h) / 45));
        int ty = h / 2 - 12 * scale / 2 - 6;
        title(ctx, "ONIX", w / 2, ty, scale, alpha, t, doneAt >= 0 ? (now - doneAt) / 1000f : -1);
        text(ctx, "CLIENT · LOADING", w / 2 - width("CLIENT · LOADING", 1) / 2, ty + 8 * scale + 6, 1, Theme.withAlpha(DIM, (int) (255 * alpha)));

        // progress bar
        int bw = Math.min(260, w / 2);
        int bx = w / 2 - bw / 2;
        int by = ty + 8 * scale + 22;
        float shownBar = done ? 1f : shown;
        ctx.fill(bx, by, bx + bw, by + 1, Theme.withAlpha(DIM, (int) (110 * alpha)));
        int fill = Math.round(bw * Math.max(0, Math.min(1, shownBar)));
        ctx.fill(bx, by - 1, bx + fill, by + 2, Theme.withAlpha(FG, (int) (255 * alpha)));
        if (fill > 2) ctx.fill(bx + fill - 2, by - 3, bx + fill, by + 4, Theme.withAlpha(0xFFFFFFFF, (int) (255 * alpha)));
        String pct = done ? "READY" : Math.round(shownBar * 100) + "%";
        text(ctx, pct, bx + bw - width(pct, 1), by + 6, 1, Theme.withAlpha(FG, (int) (220 * alpha)));
        String stage = done ? "ALL SYSTEMS READY" : lines.get(Math.max(0, Math.min(lines.size() - 1, visible - 1)))[1];
        text(ctx, clip(stage, bw - 40), bx, by + 6, 1, Theme.withAlpha(DIM, (int) (220 * alpha)));
    }

    // ------------------------------------------------------------------ world / server loading screens

    public static void screen(GuiGraphics ctx, String title, String detail, float progress, float alpha) {
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        String ti = title == null ? "LOADING" : title.toUpperCase(Locale.ROOT);
        if (!ti.equals(lastTitle)) {
            lastTitle = ti;
            history.clear();
            history.add(t2("", "ONIX // " + ti));
        }
        if (detail != null && !detail.isEmpty()) {
            String d = detail.toUpperCase(Locale.ROOT).replaceAll("\\s*·\\s*\\d+%$", "");
            String last = history.get(history.size() - 1);
            if (!last.endsWith(d)) history.add(t2("OK", d));
            while (history.size() > 14) history.remove(1);
        }
        frame(ctx, w, h, alpha);
        List<String[]> lines = new ArrayList<>();
        for (String s : history) {
            int i = s.indexOf('\u0001');
            lines.add(new String[]{s.substring(0, i), s.substring(i + 1)});
        }
        log(ctx, lines, lines.size(), w, h, alpha);
        float t = (System.currentTimeMillis() - START) / 1000f;
        int scale = Math.max(2, Math.min(5, Math.min(w, h) / 70));
        int tw = width(ti, scale);
        if (tw > w - 40) {
            scale = Math.max(1, scale - 2);
            tw = width(ti, scale);
        }
        int ty = h / 2 - 4 * scale - 10;
        title(ctx, ti, w / 2, ty, scale, alpha, t, -1);
        int bw = Math.min(220, w / 2);
        int bx = w / 2 - bw / 2;
        int by = ty + 8 * scale + 12;
        ctx.fill(bx, by, bx + bw, by + 1, Theme.withAlpha(DIM, (int) (110 * alpha)));
        if (progress < 0) {
            float k = (t * 0.7f) % 1f;
            int seg = bw / 3;
            int s1 = bx - seg + Math.round((bw + seg) * k);
            ctx.fill(Math.max(bx, s1), by - 1, Math.min(bx + bw, s1 + seg), by + 2, Theme.withAlpha(FG, (int) (255 * alpha)));
        } else {
            ctx.fill(bx, by - 1, bx + Math.round(bw * Math.min(1, progress)), by + 2, Theme.withAlpha(FG, (int) (255 * alpha)));
            String pct = Math.round(progress * 100) + "%";
            text(ctx, pct, bx + bw - width(pct, 1), by + 6, 1, Theme.withAlpha(FG, (int) (220 * alpha)));
        }
        if (detail != null) text(ctx, clip(detail.toUpperCase(Locale.ROOT), bw - 36), bx, by + 6, 1, Theme.withAlpha(DIM, (int) (220 * alpha)));
    }

    /** Frame and bar only, for screens that draw their own text (e.g. "Saving world"). */
    public static void backgroundOnly(GuiGraphics ctx, float progress, float alpha) {
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        frame(ctx, w, h, alpha);
        int bw = Math.min(200, w / 2);
        int bx = w / 2 - bw / 2;
        int by = h / 2 + 40;
        ctx.fill(bx, by, bx + bw, by + 1, Theme.withAlpha(DIM, (int) (110 * alpha)));
        float t = (System.currentTimeMillis() - START) / 1000f;
        if (progress < 0) {
            float k = (t * 0.7f) % 1f;
            int seg = bw / 3;
            int s1 = bx - seg + Math.round((bw + seg) * k);
            ctx.fill(Math.max(bx, s1), by - 1, Math.min(bx + bw, s1 + seg), by + 2, Theme.withAlpha(FG, (int) (255 * alpha)));
        } else {
            ctx.fill(bx, by - 1, bx + Math.round(bw * Math.min(1, progress)), by + 2, Theme.withAlpha(FG, (int) (255 * alpha)));
        }
    }

    private static String t2(String tag, String text) {
        return tag + '\u0001' + text;
    }

    // ------------------------------------------------------------------ parts

    private static void frame(GuiGraphics ctx, int w, int h, float alpha) {
        ctx.fill(0, 0, w, h, Theme.withAlpha(BG, (int) (255 * alpha)));
        int line = Theme.withAlpha(0xFFFFFFFF, (int) (7 * alpha));
        for (int y = 0; y < h; y += 3) ctx.fill(0, y, w, y + 1, line);
        int c = Theme.withAlpha(DIM, (int) (200 * alpha));
        int m = 8;
        int s = 12;
        // corner brackets
        ctx.fill(m, m, m + s, m + 1, c);
        ctx.fill(m, m, m + 1, m + s, c);
        ctx.fill(w - m - s, m, w - m, m + 1, c);
        ctx.fill(w - m - 1, m, w - m, m + s, c);
        ctx.fill(m, h - m - 1, m + s, h - m, c);
        ctx.fill(m, h - m - s, m + 1, h - m, c);
        ctx.fill(w - m - s, h - m - 1, w - m, h - m, c);
        ctx.fill(w - m - 1, h - m - s, w - m, h - m, c);
        // header
        int a = (int) (255 * alpha);
        text(ctx, "ONIX // SYSTEM", m + 18, m + 3, 1, Theme.withAlpha(DIM, a));
        long up = (System.currentTimeMillis() - START) / 100;
        String clock = String.format(Locale.ROOT, "T+%02d:%02d.%d", up / 600, up / 10 % 60, up % 10);
        text(ctx, clock, w - m - 18 - width(clock, 1), m + 3, 1, Theme.withAlpha(DIM, a));
    }

    private static void log(GuiGraphics ctx, List<String[]> lines, int visible, int w, int h, float alpha) {
        int lh = 10;
        int max = Math.max(4, (h / 2 - 70) / lh);
        int from = Math.max(0, visible - max);
        int x = 26;
        int y = h - 26 - (visible - from) * lh;
        int a = (int) (255 * alpha);
        int maxChars = Math.max(10, (w / 2 - 40) / 6);
        for (int i = from; i < visible; i++) {
            String[] l = lines.get(i);
            int cx = x;
            if (l[0].equals("OK")) {
                text(ctx, "[ OK ]", cx, y, 1, Theme.withAlpha(OK, a));
                cx += width("[ OK ] ", 1);
            }
            float fade = i == visible - 1 ? 1f : 0.55f + 0.45f * (i - from) / Math.max(1f, visible - from);
            text(ctx, clip(l[1], maxChars * 6), cx, y, 1, Theme.withAlpha(l[0].isEmpty() ? DIM : FG, (int) (a * fade)));
            y += lh;
        }
        // blinking cursor after the newest line
        if ((System.currentTimeMillis() / 450) % 2 == 0) ctx.fill(x, y, x + 5, y + 7, Theme.withAlpha(FG, a));
    }

    /** Big letters with an RGB split that glitches now and then (and hard right before the screen fades out). */
    private static void title(GuiGraphics ctx, String s, int cx, int y, int scale, float alpha, float t, float doneFor) {
        int tw = width(s, scale);
        int x = cx - tw / 2;
        int a = (int) (255 * alpha);
        boolean glitch = (t % 3.1f) < 0.16f || (t < 0.6f && (int) (t * 20) % 3 == 0) || (doneFor >= 0 && doneFor < 0.5f && (int) (doneFor * 25) % 2 == 0);
        int off = glitch ? Math.max(1, scale / 2) : Math.max(1, scale / 4);
        text(ctx, s, x - off, y, scale, Theme.withAlpha(RED, (int) (a * (glitch ? 0.9f : 0.45f))));
        text(ctx, s, x + off, y, scale, Theme.withAlpha(CYAN, (int) (a * (glitch ? 0.9f : 0.45f))));
        int dx = glitch ? (int) (Math.sin(t * 90) * scale) : 0;
        text(ctx, s, x + dx, y, scale, Theme.withAlpha(0xFFFFFFFF, a));
        if (glitch) {
            int sy = y + (int) ((Math.sin(t * 57) * 0.5 + 0.5) * 6 * scale);
            ctx.fill(x - 4 * scale, sy, x + tw + 4 * scale, sy + Math.max(1, scale / 2), Theme.withAlpha(BG, a));
        }
    }

    private static String clip(String s, int px) {
        int max = Math.max(1, px / 6);
        return s.length() <= max ? s : s.substring(0, Math.max(1, max - 3)) + "...";
    }

    // ------------------------------------------------------------------ 5x7 pixel font

    private static final Map<Character, int[]> GLYPHS = new HashMap<>();

    private static void g(char c, String... rows) {
        int[] r = new int[7];
        for (int i = 0; i < 7; i++) {
            int bits = 0;
            for (int j = 0; j < 5; j++) if (rows[i].charAt(j) == '#') bits |= 1 << (4 - j);
            r[i] = bits;
        }
        GLYPHS.put(c, r);
    }

    static {
        g('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        g('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        g('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        g('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        g('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        g('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####");
        g('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('I', ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.");
        g('J', "..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##..");
        g('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        g('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        g('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        g('N', "#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#");
        g('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        g('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
        g('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        g('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        g('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        g('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        g('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#.");
        g('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        g('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        g('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");
        g('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
        g('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.");
        g('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
        g('3', "#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###.");
        g('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
        g('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
        g('6', "..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###.");
        g('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
        g('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
        g('9', ".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##..");
        g('.', ".....", ".....", ".....", ".....", ".....", ".##..", ".##..");
        g(',', ".....", ".....", ".....", ".....", ".##..", "..#..", ".#...");
        g(':', ".....", ".##..", ".##..", ".....", ".##..", ".##..", ".....");
        g(';', ".....", ".##..", ".##..", ".....", ".##..", "..#..", ".#...");
        g('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....");
        g('_', ".....", ".....", ".....", ".....", ".....", ".....", "#####");
        g('/', ".....", "....#", "...#.", "..#..", ".#...", "#....", ".....");
        g('\\', ".....", "#....", ".#...", "..#..", "...#.", "....#", ".....");
        g('[', ".###.", ".#...", ".#...", ".#...", ".#...", ".#...", ".###.");
        g(']', ".###.", "...#.", "...#.", "...#.", "...#.", "...#.", ".###.");
        g('(', "...#.", "..#..", ".#...", ".#...", ".#...", "..#..", "...#.");
        g(')', ".#...", "..#..", "...#.", "...#.", "...#.", "..#..", ".#...");
        g('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....");
        g('=', ".....", ".....", "#####", ".....", "#####", ".....", ".....");
        g('%', "##...", "##..#", "...#.", "..#..", ".#...", "#..##", "...##");
        g('>', ".#...", "..#..", "...#.", "....#", "...#.", "..#..", ".#...");
        g('<', "...#.", "..#..", ".#...", "#....", ".#...", "..#..", "...#.");
        g('!', "..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#..");
        g('?', ".###.", "#...#", "....#", "...#.", "..#..", ".....", "..#..");
        g('\'', "..#..", "..#..", ".....", ".....", ".....", ".....", ".....");
        g('"', ".#.#.", ".#.#.", ".....", ".....", ".....", ".....", ".....");
        g('#', ".#.#.", ".#.#.", "#####", ".#.#.", "#####", ".#.#.", ".#.#.");
        g('*', ".....", "..#..", "#.#.#", ".###.", "#.#.#", "..#..", ".....");
        g('|', "..#..", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        g('@', ".###.", "#...#", "#.###", "#.#.#", "#.###", "#....", ".###.");
        g('&', ".##..", "#..#.", "#.#..", ".#...", "#.#.#", "#..#.", ".##.#");
        g('·', ".....", ".....", ".....", "..#..", ".....", ".....", ".....");
        g(' ', ".....", ".....", ".....", ".....", ".....", ".....", ".....");
    }

    static int width(String s, int scale) {
        return s.length() * 6 * scale - scale;
    }

    /** Draws text with the pixel font: each run of lit pixels in a glyph row is one rectangle. */
    static void text(GuiGraphics ctx, String s, int x, int y, int scale, int color) {
        if ((color >>> 24) == 0) return;
        int cx = x;
        for (int k = 0; k < s.length(); k++) {
            char c = Character.toUpperCase(s.charAt(k));
            if (c == '…') c = '.';
            int[] g = GLYPHS.get(c);
            if (g == null) g = GLYPHS.get('?');
            if (c != ' ') {
                for (int row = 0; row < 7; row++) {
                    int bits = g[row];
                    int col = 0;
                    while (col < 5) {
                        if ((bits & (1 << (4 - col))) == 0) {
                            col++;
                            continue;
                        }
                        int start = col;
                        while (col < 5 && (bits & (1 << (4 - col))) != 0) col++;
                        ctx.fill(cx + start * scale, y + row * scale, cx + col * scale, y + (row + 1) * scale, color);
                    }
                }
            }
            cx += 6 * scale;
        }
    }
}
