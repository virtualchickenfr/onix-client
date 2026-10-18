package dev.onix.client.gui;

import dev.onix.client.OnixClient;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The Onix loading animation, shared by the resource-loading overlay and every
 * world/connection loading screen. Everything is drawn procedurally (no textures,
 * optional text), so it also works while resources are still loading.
 */
public final class LoadingAnimation {
    private static final String[] TIPS = {
            "Right Shift opens the Onix menu – also from the title screen.",
            "Right-click any setting to reset it to its default.",
            "Type anywhere in the Onix menu to search modules and settings.",
            "Every module has its own toggle key under Controls.",
            "Link a profile to a server and it loads when you join.",
            "Scroll over a HUD element in the HUD editor to resize it.",
            "Press End to turn every module off – press again to bring them back.",
            "Players with the Onix badge use Onix too.",
            "Star modules to find them under Favorites.",
            "Colors can follow your theme accent or cycle through the rainbow.",
    };

    private static final long START = System.nanoTime();
    private static int tip = (int) (Math.random() * TIPS.length);
    private static long tipChangedAt;
    private static float shownProgress;

    private LoadingAnimation() {}

    public static boolean enabled() {
        return OnixClient.CONFIG == null || OnixClient.CONFIG.loadingScreens.isOn();
    }

    private static String style() {
        return OnixClient.CONFIG == null ? "Gem" : OnixClient.CONFIG.loadingStyle.get();
    }

    private static boolean tips() {
        return OnixClient.CONFIG == null || OnixClient.CONFIG.loadingTips.isOn();
    }

    private static float time() {
        return (System.nanoTime() - START) / 1_000_000_000f;
    }

    /**
     * Draws a full loading screen.
     *
     * @param title    big line under the animation, or null
     * @param detail   small line under the title, or null
     * @param progress 0..1, or a negative value for an indeterminate bar
     * @param alpha    overall opacity (fades)
     * @param text     false while fonts are not loaded yet (resource-loading overlay)
     */
    public static void render(GuiGraphics ctx, String title, String detail, float progress, float alpha, boolean text) {
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        Theme.refresh();
        background(ctx, w, h, alpha);
        int cy = h / 2 - (text ? 18 : 6);
        spinner(ctx, w / 2f, cy, Math.max(14, Math.min(26, h / 18f)), alpha);
        bar(ctx, w / 2, cy + 46, Math.min(180, w / 3), progress, alpha);
        if (text) {
            if (title != null && !title.isEmpty()) {
                int c = Theme.withAlpha(Theme.TEXT, (int) (255 * alpha));
                Theme.textCentered(ctx, title, w / 2, cy + 58, c);
            }
            if (detail != null && !detail.isEmpty()) {
                String d = Theme.ellipsize(detail, w - 40);
                Theme.small(ctx, d, w / 2 - Theme.smallWidth(d) / 2, cy + 72, Theme.withAlpha(Theme.TEXT_2, (int) (255 * alpha)));
            }
            if (tips()) tip(ctx, w, h, alpha);
        }
    }

    /** Only the spinner and the bar, for screens that draw their own text (e.g. "Saving world"). */
    public static void backgroundOnly(GuiGraphics ctx, float progress, float alpha) {
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        Theme.refresh();
        background(ctx, w, h, alpha);
        float cy = h / 2f + 34;
        spinner(ctx, w / 2f, cy, 12, alpha);
        bar(ctx, w / 2, (int) cy + 30, Math.min(140, w / 3), progress, alpha);
        if (tips()) tip(ctx, w, h, alpha);
    }

    // ------------------------------------------------------------------ parts

    private static void background(GuiGraphics ctx, int w, int h, float alpha) {
        int panel = Theme.current().panel();
        int base = Theme.lerpColor(panel, 0xFF000000, 0.35f);
        int top = Theme.lerpColor(panel, Theme.current().sky(), 0.10f);
        ctx.fillGradient(0, 0, w, h, Theme.withAlpha(top, (int) (255 * alpha)), Theme.withAlpha(base, (int) (255 * alpha)));
        // soft accent glow behind the animation
        int accent = Theme.accent();
        int max = Math.min(w, h) / 2;
        for (int i = 10; i >= 1; i--) {
            int r = max * i / 10;
            Theme.round(ctx, w / 2 - r, h / 2 - r - 10, w / 2 + r, h / 2 + r - 10, r, Theme.withAlpha(accent, (int) (3.2f * alpha)));
        }
    }

    private static void spinner(GuiGraphics ctx, float cx, float cy, float r, float alpha) {
        switch (style()) {
            case "Orbit" -> {
                gem(ctx, cx, cy, r * 0.62f, 0f, alpha);
                orbit(ctx, cx, cy, r * 1.45f, alpha);
            }
            case "Minimal" -> dots(ctx, cx, cy, r, alpha);
            default -> gem(ctx, cx, cy, r, time() * 1.6f, alpha);
        }
    }

    /** The Onix gem: six shaded facets around a hollow centre, turning around its vertical axis. */
    private static void gem(GuiGraphics ctx, float cx, float cy, float r, float spin, float alpha) {
        float breathe = 1f + 0.035f * (float) Math.sin(time() * 2.4f);
        float squash = spin == 0f ? 1f : (float) Math.cos(spin);
        float sx = Math.max(0.08f, Math.abs(squash));
        float[] ox = new float[6], oy = new float[6], ix = new float[6], iy = new float[6];
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(-90 + 60 * i);
            ox[i] = cx + (float) Math.cos(a) * r * breathe * sx;
            oy[i] = cy + (float) Math.sin(a) * r * breathe;
            ix[i] = cx + (float) Math.cos(a) * r * 0.42f * breathe * sx;
            iy[i] = cy + (float) Math.sin(a) * r * 0.42f * breathe;
        }
        int accent = Theme.accent();
        float light = squash >= 0 ? 0f : 0.35f; // the back side looks a little darker
        float[] shade = {0.95f, 0.78f, 0.55f, 0.38f, 0.55f, 0.78f};
        float[] xy = new float[6 * 12];
        int[] colors = new int[6 * 6];
        for (int i = 0; i < 6; i++) {
            int j = (i + 1) % 6;
            int facet = (i + (squash < 0 ? 3 : 0)) % 6;
            float s = Math.max(0.15f, shade[facet] - light);
            int c = Theme.withAlpha(Theme.lerpColor(Theme.lerpColor(accent, 0xFF0A0A0E, 0.55f), Theme.lerpColor(accent, 0xFFFFFFFF, 0.55f), s),
                    (int) (255 * alpha));
            float[] q = {ox[i], oy[i], ox[j], oy[j], ix[j], iy[j], ox[i], oy[i], ix[j], iy[j], ix[i], iy[i]};
            System.arraycopy(q, 0, xy, i * 12, 12);
            for (int k = 0; k < 6; k++) colors[i * 6 + k] = c;
        }
        triangles(ctx, xy, colors);
    }

    private static void orbit(GuiGraphics ctx, float cx, float cy, float r, float alpha) {
        int accent = Theme.accent();
        float t = time() * 5.2f;
        int n = 10;
        for (int i = 0; i < n; i++) {
            float a = (float) (i * Math.PI * 2 / n);
            float head = (float) ((t % (Math.PI * 2)) - a);
            if (head < 0) head += (float) (Math.PI * 2);
            float k = 1f - head / (float) (Math.PI * 2);
            int x = Math.round(cx + (float) Math.sin(a) * r);
            int y = Math.round(cy - (float) Math.cos(a) * r);
            int s = 1 + Math.round(k * 2);
            Theme.round(ctx, x - s, y - s, x + s, y + s, s, Theme.withAlpha(accent, (int) (255 * alpha * (0.15f + 0.85f * k * k))));
        }
    }

    private static void dots(GuiGraphics ctx, float cx, float cy, float r, float alpha) {
        int accent = Theme.accent();
        for (int i = 0; i < 3; i++) {
            float k = (float) (0.5 + 0.5 * Math.sin(time() * 5 - i * 0.7));
            int x = Math.round(cx + (i - 1) * r * 0.7f);
            int y = Math.round(cy - k * r * 0.25f);
            int s = 2 + Math.round(k * 1.5f);
            Theme.round(ctx, x - s, y - s, x + s, y + s, s, Theme.withAlpha(accent, (int) (255 * alpha * (0.35f + 0.65f * k))));
        }
    }

    private static void bar(GuiGraphics ctx, int cx, int y, int width, float progress, float alpha) {
        int x1 = cx - width / 2;
        int x2 = cx + width / 2;
        Theme.round(ctx, x1, y, x2, y + 3, 1, Theme.withAlpha(0xFFFFFFFF, (int) (28 * alpha)));
        int accent = Theme.withAlpha(Theme.accent(), (int) (255 * alpha));
        if (progress < 0) {
            float t = (time() * 0.8f) % 1f;
            float eased = (float) (0.5 - 0.5 * Math.cos(t * Math.PI));
            int seg = width * 2 / 5;
            int s1 = x1 - seg + Math.round((width + seg) * eased);
            Theme.round(ctx, Math.max(x1, s1), y, Math.min(x2, s1 + seg), y + 3, 1, accent);
        } else {
            shownProgress += (Math.min(1f, progress) - shownProgress) * 0.12f;
            if (progress < shownProgress - 0.2f) shownProgress = progress;
            int fill = x1 + Math.round(width * shownProgress);
            Theme.round(ctx, x1, y, Math.max(x1 + 2, fill), y + 3, 1, accent);
            // moving highlight on the filled part
            int glint = x1 + Math.round(((time() * 0.6f) % 1f) * width);
            if (glint < fill - 6) Theme.round(ctx, glint, y, Math.min(fill, glint + 12), y + 3, 1, Theme.withAlpha(0xFFFFFFFF, (int) (70 * alpha)));
        }
    }

    private static void tip(GuiGraphics ctx, int w, int h, float alpha) {
        long now = System.currentTimeMillis();
        if (tipChangedAt == 0) tipChangedAt = now;
        if (now - tipChangedAt > 6000) {
            tip = (tip + 1) % TIPS.length;
            tipChangedAt = now;
        }
        float fade = Math.min(1f, Math.min((now - tipChangedAt) / 400f, (6000 - (now - tipChangedAt)) / 400f));
        String s = Theme.ellipsize(TIPS[tip], (int) ((w - 40) / 0.75f));
        Theme.small(ctx, s, w / 2 - Theme.smallWidth(s) / 2, h - 22, Theme.withAlpha(Theme.TEXT_3, (int) (255 * alpha * Math.max(0, fade))));
    }

    /** Coloured triangles (see GuiTriangles). */
    private static void triangles(GuiGraphics ctx, float[] xy, int[] colors) {
        GuiTriangles.draw(ctx, xy, colors);
    }
}
