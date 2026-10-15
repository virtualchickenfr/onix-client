package dev.onix.client.cosmetics;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cape textures. The first capes are PNG files; every other cape is painted from its pattern and
 * palette into a 256 x 128 texture (the vanilla 64 x 32 layout at four times the resolution) the first time it
 * is needed, with the detailed finish of {@link CapeFinish}. Animated capes repaint it when the frame changes.
 */
public final class CapeTextures {
    private static final int TW = 256;
    private static final int TH = 128;
    private static final int MAX_CACHED = 200;
    /** A registered cape texture and the animation frame it shows. */
    private static final class Entry {
        final Identifier id;
        final NativeImageBackedTexture texture;
        int frame;

        Entry(Identifier id, NativeImageBackedTexture texture, int frame) {
            this.id = id;
            this.texture = texture;
            this.frame = frame;
        }
    }

    /** item id → registered texture, oldest first (least recently used). */
    private static final Map<String, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);

    private CapeTextures() {}

    /** The texture for a cape, or null if it cannot be made right now (not on the render thread). */
    public static Identifier get(CosmeticCatalog.Item item) {
        if (item == null || !item.isCape()) return null;
        if (item.texture() != null) return Identifier.of("onix", "textures/cosmetics/capes/" + item.texture() + ".png");
        if (!RenderSystem.isOnRenderThread()) {
            Entry e = CACHE.get(item.id());
            return e == null ? null : e.id;
        }
        int frame = frame(item);
        Entry e = CACHE.get(item.id());
        if (e != null) {
            if (e.frame != frame && e.texture.getImage() != null) {
                // animated cape: paint the new frame into the same texture
                paintInto(e.texture.getImage(), item, frame);
                e.texture.upload();
                e.frame = frame;
            }
            return e.id;
        }
        Identifier id = Identifier.of("onix", "cape/" + item.id());
        NativeImage image = new NativeImage(TW, TH, true);
        paintInto(image, item, frame);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
        CACHE.put(item.id(), new Entry(id, texture, frame));
        trim();
        return id;
    }

    /** Current animation frame of a cape: the same clock everywhere (menu, world, launcher). */
    static int frame(CosmeticCatalog.Item item) {
        if (item.pattern() == null) return 0;
        int[] anim = CosmeticCatalog.capeAnim(item.pattern());
        if (anim[0] <= 1) return 0;
        long ticks = System.currentTimeMillis() / 50L;
        return (int) ((ticks / anim[1]) % anim[0]);
    }

    private static void trim() {
        if (CACHE.size() <= MAX_CACHED) return;
        Identifier selected = null;
        CosmeticCatalog.Item cape = Cosmetics.shown(Cosmetics.Slot.CAPE);
        if (cape != null && CACHE.containsKey(cape.id())) selected = CACHE.get(cape.id()).id;
        Iterator<Map.Entry<String, Entry>> it = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_CACHED && it.hasNext()) {
            Identifier id = it.next().getValue().id;
            if (id.equals(selected)) continue;
            MinecraftClient.getInstance().getTextureManager().destroyTexture(id);
            it.remove();
        }
    }

    /** Paints the full cape texture: detailed outside face, darker inside face, edges, and the elytra area. */
    static void paintInto(NativeImage img, CosmeticCatalog.Item item, int frame) {
        int[] palette = CosmeticCatalog.palette(item.palette());
        int[] face = CapeFinish.face(CosmeticCatalog.capeIndices(item.pattern(), frame), palette, item.tier().ordinal());
        int w = CapeFinish.W;
        int h = CapeFinish.H;
        int edge = abgr(palette[Math.min(5, palette.length - 1)]);
        for (int y = 0; y < TH; y++) for (int x = 0; x < TW; x++) img.setColor(x, y, edge);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = face[y * w + x];
                img.setColor(4 + x, 4 + y, abgr(c));                              // outside (vanilla x 1..11, y 1..17)
                img.setColor(48 + (w - 1 - x), 4 + y, abgr(shade(c, 0.62f)));      // inside, mirrored (vanilla x 12..22)
            }
        }
        // elytra (vanilla x 22..46, y 0..22): the face, repeated
        for (int y = 0; y < 88; y++) {
            for (int x = 88; x < 184; x++) img.setColor(x, y, abgr(face[(y % h) * w + ((x - 88) % w)]));
        }
    }

    /** Draws the outside face of a cape into a rectangle (keeps the 10:16 shape, pixel art stays sharp). */
    public static void drawFace(DrawContext ctx, CosmeticCatalog.Item item, int x, int y, int width, int height) {
        Identifier tex = get(item);
        if (tex == null) return;
        if (item.texture() != null) ctx.drawTexture(tex, x, y, width, height, 1f, 1f, 10, 16, 64, 32);
        else ctx.drawTexture(tex, x, y, width, height, 4f, 4f, CapeFinish.W, CapeFinish.H, TW, TH);
    }

    static int shade(int argb, float f) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * f));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * f));
        int b = Math.min(255, Math.round((argb & 0xFF) * f));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** NativeImage stores pixels as ABGR. */
    private static int abgr(int argb) {
        return (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16);
    }
}
