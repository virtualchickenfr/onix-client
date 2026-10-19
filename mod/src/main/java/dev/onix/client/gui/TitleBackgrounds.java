package dev.onix.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.onix.client.OnixClient;
import dev.onix.client.config.ConfigFiles;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Title screen backgrounds: the built-in set plus any PNG the player drops into
 * config/onix/backgrounds. Supports a slideshow with crossfade.
 */
public final class TitleBackgrounds {
    public record Background(String id, String name, Identifier texture, int width, int height, boolean custom) {}

    private static final List<Background> BUILT_IN = List.of(
            builtIn("carbon", "Carbon", 960, 540),
            builtIn("onix", "Onix Glow", 960, 540),
            builtIn("dusk", "Dusk Ridge", 1280, 720),
            builtIn("aurora", "Aurora", 1280, 720),
            builtIn("golden_hour", "Golden Hour", 1280, 720),
            builtIn("midnight_sea", "Midnight Sea", 1280, 720),
            builtIn("misty_pines", "Misty Pines", 1280, 720),
            builtIn("cherry_grove", "Cherry Grove", 1280, 720),
            builtIn("dunes", "Dunes", 1280, 720),
            builtIn("glacier", "Glacier", 1280, 720),
            builtIn("lavender", "Lavender", 1280, 720),
            builtIn("nether", "Nether", 1280, 720),
            builtIn("the_end", "The End", 1280, 720),
            builtIn("deep_space", "Deep Space", 1280, 720));

    private static final Map<Path, Background> LOADED = new HashMap<>();
    private static List<Background> custom = List.of();
    private static long lastScan;

    // slideshow state
    private static Background shown;
    private static Background previous;
    private static long switchedAt;

    private TitleBackgrounds() {}

    private static Background builtIn(String id, String name, int w, int h) {
        return new Background(id, name, Identifier.of("onix", "textures/gui/backgrounds/" + id + ".png"), w, h, false);
    }

    public static Path folder() {
        return ConfigFiles.dir().resolve("backgrounds");
    }

    public static void openFolder() {
        try {
            Files.createDirectories(folder());
            Util.getOperatingSystem().open(folder().toFile());
        } catch (Exception e) {
            OnixClient.LOGGER.warn("Could not open {}", folder(), e);
        }
    }

    public static List<Background> all() {
        scanIfStale();
        List<Background> list = new ArrayList<>(BUILT_IN);
        list.addAll(custom);
        return list;
    }

    public static Background byId(String id) {
        for (Background b : all()) if (b.id().equals(id)) return b;
        return BUILT_IN.get(0);
    }

    // ------------------------------------------------------------------ custom images

    private static void scanIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastScan < 3000) return;
        lastScan = now;
        List<Background> found = new ArrayList<>();
        if (Files.isDirectory(folder())) {
            try (Stream<Path> files = Files.list(folder())) {
                files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).sorted().forEach(p -> {
                    Background b = LOADED.computeIfAbsent(p, TitleBackgrounds::load);
                    if (b != null) found.add(b);
                });
            } catch (Exception e) {
                OnixClient.LOGGER.warn("Could not list backgrounds", e);
            }
        }
        custom = found;
    }

    private static Background load(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            String name = file.getFileName().toString().replaceFirst("(?i)\\.png$", "");
            String key = "custom_" + Integer.toHexString(file.toAbsolutePath().toString().hashCode());
            Identifier id = Identifier.of("onix", "backgrounds/" + key);
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
            return new Background(key, name, id, image.getWidth(), image.getHeight(), true);
        } catch (Exception e) {
            OnixClient.LOGGER.warn("Could not load background {} (PNG images only)", file, e);
            return null;
        }
    }

    // ------------------------------------------------------------------ drawing

    private static Background target() {
        var c = OnixClient.CONFIG;
        if (c == null) return BUILT_IN.get(0);
        if (!c.backgroundSlideshow.isOn()) return byId(c.titleBackground.get());
        List<Background> list = all();
        long period = (long) (c.slideshowSeconds.get() * 1000);
        int index = (int) ((System.currentTimeMillis() / period) % list.size());
        return list.get(index);
    }

    /** Draws the background "cover"-scaled, with optional drift/parallax and crossfades. */
    public static void draw(DrawContext ctx, int width, int height, int mouseX, int mouseY, float time) {
        Background now = target();
        long ms = System.currentTimeMillis();
        if (shown == null) {
            shown = now;
            switchedAt = ms - 10_000;
        } else if (!now.id().equals(shown.id())) {
            previous = shown;
            shown = now;
            switchedAt = ms;
        }
        float fade = Math.min(1f, (ms - switchedAt) / 1200f);
        boolean motion = OnixClient.CONFIG == null || OnixClient.CONFIG.backgroundMotion.isOn();
        RenderSystem.enableBlend();
        if (previous != null && fade < 1f) drawCover(ctx, previous, width, height, mouseX, mouseY, time, motion, 1f);
        drawCover(ctx, shown, width, height, mouseX, mouseY, time, motion, previous == null ? 1f : fade);
        RenderSystem.disableBlend();
        if (fade >= 1f) previous = null;
    }

    private static void drawCover(DrawContext ctx, Background b, int width, int height, int mouseX, int mouseY, float time, boolean motion, float alpha) {
        float scale = Math.max(width / (float) b.width(), height / (float) b.height()) * (motion ? 1.06f : 1f);
        int w = Math.round(b.width() * scale);
        int h = Math.round(b.height() * scale);
        int ox = (width - w) / 2;
        int oy = (height - h) / 2;
        if (motion) {
            ox += Math.round((float) Math.sin(time * 0.05f) * width * 0.012f) - Math.round((mouseX - width / 2f) * 0.008f);
            oy -= Math.round((mouseY - height / 2f) * 0.008f);
        }
        ctx.setShaderColor(1f, 1f, 1f, alpha);
        ctx.drawTexture(b.texture(), ox, oy, w, h, 0f, 0f, b.width(), b.height(), b.width(), b.height());
        ctx.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** A small preview tile for the picker. */
    public static void drawThumbnail(DrawContext ctx, Background b, int x, int y, int w, int h) {
        float scale = Math.max(w / (float) b.width(), h / (float) b.height());
        float srcW = w / scale;
        float srcH = h / scale;
        float u = (b.width() - srcW) / 2f;
        float v = (b.height() - srcH) / 2f;
        RenderSystem.enableBlend();
        ctx.drawTexture(b.texture(), x, y, w, h, u, v, Math.round(srcW), Math.round(srcH), b.width(), b.height());
        RenderSystem.disableBlend();
    }
}
