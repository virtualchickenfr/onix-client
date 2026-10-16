package dev.onix.client.gui;

import dev.onix.client.OnixClient;
import dev.onix.client.config.OnixConfig;
import dev.onix.client.config.ThemeStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * The Onix design system: colors (derived from the active theme), fonts (Inter) and
 * drawing helpers. All menus draw through this class so the look stays consistent.
 */
public final class Theme {
    // ---------- fonts ----------
    public static final ResourceLocation FONT_UI = ResourceLocation.fromNamespaceAndPath("onix", "ui");
    public static final ResourceLocation FONT_BOLD = ResourceLocation.fromNamespaceAndPath("onix", "ui_bold");
    public static final ResourceLocation FONT_DISPLAY = ResourceLocation.fromNamespaceAndPath("onix", "display");

    // ---------- surfaces (set in refresh()) ----------
    public static int BACKDROP = 0xB0050507;
    public static int SURFACE = 0xF20D0D11;
    public static int SURFACE_SUNKEN = 0xFF09090C;
    public static int SURFACE_2 = 0xFF141419;
    public static int SURFACE_3 = 0xFF1B1B21;
    public static int SURFACE_4 = 0xFF26262E;
    public static int BORDER = 0xFF222229;
    public static int BORDER_STRONG = 0xFF33333C;

    // ---------- Text ----------
    public static int TEXT = 0xFFF4F4F6;
    public static int TEXT_2 = 0xFFA1A1AA;
    public static int TEXT_3 = 0xFF6B6B76;
    public static final int TEXT_DARK = 0xFF0B0B0E;

    public static final int RED = 0xFFFF5A5F;
    public static final int GREEN = 0xFF34D399;
    public static final int ORANGE = 0xFFFBBF24;

    private static ThemeDef current = ThemeDef.BUILT_IN[0];
    private static boolean light;
    private static int uiAccent = ThemeDef.BUILT_IN[0].accent();

    private Theme() {}

    public static ThemeDef current() {
        return current;
    }

    /** Recomputes the palette; cheap enough to call every frame. */
    public static void refresh() {
        OnixConfig c = OnixClient.CONFIG;
        ThemeDef def = c == null ? ThemeDef.BUILT_IN[0] : ThemeStore.byId(c.theme.get());
        ThemeDef editing = ThemeEditor.livePreview(def.id());
        if (editing != null) def = editing;
        current = def;
        light = c != null && c.lightMode.isOn();
        int a = def.accent();
        if (light) {
            // very light accents (Onyx) would vanish on white, so they turn dark instead
            float lum = 0.299f * red(a) + 0.587f * green(a) + 0.114f * blue(a);
            uiAccent = lum > 0.72f ? 0xFF18181B : lerpColor(a, 0xFF000000, 0.12f);
            int p = lerpColor(0xFFFAFAFB, a, 0.025f);
            SURFACE = withAlpha(p, 0xF6);
            SURFACE_SUNKEN = withAlpha(lerpColor(p, 0xFF000000, 0.035f), 0xFF);
            SURFACE_2 = withAlpha(lerpColor(p, 0xFF000000, 0.025f), 0xFF);
            SURFACE_3 = withAlpha(lerpColor(p, 0xFF000000, 0.055f), 0xFF);
            SURFACE_4 = withAlpha(lerpColor(p, 0xFF000000, 0.10f), 0xFF);
            BORDER = withAlpha(lerpColor(p, 0xFF000000, 0.08f), 0xFF);
            BORDER_STRONG = withAlpha(lerpColor(p, 0xFF000000, 0.16f), 0xFF);
            BACKDROP = withAlpha(0xFF0B0B0F, 0x70);
            TEXT = 0xFF111114;
            TEXT_2 = 0xFF52525C;
            TEXT_3 = 0xFF8A8A94;
            return;
        }
        uiAccent = a;
        TEXT = 0xFFF4F4F6;
        TEXT_2 = 0xFFA1A1AA;
        TEXT_3 = 0xFF6B6B76;
        int p = def.panel();
        // Restrained: surfaces stay neutral, the accent only tints them slightly.
        SURFACE = withAlpha(p, 0xF4);
        SURFACE_SUNKEN = withAlpha(lerpColor(p, 0xFF000000, 0.35f), 0xFF);
        SURFACE_2 = withAlpha(lerpColor(lerpColor(p, a, 0.02f), 0xFFFFFFFF, 0.035f), 0xFF);
        SURFACE_3 = withAlpha(lerpColor(lerpColor(p, a, 0.04f), 0xFFFFFFFF, 0.065f), 0xFF);
        SURFACE_4 = withAlpha(lerpColor(lerpColor(p, a, 0.06f), 0xFFFFFFFF, 0.12f), 0xFF);
        BORDER = withAlpha(lerpColor(p, 0xFFFFFFFF, 0.075f), 0xFF);
        BORDER_STRONG = withAlpha(lerpColor(lerpColor(p, a, 0.08f), 0xFFFFFFFF, 0.14f), 0xFF);
        BACKDROP = withAlpha(lerpColor(0xFF020203, p, 0.4f), 0xB8);
    }

    /** Accent for menus (darkened in light mode when the theme accent is very light). */
    public static int accent() {
        return uiAccent;
    }

    /** The theme's own accent, for the HUD and the world (independent of light mode). */
    public static int themeAccent() {
        return current.accent();
    }

    public static boolean isLight() {
        return light;
    }

    /** Corner radius adjusted to the chosen button style. */
    public static int corner(int radius) {
        OnixConfig c = OnixClient.CONFIG;
        if (c == null) return radius;
        return switch (c.buttonStyle.get()) {
            case "Square" -> Math.min(radius, 2);
            case "Pill" -> radius * 4;
            default -> radius;
        };
    }

    /** Accent mixed towards the panel color, e.g. for pressed states. */
    public static int accentMuted() {
        return lerpColor(accent(), current.panel(), 0.35f);
    }

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    public static Font font() {
        return Minecraft.getInstance().font;
    }

    // ================================================================== typography

    public static MutableComponent ui(String s) {
        return Component.literal(s).withStyle(st -> st.withFont(FONT_UI));
    }

    public static MutableComponent bold(String s) {
        return Component.literal(s).withStyle(st -> st.withFont(FONT_BOLD));
    }

    public static MutableComponent display(String s) {
        return Component.literal(s).withStyle(st -> st.withFont(FONT_DISPLAY));
    }

    public static int width(Component t) {
        return font().width(t);
    }

    public static int width(String s) {
        return font().width(ui(s));
    }

    public static int boldWidth(String s) {
        return font().width(bold(s));
    }

    public static int text(GuiGraphics ctx, String s, int x, int y, int color) {
        return ctx.drawString(font(), ui(s), x, y, color, false);
    }

    public static int textBold(GuiGraphics ctx, String s, int x, int y, int color) {
        return ctx.drawString(font(), bold(s), x, y, color, false);
    }

    public static void textCentered(GuiGraphics ctx, String s, int cx, int y, int color) {
        Component t = ui(s);
        ctx.drawString(font(), t, cx - font().width(t) / 2, y, color, false);
    }

    public static void textRight(GuiGraphics ctx, String s, int right, int y, int color) {
        Component t = ui(s);
        ctx.drawString(font(), t, right - font().width(t), y, color, false);
    }

    /** Shortens text with "…" when it does not fit. */
    public static String ellipsize(String s, int maxWidth) {
        if (width(s) <= maxWidth) return s;
        String out = s;
        while (!out.isEmpty() && width(out + "…") > maxWidth) out = out.substring(0, out.length() - 1);
        return out.trim() + "…";
    }

    /** Small text (75 %) for captions and hints. */
    public static void small(GuiGraphics ctx, String s, int x, int y, int color) {
        scaled(ctx, ui(s), x, y, 0.75f, color);
    }

    public static void smallBold(GuiGraphics ctx, String s, int x, int y, int color) {
        scaled(ctx, bold(s), x, y, 0.75f, color);
    }

    public static int smallWidth(String s) {
        return (int) Math.ceil(width(s) * 0.75f);
    }

    /** Section label in capitals. */
    public static void label(GuiGraphics ctx, String s, int x, int y, int color) {
        StringBuilder spaced = new StringBuilder();
        for (char ch : s.toUpperCase().toCharArray()) spaced.append(ch).append(' ');
        scaled(ctx, bold(spaced.toString().trim()), x, y, 0.62f, color);
    }

    public static void scaled(GuiGraphics ctx, Component t, int x, int y, float scale, int color) {
        ctx.pose().pushPose();
        ctx.pose().translate(x, y, 0);
        ctx.pose().scale(scale, scale, 1f);
        ctx.drawString(font(), t, 0, 0, color, false);
        ctx.pose().popPose();
    }

    public static void scaled(GuiGraphics ctx, String s, int x, int y, float scale, int color, boolean shadow) {
        ctx.pose().pushPose();
        ctx.pose().translate(x, y, 0);
        ctx.pose().scale(scale, scale, 1f);
        ctx.drawString(font(), ui(s), 0, 0, color, shadow);
        ctx.pose().popPose();
    }

    // ================================================================== shapes

    /** Rectangle with (pixel-accurate) rounded corners. */
    public static void round(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, int color) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            ctx.fill(x1, y1, x2, y2, color);
            return;
        }
        ctx.fill(x1, y1 + r, x2, y2 - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            ctx.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);
            ctx.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);
        }
    }

    /** Filled shape with a 1px border. */
    public static void card(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, int fill, int border) {
        round(ctx, x1, y1, x2, y2, radius, border);
        round(ctx, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, radius - 1), fill);
    }

    /** Soft shadow under windows. */
    public static void shadow(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius) {
        for (int i = 6; i >= 1; i--) {
            round(ctx, x1 - i, y1 - i + 3, x2 + i, y2 + i + 3, radius + i, withAlpha(0xFF000000, 10 + (6 - i) * 4));
        }
    }

    /** Window: shadow, border, surface and a faint highlight along the top edge. */
    public static void window(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius) {
        shadow(ctx, x1, y1, x2, y2, radius);
        card(ctx, x1, y1, x2, y2, radius, SURFACE, BORDER_STRONG);
        ctx.fill(x1 + radius, y1 + 1, x2 - radius, y1 + 2, withAlpha(0xFFFFFFFF, 14));
    }

    /** Glass panel (bloom style) with a soft accent glow. */
    public static void glass(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, float glow) {
        int a = accent();
        for (int i = 3; i >= 1; i--) {
            round(ctx, x1 - i, y1 - i, x2 + i, y2 + i, radius + i, withAlpha(a, (int) ((6 + (3 - i) * 6) * glow)));
        }
        card(ctx, x1, y1, x2, y2, radius, SURFACE, withAlpha(lerpColor(BORDER_STRONG, a, 0.35f), 0xFF));
    }

    public static void outline(GuiGraphics ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1 + 1, y1, x2 - 1, y1 + 1, color);
        ctx.fill(x1 + 1, y2 - 1, x2 - 1, y2, color);
        ctx.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        ctx.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    /**
     * Colored screen edges that fade towards the center (low health, damage flash).
     * @param size edge thickness as a fraction of the shorter screen side
     */
    public static void vignette(GuiGraphics ctx, int color, float alpha, float size) {
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        int edge = Math.max(8, Math.round(Math.min(w, h) * size));
        int steps = 16;
        for (int i = 0; i < steps; i++) {
            float t = 1f - i / (float) steps;
            int a = Math.round(255 * alpha * t * t * 0.85f);
            int c = withAlpha(color, Math.min(255, a));
            int o = edge * i / steps;
            int o2 = edge * (i + 1) / steps;
            ctx.fill(0, o, w, o2, c);
            ctx.fill(0, h - o2, w, h - o, c);
            ctx.fill(o, o2, o2, h - o2, c);
            ctx.fill(w - o2, o2, w - o, h - o2, c);
        }
    }

    public static void hairline(GuiGraphics ctx, int x1, int x2, int y) {
        ctx.fill(x1, y, x2, y + 1, BORDER);
    }

    /** Toggle switch, t = 0..1 (animated). */
    public static void toggle(GuiGraphics ctx, int x, int y, float t) {
        int w = 18;
        int h = 10;
        round(ctx, x, y, x + w, y + h, 5, lerpColor(SURFACE_4, accent(), t));
        int knob = x + 1 + Math.round((w - 10) * t);
        round(ctx, knob, y + 1, knob + 8, y + h - 1, 4, lerpColor(TEXT_2, onAccent(), t));
    }

    /** Readable text color on top of the accent. */
    public static int onAccent() {
        int a = accent();
        float lum = 0.299f * red(a) + 0.587f * green(a) + 0.114f * blue(a);
        return lum > 0.6f ? TEXT_DARK : 0xFFFFFFFF;
    }

    // ================================================================== colors

    public static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    public static int fade(int color, float factor) {
        int a = (color >>> 24) & 0xFF;
        return withAlpha(color, Math.round(a * Math.max(0, Math.min(1, factor))));
    }

    public static int lerpColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static float red(int c) { return ((c >> 16) & 0xFF) / 255f; }
    public static float green(int c) { return ((c >> 8) & 0xFF) / 255f; }
    public static float blue(int c) { return (c & 0xFF) / 255f; }

    public static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }
}
