package dev.onix.client.module.impl.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.music.Lyrics;
import dev.onix.client.music.NowPlaying;
import dev.onix.client.render.FxDraw;
import dev.onix.client.render.WorldDraw;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Song lyrics: the song playing on the PC (Spotify first) with time-synced lyrics from LRCLIB – a bar on screen with
 * the current line (the sung word highlighted) and, if wanted, the words appearing as big text on walls and floor
 * around you. Only you see them. No Spotify login needed: the song is read from the system's media controls.
 */
public class SongLyricsModule extends HudModule {
    private final BoolSetting onlySpotify = add(new BoolSetting("onlySpotify", "Only Spotify", true)
            .describe("Off: also songs from other players (YouTube Music in the browser, Apple Music …)."));
    private final BoolSetting nextLine = add(new BoolSetting("nextLine", "Show Next Line", true));
    private final BoolSetting worldLyrics = add(new BoolSetting("world", "Lyrics in the World", true)
            .describe("The words appear as big text on walls and the floor around you."));
    private final NumberSetting worldSize = add(new NumberSetting("worldSize", "World Text Size", 1, 0.4, 2.5, 0.1).unit("x").visibleIf(worldLyrics::isOn));
    private final NumberSetting worldDistance = add(new NumberSetting("worldDistance", "World Text Distance", 8, 3, 20, 1).unit("blocks").visibleIf(worldLyrics::isOn));
    private final ModeSetting colors = add(new ModeSetting("colors", "Colors", "Pink", "Theme", "Rainbow", "Custom"));
    private final ColorSetting customColor = add(new ColorSetting("customColor", "Custom Color", 0xFFFF7AA8).visibleIf(() -> colors.is("Custom")));
    private final NumberSetting offset = add(new NumberSetting("offset", "Timing Offset", 0, -2000, 2000, 50).unit("ms")
            .describe("Shifts the lyrics if they come too early (−) or too late (+)."));

    /** A word standing in the world. */
    private record WorldWord(String text, Vec3 pos, Vec3 right, Vec3 up, Vec3 normal, float scale, long showAt, long hideAt, int lineIndex, int wordIndex) {}

    private final List<WorldWord> worldWords = new ArrayList<>();
    private final Random random = new Random();
    private String worldSong = "";
    private int worldLine = -1;
    private int side = 1;

    public SongLyricsModule() {
        super("songlyrics", "Song Lyrics", "Lyrics of the song you play on Spotify, on screen and in the world.", Category.HUD, false,
                Anchor.TOP_CENTER, 0, 4);
        tags("spotify", "lyrics", "music", "song", "karaoke", "now playing");
    }

    @Override
    protected void onEnable() {
        NowPlaying.start(onlySpotify.isOn());
    }

    @Override
    public void onShutdown() {
        NowPlaying.stop(); // ends the PowerShell reader with the game
    }

    @Override
    protected void onDisable() {
        NowPlaying.stop();
        worldWords.clear();
        worldLine = -1;
    }

    // ------------------------------------------------------------------ state

    private NowPlaying.Track track() {
        NowPlaying.start(onlySpotify.isOn()); // cheap when already running; picks up the setting
        return NowPlaying.current();
    }

    private long now(NowPlaying.Track t) {
        return t.positionNow() + offset.getInt();
    }

    private int accent(float t) {
        return switch (colors.get()) {
            case "Theme" -> Theme.themeAccent();
            case "Rainbow" -> FxDraw.rainbow(t);
            case "Custom" -> customColor.color();
            default -> 0xFFFF7AA8;
        };
    }

    // ------------------------------------------------------------------ HUD bar

    private static final int W = 300;
    private static final int H = 16;

    @Override
    public int getWidth() {
        return W;
    }

    @Override
    public int getHeight() {
        return H;
    }

    @Override
    public boolean hasContent() {
        return NowPlaying.current() != null;
    }

    @Override
    public void render(GuiGraphics ctx, boolean preview) {
        NowPlaying.Track t = track();
        String time;
        String current;
        String next;
        int word = 0;
        List<String> words;
        boolean noLyrics = false;
        if (t == null) {
            if (!preview) return;
            time = "0:37";
            current = "I'd go to your party, I'd be there";
            next = "Too bad I wasn't invited";
            words = List.of(current.split(" "));
            word = 4;
        } else {
            long ms = now(t);
            time = fmt(ms);
            List<Lyrics.Line> lines = Lyrics.of(t);
            if (lines == null || lines.isEmpty()) {
                noLyrics = true;
                current = t.title() + (t.artist().isBlank() ? "" : " · " + t.artist());
                next = lines == null ? "Loading lyrics…" : "No synced lyrics";
                words = List.of(current);
            } else {
                int i = Lyrics.lineAt(lines, ms);
                if (i < 0) {
                    current = "♪ " + t.title();
                    words = List.of(current);
                    next = lines.get(0).text();
                } else {
                    Lyrics.Line line = lines.get(i);
                    current = line.text();
                    words = new ArrayList<>();
                    for (Lyrics.Word w : line.words()) words.add(w.text());
                    word = Lyrics.wordAt(line, ms);
                    next = i + 1 < lines.size() ? lines.get(i + 1).text() : "";
                }
            }
        }
        drawBackground(ctx, W, H);
        Font f = Theme.font();
        int x = pad();
        int y = (H - 8) / 2;
        float clock = (System.currentTimeMillis() % 100_000L) / 1000f;
        int acc = accent(clock * 0.1f);
        x = drawText(ctx, time, x, y, 0xFFA1A1AA) + 4;
        // little playing dot
        Theme.round(ctx, x, y + 1, x + 6, y + 7, 3, acc);
        x += 10;
        int right = W - pad() - 14;
        int nextSpace = nextLine.isOn() && !next.isEmpty() ? Math.min(90, textWidth(next)) : 0;
        int lineMax = right - x - (nextSpace > 0 ? nextSpace + 8 : 0);
        // current line, word by word; the sung word sits on a coloured pill
        ctx.enableScissor(x, 0, x + lineMax, H);
        int cx = x;
        for (int i = 0; i < words.size(); i++) {
            String w = words.get(i);
            int ww = textWidth(w);
            if (i == word && !noLyrics) {
                Theme.round(ctx, cx - 2, y - 2, cx + ww + 2, y + 9, 3, Theme.withAlpha(acc, 210));
                drawText(ctx, w, cx, y, 0xFFFFFFFF);
            } else {
                drawText(ctx, w, cx, y, i < word ? 0xFFFFFFFF : 0xFFC9C9D1);
            }
            cx += ww + textWidth(" ");
        }
        ctx.disableScissor();
        if (nextSpace > 0) {
            int nx = right - nextSpace;
            ctx.enableScissor(nx, 0, right, H);
            drawText(ctx, next, nx, y, 0xFF6B6B76);
            ctx.disableScissor();
        }
        // equalizer
        boolean playing = t == null || t.playing();
        for (int b = 0; b < 3; b++) {
            float hgt = playing ? 2 + 4 * (0.5f + 0.5f * (float) Math.sin(clock * (6 + b * 2.3f) + b)) : 2;
            int bx = W - pad() - 10 + b * 3;
            ctx.fill(bx, y + 8 - Math.round(hgt), bx + 2, y + 8, 0xFFE4E4E7);
        }
    }

    private static String fmt(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    // ------------------------------------------------------------------ lyrics in the world

    @Override
    public void onTick() {
        if (!worldLyrics.isOn() || mc().player == null || mc().level == null) {
            worldWords.clear();
            return;
        }
        NowPlaying.Track t = NowPlaying.current();
        long wall = System.currentTimeMillis();
        worldWords.removeIf(w -> wall > w.hideAt() + 700);
        if (t == null || !t.playing()) return;
        List<Lyrics.Line> lines = Lyrics.of(t);
        if (lines == null || lines.isEmpty()) return;
        long ms = now(t);
        int i = Lyrics.lineAt(lines, ms);
        if (!t.key().equals(worldSong)) {
            worldSong = t.key();
            worldLine = -1;
            worldWords.clear();
        }
        if (i < 0 || i == worldLine) return;
        worldLine = i;
        placeLine(lines.get(i), i, ms, wall);
    }

    /** Puts a line's words on a wall or the floor in front of the player (alternating sides), or floating in the air. */
    private void placeLine(Lyrics.Line line, int index, long songMs, long wall) {
        Player p = mc().player;
        Font f = mc().font;
        side = -side;
        float yaw = p.getYRot() + side * (20 + random.nextInt(30));
        float pitch = -5 + random.nextInt(30);
        Vec3 eye = p.getEyePosition();
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        double dist = worldDistance.get();
        BlockHitResult hit = mc().level.clip(new ClipContext(eye, eye.add(dir.scale(dist)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 flat = new Vec3(dir.x, 0, dir.z).normalize();
        Vec3 origin;
        Vec3 normal;
        Vec3 up;
        if (hit.getType() == HitResult.Type.BLOCK) {
            Direction face = hit.getDirection();
            normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
            up = face.getAxis() == Direction.Axis.Y ? (face == Direction.UP ? flat : flat.scale(-1)) : new Vec3(0, 1, 0);
            origin = hit.getLocation().add(normal.scale(0.03));
        } else {
            normal = dir.scale(-1);
            up = new Vec3(0, 1, 0);
            normal = new Vec3(normal.x, 0, normal.z).normalize();
            origin = eye.add(dir.scale(dist * 0.8));
        }
        Vec3 right = up.cross(normal).normalize();
        float scale = 0.09f * worldSize.getFloat();
        int textW = f.width(line.text());
        double widthBlocks = textW * scale;
        if (widthBlocks > 9) {
            scale *= (float) (9 / widthBlocks);
            widthBlocks = 9;
        }
        double cursor = -widthBlocks / 2;
        long hideAt = wall + Math.max(1500, Math.min(7000, line.endMs() - songMs)) + 1800;
        int space = f.width(" ");
        for (int k = 0; k < line.words().size(); k++) {
            Lyrics.Word w = line.words().get(k);
            Vec3 pos = origin.add(right.scale(cursor)).add(up.scale(4 * scale));
            long showAt = wall + Math.max(0, w.startMs() - songMs);
            worldWords.add(new WorldWord(w.text(), pos, right, up, normal, scale, showAt, hideAt, index, k));
            cursor += (f.width(w.text()) + space) * scale;
        }
        while (worldWords.size() > 60) worldWords.remove(0);
    }

    /** Dev test: puts a sample line into the world in front of the player. */
    public void testWorldLine() {
        if (mc().player == null) return;
        String text = "I'd go to your party, I'd be there";
        List<Lyrics.Word> words = new ArrayList<>();
        long t0 = 0;
        for (String w : text.split(" ")) {
            words.add(new Lyrics.Word(w, t0));
            t0 += 150;
        }
        placeLine(new Lyrics.Line(0, 3000, text, words), 0, 0, System.currentTimeMillis());
    }

    @Override
    public void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled() || !worldLyrics.isOn() || worldWords.isEmpty() || context.consumers() == null || context.matrices() == null) return;
            PoseStack matrices = context.matrices();
            Vec3 cam = WorldDraw.camera(context);
            Font f = mc().font;
            long wall = System.currentTimeMillis();
            NowPlaying.Track t = NowPlaying.current();
            List<Lyrics.Line> lines = t == null ? null : Lyrics.of(t);
            int curLine = lines == null || lines.isEmpty() ? -1 : Lyrics.lineAt(lines, now(t));
            int curWord = curLine < 0 ? -1 : Lyrics.wordAt(lines.get(curLine), now(t));
            for (WorldWord w : worldWords) {
                if (wall < w.showAt()) continue;
                float in = Math.min(1f, (wall - w.showAt()) / 180f);
                float out = Math.max(0f, Math.min(1f, (w.hideAt() + 600 - wall) / 600f));
                float alpha = Math.min(in, out);
                if (alpha <= 0.02f) continue;
                float pop = 0.75f + 0.25f * (1 - (1 - in) * (1 - in));
                boolean sung = w.lineIndex() == curLine && w.wordIndex() == curWord;
                int base = accent(w.lineIndex() * 0.13f + w.wordIndex() * 0.05f);
                int color = sung ? 0xFFFFFFFF : base;
                int argb = (Math.max(5, Math.round(alpha * 255)) << 24) | (color & 0xFFFFFF);
                matrices.pushPose();
                Vec3 o = w.pos().subtract(cam);
                Matrix4f basis = new Matrix4f(
                        (float) w.right().x, (float) w.right().y, (float) w.right().z, 0,
                        (float) w.up().x, (float) w.up().y, (float) w.up().z, 0,
                        (float) w.normal().x, (float) w.normal().y, (float) w.normal().z, 0,
                        (float) o.x, (float) o.y, (float) o.z, 1);
                matrices.last().pose().mul(basis);
                float s = w.scale() * pop;
                matrices.scale(s, -s, s);
                // a soft dark copy behind the word keeps it readable on bright blocks
                f.drawInBatch(w.text(), 1, 1, (Math.max(8, Math.round(alpha * 120)) << 24), false, matrices.last().pose(), context.consumers(),
                        Font.DisplayMode.NORMAL, 0, 0xF000F0);
                matrices.translate(0, 0, 0.6f);
                f.drawInBatch(w.text(), 0, 0, argb, false, matrices.last().pose(), context.consumers(), Font.DisplayMode.NORMAL, 0, 0xF000F0);
                matrices.popPose();
            }
        });
    }
}
