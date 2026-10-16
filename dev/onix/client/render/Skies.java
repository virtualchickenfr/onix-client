package dev.onix.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.onix.client.OnixClient;
import dev.onix.client.config.ConfigFiles;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Sky boxes for the Custom Sky module: the built-in set plus PNGs in config/onix/skies.
 * Each sky is one image laid out as 3 x 2 cube faces (see /tools/skies.py in the repository).
 */
public final class Skies {
    /**
     * @param horizon average color near the horizon, used to blend the fog into the sky
     * @param thumbnail preview image, or null to preview a side face of the atlas
     */
    public record Sky(String name, ResourceLocation texture, ResourceLocation thumbnail, int horizon, boolean custom) {}

    private static final List<Sky> BUILT_IN = List.of(
            builtIn("clear_day", "Clear Day", 0xC7DFF2),
            builtIn("golden_hour", "Golden Hour", 0xFFD292),
            builtIn("sunset", "Sunset Bloom", 0xFF947A),
            builtIn("pastel_dawn", "Pastel Dawn", 0xFFE3D5),
            builtIn("cotton_candy", "Cotton Candy", 0xFDCEE5),
            builtIn("morning_mist", "Morning Mist", 0xE0E6EA),
            builtIn("arctic", "Arctic Day", 0xE3F4FF),
            builtIn("overcast", "Overcast", 0xA9AFB4),
            builtIn("storm", "Thunderstorm", 0x425057),
            builtIn("lavender_dusk", "Lavender Dusk", 0xEDABCE),
            builtIn("ember", "Ember Sky", 0xC75325),
            builtIn("starry_night", "Starry Night", 0x17213E),
            builtIn("midnight_clouds", "Midnight Clouds", 0x1A2842),
            builtIn("aurora", "Aurora", 0x0C1D2F),
            builtIn("emerald_night", "Emerald Night", 0x0B2C24),
            builtIn("nebula", "Nebula", 0x170B24),
            builtIn("deep_space", "Deep Space", 0x020208),
            builtIn("blood_moon", "Blood Moon", 0x480C0D));

    private static final Map<Path, Sky> LOADED = new HashMap<>();
    private static final Set<ResourceLocation> FILTERED = new HashSet<>();
    private static List<Sky> custom = List.of();
    private static long lastScan;

    private Skies() {}

    private static Sky builtIn(String id, String name, int horizon) {
        return new Sky(name, ResourceLocation.fromNamespaceAndPath("onix", "textures/sky/" + id + ".png"),
                ResourceLocation.fromNamespaceAndPath("onix", "textures/gui/skies/" + id + ".png"), 0xFF000000 | horizon, false);
    }

    public static Path folder() {
        return ConfigFiles.dir().resolve("skies");
    }

    public static void openFolder() {
        try {
            Files.createDirectories(folder());
            Util.getPlatform().openFile(folder().toFile());
        } catch (Exception e) {
            OnixClient.LOGGER.warn("Could not open {}", folder(), e);
        }
    }

    public static List<Sky> all() {
        scanIfStale();
        List<Sky> list = new ArrayList<>(BUILT_IN);
        list.addAll(custom);
        return list;
    }

    public static String[] names() {
        return all().stream().map(Sky::name).toArray(String[]::new);
    }

    public static Sky byName(String name) {
        for (Sky s : all()) if (s.name().equals(name)) return s;
        return null;
    }

    /** Smooth filtering looks much better for skies than Minecraft's default pixel look. */
    public static void ensureFiltered(ResourceLocation texture) {
        if (FILTERED.contains(texture)) return;
        AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (t != null) {
            t.setFilter(true, false);
            FILTERED.add(texture);
        }
    }

    private static void scanIfStale() {
        long now = System.currentTimeMillis();
        if (now - lastScan < 3000) return;
        lastScan = now;
        List<Sky> found = new ArrayList<>();
        if (Files.isDirectory(folder())) {
            try (Stream<Path> files = Files.list(folder())) {
                files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).sorted().forEach(p -> {
                    Sky s = LOADED.computeIfAbsent(p, Skies::load);
                    if (s != null) found.add(s);
                });
            } catch (Exception e) {
                OnixClient.LOGGER.warn("Could not list skies", e);
            }
        }
        custom = found;
    }

    private static Sky load(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            if (image.getWidth() * 2 != image.getHeight() * 3) {
                OnixClient.LOGGER.warn("Sky {} must be 3:2 (3 x 2 cube faces), got {}x{}", file, image.getWidth(), image.getHeight());
                image.close();
                return null;
            }
            int horizon = averageRow(image, image.getHeight() / 4);
            String name = file.getFileName().toString().replaceFirst("(?i)\\.png$", "");
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("onix", "skies/custom_" + Integer.toHexString(file.toAbsolutePath().toString().hashCode()));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            return new Sky(name, id, null, horizon, true);
        } catch (Exception e) {
            OnixClient.LOGGER.warn("Could not load sky {} (PNG images only)", file, e);
            return null;
        }
    }

    /** Average color of one row of the first side face (NativeImage stores ABGR). */
    private static int averageRow(NativeImage image, int y) {
        int face = image.getWidth() / 3;
        long r = 0, g = 0, b = 0;
        for (int x = face; x < face * 2; x++) {
            int abgr = image.getPixelRGBA(x, y);
            r += abgr & 0xFF;
            g += (abgr >> 8) & 0xFF;
            b += (abgr >> 16) & 0xFF;
        }
        return 0xFF000000 | (int) (r / face) << 16 | (int) (g / face) << 8 | (int) (b / face);
    }
}
