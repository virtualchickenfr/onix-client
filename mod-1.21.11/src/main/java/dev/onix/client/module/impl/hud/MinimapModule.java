package dev.onix.client.module.impl.hud;

import com.mojang.blaze3d.platform.NativeImage;
import dev.onix.client.OnixClient;
import dev.onix.client.gui.GuiTriangles;
import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * A minimap: the terrain around you from above in map colours with height shading, turning with you (or north up),
 * with players, mobs and your waypoints on it. Terrain is read from the chunks the game already has loaded, a few
 * chunks per frame and cached, so it costs next to nothing; in the Nether it maps the floor you are on.
 */
public class MinimapModule extends HudModule {
    private static final int N = 192;              // texture size in blocks
    private static final long REFRESH_MS = 4000;   // how old a cached chunk may get before it is read again

    private final NumberSetting size = add(new NumberSetting("size", "Size", 110, 60, 220, 2).unit("px"));
    private final NumberSetting zoom = add(new NumberSetting("zoom", "Zoom", 1, 0.5, 3, 0.1).unit("x")
            .describe("Pixels per block – higher shows a smaller area bigger."));
    private final BoolSetting rotate = add(new BoolSetting("rotate", "Turn With View", true).describe("Off: north is always up."));
    private final BoolSetting players = add(new BoolSetting("players", "Show Players", true));
    private final BoolSetting mobs = add(new BoolSetting("mobs", "Show Mobs", false).describe("Hostile mobs red, others grey."));
    private final BoolSetting waypoints = add(new BoolSetting("waypoints", "Show Waypoints", true));
    private final BoolSetting coords = add(new BoolSetting("coords", "Coordinates Below", true));
    private final ColorSetting arrowColor = add(ColorSetting.accent("arrowColor", "Your Arrow"));
    private final dev.onix.client.setting.KeybindSetting mapKey = add(new dev.onix.client.setting.KeybindSetting("mapKey", "World Map Key", 77)
            .describe("Opens the big map of everything you explored (default M)."));

    /** Explored chunks per server and dimension, so the map is still there when you come back. */
    private final java.util.Map<String, Long2ObjectOpenHashMap<int[]>> worlds = new java.util.HashMap<>();
    private final java.util.Map<String, Long2LongOpenHashMap> worldsReadAt = new java.util.HashMap<>();
    private Long2ObjectOpenHashMap<int[]> chunks = new Long2ObjectOpenHashMap<>();
    private Long2LongOpenHashMap readAt = new Long2LongOpenHashMap();
    private final Identifier textureId = Identifier.fromNamespaceAndPath("onix", "minimap");
    private DynamicTexture texture;
    private ClientLevel cachedLevel;
    private int originX = Integer.MIN_VALUE;
    private int originZ;
    private boolean dirty;

    public MinimapModule() {
        super("minimap", "Minimap", "A map of the area around you, with players and waypoints.", Category.HUD, false, Anchor.BOTTOM_RIGHT, -4, -4);
        tags("map", "radar", "navigation", "xaero", "journeymap");
        useRoundCorners();
    }

    @Override
    public int getWidth() {
        return size.getInt();
    }

    @Override
    public int getHeight() {
        return size.getInt() + (coords.isOn() ? 12 : 0);
    }

    @Override
    public void onTick() {
        if (mapKey.poll() && mc().screen == null && mc().level != null) mc().setScreen(new dev.onix.client.gui.WorldMapScreen(this));
    }

    @Override
    public void init() {
        // the world map fills in the background (a couple of chunks per tick), also while the minimap is hidden
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientLevel level = client.level;
            var player = client.player;
            if (level == null || player == null) return;
            int rd = Math.max(2, client.options.getEffectiveRenderDistance());
            scan(level, player.getBlockX(), player.getBlockY(), player.getBlockZ(), rd, 2, 30_000);
        });
    }

    /** Which map the chunks belong to: the server (or singleplayer world) and the dimension. */
    private String worldKey(ClientLevel level) {
        var server = mc().getCurrentServer();
        String where = server != null ? server.ip : mc().getSingleplayerServer() != null
                ? "sp:" + mc().getSingleplayerServer().getWorldData().getLevelName() : "?";
        return where + "|" + level.dimension().identifier();
    }

    /** The colour of a block on the map (0 = not explored). */
    public int colorAt(int x, int z) {
        int[] c = chunks.get((long) (x >> 4) << 32 | ((z >> 4) & 0xFFFFFFFFL));
        return c == null ? 0 : c[(z & 15) * 16 + (x & 15)];
    }

    /** The y of the top block for a waypoint placed on the map (the player's y when unknown). */
    public int heightAt(int x, int z) {
        ClientLevel level = mc().level;
        if (level == null || !level.hasChunk(x >> 4, z >> 4)) return mc().player == null ? 64 : mc().player.getBlockY();
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
    }

    public int arrowColor() {
        return arrowColor.color();
    }

    @Override
    protected void onDisable() {
        if (texture != null) {
            texture.close();
            mc().getTextureManager().release(textureId);
            texture = null;
        }
    }

    // ---------------------------------------------------------------- terrain

    /** The colours of one chunk (16x16, ARGB; 0 = unknown), with map style shading by height. */
    private int[] readChunk(ClientLevel level, int cx, int cz, int playerY, boolean cave) {
        int[] out = new int[256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] heights = new int[17 * 16];   // row 0 = the row north of the chunk, for shading
        for (int dz = -1; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = (cx << 4) + dx;
                int z = (cz << 4) + dz;
                heights[(dz + 1) * 16 + dx] = surface(level, pos, x, z, playerY, cave);
            }
        }
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = (cx << 4) + dx;
                int z = (cz << 4) + dz;
                int y = heights[(dz + 1) * 16 + dx];
                if (y == Integer.MIN_VALUE) continue;
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                MapColor color = state.getMapColor(level, pos);
                if (color == MapColor.NONE) continue;
                MapColor.Brightness b;
                if (color == MapColor.WATER) {
                    // deeper water is darker, like on a vanilla map
                    int depth = 0;
                    pos.setY(y - 1);
                    while (depth < 10 && level.getBlockState(pos).getMapColor(level, pos) == MapColor.WATER) {
                        depth++;
                        pos.setY(pos.getY() - 1);
                    }
                    b = depth > 6 ? MapColor.Brightness.LOW : depth > 2 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
                } else {
                    int north = heights[dz * 16 + dx];
                    b = north == Integer.MIN_VALUE || y == north ? MapColor.Brightness.NORMAL
                            : y > north ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                }
                out[dz * 16 + dx] = color.calculateARGBColor(b);
            }
        }
        return out;
    }

    /** The y of the top block at x/z: the heightmap above ground, the floor you stand on under a ceiling. */
    private static int surface(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z, int playerY, boolean cave) {
        if (!level.hasChunk(x >> 4, z >> 4)) return Integer.MIN_VALUE;
        if (!cave) return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        pos.set(x, playerY + 2, z);
        boolean air = level.getBlockState(pos).isAir();
        for (int y = playerY + 1; y > playerY - 32; y--) {
            pos.setY(y);
            boolean here = level.getBlockState(pos).isAir();
            if (air && !here) return y;
            air = here;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Reads the missing or oldest loaded chunks within radius (in chunks) around you, nearest first, at most budget of
     * them. The two rings around you refresh faster, so placed blocks show up quickly. True when something was read.
     */
    private boolean scan(ClientLevel level, int px, int py, int pz, int radius, int budget, long maxAge) {
        if (level != cachedLevel) {
            String key = worldKey(level);
            chunks = worlds.computeIfAbsent(key, k -> new Long2ObjectOpenHashMap<>());
            readAt = worldsReadAt.computeIfAbsent(key, k -> new Long2LongOpenHashMap());
            readAt.clear();                // re-read everything once: blocks may have changed while you were away
            cachedLevel = level;
            originX = Integer.MIN_VALUE;
        }
        boolean cave = level.dimensionType().hasCeiling();
        long now = System.currentTimeMillis();
        int pcx = px >> 4, pcz = pz >> 4;
        boolean read = false;
        for (int r = 0; r <= radius && budget > 0; r++) {
            for (int cx = pcx - r; cx <= pcx + r && budget > 0; cx++) {
                for (int cz = pcz - r; cz <= pcz + r && budget > 0; cz++) {
                    if (Math.max(Math.abs(cx - pcx), Math.abs(cz - pcz)) != r || !level.hasChunk(cx, cz)) continue;
                    long key = (long) cx << 32 | (cz & 0xFFFFFFFFL);
                    long at = readAt.getOrDefault(key, 0L);
                    long age = r <= 1 ? 700 : maxAge;
                    if (at != 0 && now - at < age) continue;
                    chunks.put(key, readChunk(level, cx, cz, py, cave));
                    readAt.put(key, now);
                    budget--;
                    read = true;
                }
            }
        }
        // keep what you explored (about 1 KB a chunk); only far away chunks go once there are very many
        if (chunks.size() > 12_000) {
            var it = chunks.keySet().iterator();
            while (it.hasNext()) {
                long key = it.nextLong();
                int cx = (int) (key >> 32), cz = (int) key;
                if (Math.abs(cx - pcx) > 48 || Math.abs(cz - pcz) > 48) {
                    it.remove();
                    readAt.remove(key);
                }
            }
        }
        return read;
    }

    private void update(ClientLevel level, int px, int py, int pz) {
        int half = N / 2;
        if (scan(level, px, py, pz, (half >> 4) + 1, 6, REFRESH_MS)) dirty = true;
        int ox = px - half, oz = pz - half;
        if (ox != originX || oz != originZ) {
            originX = ox;
            originZ = oz;
            dirty = true;
        }
        if (!dirty && texture != null) return;
        dirty = false;
        if (texture == null) {
            texture = new DynamicTexture(() -> "onix minimap", N, N, false);
            mc().getTextureManager().register(textureId, texture);
        }
        NativeImage img = texture.getPixels();
        if (img == null) return;
        for (int z = 0; z < N; z++) {
            int wz = oz + z;
            for (int x = 0; x < N; x++) {
                int wx = ox + x;
                int[] c = chunks.get((long) (wx >> 4) << 32 | ((wz >> 4) & 0xFFFFFFFFL));
                int argb = c == null ? 0 : c[(wz & 15) * 16 + (wx & 15)];
                img.setPixel(x, z, argb == 0 ? 0xFF15181C : argb);
            }
        }
        texture.upload();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(GuiGraphics ctx, boolean preview) {
        int s = size.getInt();
        drawBackground(ctx, s, getHeight());
        var player = mc().player;
        ClientLevel level = mc().level;
        int inset = 3;
        int inner = s - inset * 2;
        float cx = s / 2f, cy = s / 2f;
        if (player == null || level == null) {
            ctx.fill(inset, inset, s - inset, s - inset, 0xFF1F3B2A);
            Theme.textCentered(ctx, "Minimap", s / 2, s / 2 - 4, 0xAAFFFFFF);
            return;
        }
        float pt = mc().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double px = lerp(pt, player.xo, player.getX());
        double pz = lerp(pt, player.zo, player.getZ());
        update(level, (int) Math.floor(px), (int) Math.floor(player.getY()), (int) Math.floor(pz));
        float z = zoom.getFloat();
        float yaw = player.getViewYRot(pt);
        float angle = rotate.isOn() ? (float) Math.toRadians(180f - yaw) : 0f;

        ctx.enableScissor(inset, inset, s - inset, s - inset);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().rotate(angle);
        ctx.pose().scale(z, z);
        // the texture's corner sits at (origin - player) blocks from the centre
        ctx.pose().translate((float) (originX - px), (float) (originZ - pz));
        if (texture != null) ctx.blit(RenderPipelines.GUI_TEXTURED, textureId, 0, 0, 0f, 0f, N, N, N, N);
        ctx.pose().popMatrix();

        // markers: positions turned like the map
        float sin = (float) Math.sin(angle), cos = (float) Math.cos(angle);
        float radius = inner / 2f;
        if (waypoints.isOn()) {
            WaypointsModule wp = OnixClient.MODULES.get(WaypointsModule.class);
            if (wp != null) {
                for (int[] w : wp.inThisDimension()) {
                    float[] m = marker(w[0] + 0.5 - px, w[1] + 0.5 - pz, z, sin, cos, radius, true);
                    diamond(ctx, cx + m[0], cy + m[1], 3f, 0xFFFFD34D);
                }
            }
        }
        if (players.isOn() || mobs.isOn()) {
            for (Entity e : level.entitiesForRendering()) {
                if (e == player) continue;
                boolean isPlayer = e instanceof Player;
                if (isPlayer ? !players.isOn() : !mobs.isOn() || !(e instanceof net.minecraft.world.entity.Mob)) continue;
                double ex = lerp(pt, e.xo, e.getX()) - px, ez = lerp(pt, e.zo, e.getZ()) - pz;
                float[] m = marker(ex, ez, z, sin, cos, radius, false);
                if (m == null) continue;
                int color = isPlayer ? 0xFFFFFFFF : e instanceof net.minecraft.world.entity.monster.Enemy ? 0xFFFF5A5F : 0xFFB8C0C8;
                float r = isPlayer ? 2f : 1.5f;
                diamond(ctx, cx + m[0], cy + m[1], r + 0.8f, 0xAA000000);
                diamond(ctx, cx + m[0], cy + m[1], r, color);
            }
        }
        ctx.disableScissor();

        // north marker on the edge
        float[] n = marker(0, -10_000, 1f, sin, cos, radius - 5, true);
        Theme.textCentered(ctx, "N", Math.round(cx + n[0]), Math.round(cy + n[1]) - 4, 0xFFFF5A5F);

        // you: an arrow in the middle
        float a = rotate.isOn() ? 0f : (float) Math.toRadians(yaw + 180f);
        arrow(ctx, cx, cy, a);

        if (coords.isOn()) {
            String text = (int) Math.floor(px) + ", " + (int) Math.floor(player.getY()) + ", " + (int) Math.floor(pz);
            drawText(ctx, text, s / 2 - textWidth(text) / 2, s + 1, 0xFFE6E6EA);
        }
    }

    /** Screen offset of a world offset (dx, dz), or null when off the map (clamped to the edge when clamp). */
    private static float[] marker(double dx, double dz, float zoom, float sin, float cos, float radius, boolean clamp) {
        float x = (float) dx * zoom, y = (float) dz * zoom;
        float rx = x * cos - y * sin;
        float ry = x * sin + y * cos;
        float lim = radius - 2;
        if (Math.abs(rx) > lim || Math.abs(ry) > lim) {
            if (!clamp) return null;
            float k = lim / Math.max(Math.abs(rx), Math.abs(ry));
            rx *= k;
            ry *= k;
        }
        return new float[]{rx, ry};
    }

    public static void diamond(GuiGraphics ctx, float x, float y, float r, int color) {
        tris(ctx, new float[]{x, y - r, x + r, y, x - r, y, x - r, y, x + r, y, x, y + r}, color);
    }

    /** Filled triangles in one colour, drawn with both windings (the GUI pipeline culls one side). */
    public static void tris(GuiGraphics ctx, float[] xy, int color) {
        int n = xy.length / 2;
        float[] both = new float[xy.length * 2];
        System.arraycopy(xy, 0, both, 0, xy.length);
        for (int t = 0; t < n; t += 3) {
            for (int k = 0; k < 3; k++) {
                both[xy.length + (t + k) * 2] = xy[(t + 2 - k) * 2];
                both[xy.length + (t + k) * 2 + 1] = xy[(t + 2 - k) * 2 + 1];
            }
        }
        int[] colors = new int[n * 2];
        java.util.Arrays.fill(colors, color);
        GuiTriangles.draw(ctx, both, colors);
    }

    public void arrow(GuiGraphics ctx, float x, float y, float angle) {
        float s = (float) Math.sin(angle), c = (float) Math.cos(angle);
        float[] pts = {0, -5.5f, 4, 4, 0, 2, -4, 4};   // tip, right, notch, left
        float[] t = new float[8];
        for (int i = 0; i < 4; i++) {
            float px = pts[i * 2], py = pts[i * 2 + 1];
            t[i * 2] = x + px * c - py * s;
            t[i * 2 + 1] = y + px * s + py * c;
        }
        int col = arrowColor.color();
        int edge = 0xCC000000;
        // a dark rim, then the arrow
        float[] rim = new float[8];
        for (int i = 0; i < 4; i++) {
            float px = pts[i * 2] * 1.35f, py = pts[i * 2 + 1] * 1.35f;
            rim[i * 2] = x + px * c - py * s;
            rim[i * 2 + 1] = y + px * s + py * c;
        }
        tris(ctx, new float[]{rim[0], rim[1], rim[2], rim[3], rim[4], rim[5], rim[0], rim[1], rim[4], rim[5], rim[6], rim[7]}, edge);
        tris(ctx, new float[]{t[0], t[1], t[2], t[3], t[4], t[5], t[0], t[1], t[4], t[5], t[6], t[7]}, col);
    }

    private static double lerp(float t, double a, double b) {
        return a + (b - a) * t;
    }
}
