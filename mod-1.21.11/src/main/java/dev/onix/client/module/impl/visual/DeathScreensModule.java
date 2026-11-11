package dev.onix.client.module.impl.visual;

import dev.onix.client.gui.SoftShapes;
import dev.onix.client.gui.Theme;
import dev.onix.client.mixin.ScreenTitleAccessor;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.TextSetting;
import java.util.Random;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Styled death screens: an animated scene behind the respawn buttons and a big title of its own (the vanilla title is
 * hidden). Styles: Cinematic, Glitch, Arcade, Energy and Ink. Only you see them.
 */
public class DeathScreensModule extends Module {
    public static final String[] STYLES = {"Cinematic", "Glitch", "Arcade", "Energy", "Ink"};

    private final ModeSetting style = add(new ModeSetting("style", "Style", STYLES[0], STYLES[1], STYLES[2], STYLES[3], STYLES[4])
            .describe("How the screen looks when you die."));
    private final TextSetting text = add(new TextSetting("text", "Title", "", 24)
            .describe("Your own title; empty uses the style's own words."));
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFF4D5E));
    private final BoolSetting preview = add(new BoolSetting("preview", "Show Preview", false)
            .describe("Opens the death screen once so you can look at the style."));

    private long openedAt;

    public DeathScreensModule() {
        super("deathscreens", "Death Screens", "Animated death screens in five styles: cinematic, glitch, arcade, energy and ink.", Category.VISUAL, false);
        tags("death", "died", "respawn", "screen", "game over");
    }

    @Override
    public void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (!isEnabled() || !(screen instanceof DeathScreen)) return;
            openedAt = System.currentTimeMillis();
            ((ScreenTitleAccessor) screen).onix$setTitle(Component.empty());
            ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, delta) -> background(ctx, s.width, s.height));
            ScreenEvents.afterRender(screen).register((s, ctx, mx, my, delta) -> foreground(ctx, s.width, s.height));
        });
    }

    @Override
    public void onTick() {
        if (preview.isOn()) {
            preview.set(false);
            openPreview();
        }
    }

    /** A screen that only shows the style (Escape closes it). */
    public void openPreview() {
        openedAt = System.currentTimeMillis();
        mc().setScreen(new Screen(Component.literal("Death Screen Preview")) {
            @Override
            public void render(GuiGraphics ctx, int mx, int my, float delta) {
                ctx.fill(0, 0, width, height, 0xFF101014);
                background(ctx, width, height);
                foreground(ctx, width, height);
                Theme.textCentered(ctx, "Preview – Esc to close", width / 2, height - 20, 0x99FFFFFF);
            }

            @Override
            public boolean isPauseScreen() {
                return false;
            }
        });
    }

    public void setStyle(String s) {
        style.set(s);
    }

    private float t() {
        return (System.currentTimeMillis() - openedAt) / 1000f;
    }

    private String title(String fallback) {
        return text.get().isBlank() ? fallback : text.get();
    }

    private static int a(int rgb, float alpha) {
        int al = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255);
        return al << 24 | (rgb & 0xFFFFFF);
    }

    private static float ease(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return 1f - (1f - x) * (1f - x) * (1f - x);
    }

    // ------------------------------------------------------------------ behind the buttons

    private void background(GuiGraphics ctx, int w, int h) {
        float t = t();
        int c = color.color();
        switch (style.get()) {
            case "Cinematic" -> {
                // the world fades to near black and white, then bars close in like a film ending
                ctx.fill(0, 0, w, h, a(0x0B0B0E, 0.55f * ease(t / 1.5f)));
                SoftShapes.vignette(ctx, 0, 0, w, h, a(0x000000, 0.9f * ease(t / 2f)));
                int bar = Math.round(h * 0.13f * ease(t / 1.1f));
                ctx.fill(0, 0, w, bar, 0xFF000000);
                ctx.fill(0, h - bar, w, h, 0xFF000000);
            }
            case "Glitch" -> {
                ctx.fill(0, 0, w, h, a(0x05060A, 0.7f));
                Random r = new Random((long) (t * 12));
                for (int i = 0; i < 9; i++) {
                    int y = r.nextInt(Math.max(1, h));
                    int hh = 2 + r.nextInt(10);
                    int shift = r.nextInt(40) - 20;
                    ctx.fill(shift, y, w + shift, y + hh, a(i % 3 == 0 ? 0xFF2A55 : i % 3 == 1 ? 0x2AE5FF : 0xFFFFFF, 0.12f + r.nextFloat() * 0.15f));
                }
                for (int y = 0; y < h; y += 3) ctx.fill(0, y, w, y + 1, 0x22000000);
            }
            case "Arcade" -> {
                ctx.fill(0, 0, w, h, a(0x0A0820, 0.85f));
                int grid = 16;
                float scroll = (t * 20) % grid;
                for (int x = 0; x < w; x += grid) ctx.fill(x, 0, x + 1, h, 0x1A6A5CFF);
                for (float y = -grid + scroll; y < h; y += grid) ctx.fill(0, (int) y, w, (int) y + 1, 0x1A6A5CFF);
            }
            case "Energy" -> {
                ctx.fill(0, 0, w, h, a(0x07060C, 0.75f));
                int cx = w / 2, cy = h / 3;
                for (int i = 0; i < 4; i++) {
                    float p = ((t * 0.6f) + i * 0.25f) % 1f;
                    int r = Math.round(20 + p * Math.max(w, h) * 0.7f);
                    ring(ctx, cx, cy, r, 2, a(c, (1f - p) * 0.5f));
                }
                SoftShapes.glow(ctx, cx, cy, 90, 60, a(c, 0.35f));
            }
            default -> { // Ink: dark blots grow in from the corners
                ctx.fill(0, 0, w, h, a(0x000000, 0.35f));
                float g = ease(t / 1.4f);
                int[][] spots = {{0, 0}, {w, 0}, {0, h}, {w, h}, {w / 2, -20}, {w / 2, h + 20}};
                for (int i = 0; i < spots.length; i++) {
                    int r = Math.round((Math.max(w, h) * 0.32f + i * 9) * g);
                    disc(ctx, spots[i][0], spots[i][1], r, 0xF2050507);
                }
                ctx.fill(0, h / 2 - 22, w, h / 2 + 22, a(c, 0.0f));
            }
        }
    }

    // ------------------------------------------------------------------ the title (over everything)

    private void foreground(GuiGraphics ctx, int w, int h) {
        float t = t();
        int c = color.color();
        int y = h / 4 - 8;
        switch (style.get()) {
            case "Cinematic" -> {
                String s = spaced(title("YOU FELL"));
                float in = ease((t - 0.4f) / 1.6f);
                title(ctx, Theme.display(s), w / 2, y, 2.6f + 0.25f * (1f - in), a(0xF2F2F2, in));
                Theme.textCentered(ctx, "— the end of this chapter —", w / 2, y + 34, a(0xBFBFBF, ease((t - 1.4f) / 1f)));
            }
            case "Glitch" -> {
                String s = title("SIGNAL LOST");
                Random r = new Random((long) (t * 20));
                int jx = r.nextInt(5) - 2;
                title(ctx, Theme.display(s), w / 2 - 2 + jx, y, 3f, a(0xFF2A55, 0.8f));
                title(ctx, Theme.display(s), w / 2 + 2 - jx, y, 3f, a(0x2AE5FF, 0.8f));
                title(ctx, Theme.display(s), w / 2, y, 3f, 0xFFFFFFFF);
                if (r.nextInt(6) == 0) ctx.fill(0, y + r.nextInt(30), w, y + r.nextInt(30) + 3, 0x66FFFFFF);
            }
            case "Arcade" -> {
                boolean blink = ((int) (t * 2.5f)) % 2 == 0;
                title(ctx, Component.literal(title("GAME OVER")), w / 2, y, 4f, blink ? 0xFFFFE14D : 0xFFFF6A3D);
                int left = Math.max(0, 9 - (int) t);
                Theme.textCentered(ctx, "CONTINUE?  " + left, w / 2, y + 44, 0xFFFFFFFF);
            }
            case "Energy" -> {
                float pulse = 1f + 0.05f * (float) Math.sin(t * 6);
                title(ctx, Theme.display(title("DEFEATED")), w / 2, y, 3.2f * pulse, c);
                Theme.textCentered(ctx, "Get back up.", w / 2, y + 40, 0xCCFFFFFF);
            }
            default -> {
                float in = ease((t - 0.3f) / 0.8f);
                title(ctx, Theme.display(title("ELIMINATED")), w / 2, y, 3.4f, a(c, in));
            }
        }
    }

    private static String spaced(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(ch).append(' ');
        return b.toString().trim();
    }

    /** Centred, scaled text. */
    private static void title(GuiGraphics ctx, Component c, int cx, int y, float scale, int color) {
        if ((color >>> 24) < 6) return;
        int width = Theme.width(c);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx - width * scale / 2f, y);
        ctx.pose().scale(scale, scale);
        ctx.drawString(Theme.font(), c, 0, 0, color, true);
        ctx.pose().popMatrix();
    }

    private static void disc(GuiGraphics ctx, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy += 2) {
            int dx = (int) Math.sqrt((double) r * r - (double) dy * dy);
            ctx.fill(cx - dx, cy + dy, cx + dx, cy + dy + 2, color);
        }
    }

    private static void ring(GuiGraphics ctx, int cx, int cy, int r, int thick, int color) {
        int steps = Math.max(24, r);
        for (int i = 0; i < steps; i++) {
            double ang = Math.PI * 2 * i / steps;
            int x = cx + (int) (Math.cos(ang) * r), yy = cy + (int) (Math.sin(ang) * r);
            ctx.fill(x, yy, x + thick, yy + thick, color);
        }
    }
}
