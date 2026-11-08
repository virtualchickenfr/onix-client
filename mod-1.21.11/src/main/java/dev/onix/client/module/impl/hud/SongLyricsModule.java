package dev.onix.client.module.impl.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.module.OverlayRenderer;
import dev.onix.client.music.Artwork;
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
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Song lyrics like a music edit: the song playing on the PC (Spotify first, read from the system's media controls –
 * no login) with synced lyrics (LRCLIB, then NetEase, then estimated timing). The words appear around you in the
 * world with animations, colours and effects, plus a karaoke bar, cinematic bars and a title card on screen.
 * Only you see any of it.
 */
public class SongLyricsModule extends HudModule implements OverlayRenderer {
    // ---------------------------------------------------------------- general
    private final ModeSetting preset = add(new ModeSetting("preset", "Preset", "Custom", "Music Edit", "Chill", "Hype", "Minimal", "Neon", "Retro", "Dreamy")
            .describe("Sets everything below to a style in one click; change single settings afterwards as you like."));
    private final BoolSetting onlySpotify = add(new BoolSetting("onlySpotify", "Only Spotify", true)
            .describe("Off: also songs from other players (YouTube Music in the browser, Apple Music …)."));
    private final BoolSetting unsynced = add(new BoolSetting("unsynced", "Unsynced Lyrics Fallback", true)
            .describe("If no synced lyrics exist, plain lyrics are spread over the song (timing estimated)."));
    private final NumberSetting offset = add(new NumberSetting("offset", "Timing Offset", 0, -2000, 2000, 50).unit("ms")
            .describe("Shifts the lyrics if they come too early (−) or too late (+)."));

    {
        group("World");
    }

    private final BoolSetting worldLyrics = add(new BoolSetting("world", "Lyrics in the World", true));
    private final ModeSetting placement = add(new ModeSetting("placement", "Placement", "Follow You", "Scattered Around You", "Straight Ahead", "Follow View", "In the Sky")
            .describe("Follow You: the lyrics float in front of you and smoothly follow where you go and look; the current line is in the middle, the last one slides up.")
            .visibleIf(worldLyrics::isOn));
    private final BoolSetting followSet = add(new BoolSetting("followSet", "Follow Set", false).hidden());
    private final ModeSetting animation = add(new ModeSetting("animation", "Animation", "Karaoke", "Typewriter", "Wave", "Drop", "Rise", "Zoom", "Glitch")
            .visibleIf(worldLyrics::isOn));
    private final ModeSetting intensity = add(new ModeSetting("intensity", "Intensity", "Normal", "Chill", "Hype").visibleIf(worldLyrics::isOn));
    private final ModeSetting worldFont = add(new ModeSetting("worldFont", "Lyrics Font", "Bold", "Smooth", "Minecraft").visibleIf(worldLyrics::isOn));
    private final NumberSetting wordsPerLine = add(new NumberSetting("wordsPerLine", "Words per Line", 6, 2, 14, 1).visibleIf(worldLyrics::isOn));
    private final NumberSetting stay = add(new NumberSetting("stay", "Lines Stay For", 2.5, 0.5, 8, 0.5).unit("s").visibleIf(worldLyrics::isOn));
    private final BoolSetting faceYou = add(new BoolSetting("faceYou", "Turn to Face You", false).visibleIf(worldLyrics::isOn)
            .describe("Words always turn towards you instead of lying on walls and the floor."));
    private final NumberSetting worldDistance = add(new NumberSetting("worldDistance", "Distance", 8, 3, 20, 1).unit("blocks").visibleIf(worldLyrics::isOn));
    private final NumberSetting worldSize = add(new NumberSetting("worldSize", "Size", 1, 0.4, 2.5, 0.1).unit("x").visibleIf(worldLyrics::isOn));
    private final BoolSetting throughWalls = add(new BoolSetting("throughWalls", "Show Through Walls", false).visibleIf(worldLyrics::isOn));

    {
        group("Look");
    }

    private final ModeSetting colorMode = add(new ModeSetting("colorMode", "Colour Mode", "Gradient", "Single", "Rainbow", "Album")
            .describe("Album takes the colours of the song's cover."));
    private final ColorSetting color1 = add(new ColorSetting("color1", "Colour", 0xFFFF7AA8));
    private final ColorSetting color2 = add(new ColorSetting("color2", "Second Colour", 0xFFFFB347).visibleIf(() -> colorMode.is("Gradient")));
    private final BoolSetting glow = add(new BoolSetting("glow", "Glow", true));
    private final BoolSetting chromatic = add(new BoolSetting("chromatic", "Chromatic Split", false));
    private final BoolSetting sparkles = add(new BoolSetting("sparkles", "Sparkles", true));
    private final BoolSetting beatZoom = add(new BoolSetting("beatZoom", "Beat Zoom", false).describe("The view kicks in a little on every word."));
    private final BoolSetting beatPulse = add(new BoolSetting("beatPulse", "Beat Pulse", true).describe("The sung word grows for a moment."));

    {
        group("Screen");
    }

    private final BoolSetting karaokeBar = add(new BoolSetting("karaokeBar", "Karaoke Bar", true));
    private final BoolSetting nextLine = add(new BoolSetting("nextLine", "Show Next Line", true).visibleIf(karaokeBar::isOn));
    private final BoolSetting cinematic = add(new BoolSetting("cinematic", "Cinematic Bars", false));
    private final BoolSetting titleIntro = add(new BoolSetting("titleIntro", "Song Title Intro", true));

    // ---------------------------------------------------------------- state

    /**
     * A word in the world. surface = lies on a wall/floor with its own axes; otherwise it turns to the camera.
     * x/y = position inside its line in font pixels, s = blocks per font pixel. follow = attached to the view.
     */
    private record WorldWord(String text, Vec3 anchor, Vec3 right, Vec3 up, Vec3 normal, boolean surface, boolean follow,
                             float x, float y, float s, long showAt, long sungAt, long hideAt, int line, int index, int count, float lineH) {
        WorldWord hideBy(long t) {
            return t >= hideAt ? this : new WorldWord(text, anchor, right, up, normal, surface, follow, x, y, s, showAt, sungAt, t, line, index, count, lineH);
        }
    }

    // Follow You: a point in front of the player that trails behind the view, and where each line sits in the stack
    private Vec3 rigPos;
    private long rigTime;
    private final java.util.Map<Integer, Float> lineShift = new java.util.HashMap<>();

    private static volatile long lastBeat;
    private static volatile float beatAmount;

    private final List<WorldWord> worldWords = new ArrayList<>();
    private final Random random = new Random();
    private String worldSong = "";
    private int worldLine = -1;
    private int lastSung = -1;
    private int side = 1;
    private String introSong = "";
    private long introStart;
    private float barsAnim;

    public SongLyricsModule() {
        super("songlyrics", "Song Lyrics", "Synced lyrics of your Spotify song, animated in the world like a music edit.", Category.HUD, false,
                Anchor.TOP_CENTER, 0, 4);
        tags("spotify", "lyrics", "music", "song", "karaoke", "now playing", "music edit");
        preset.onChange(this::applyPreset);
    }

    /** The FOV kick of Beat Zoom (1 = none), read by GameRendererMixin. */
    public static float fovFactor() {
        float amp = beatAmount;
        if (amp <= 0) return 1f;
        float t = (System.currentTimeMillis() - lastBeat) / 140f;
        return t > 4 ? 1f : 1f - amp * (float) Math.exp(-t);
    }

    private void applyPreset() {
        switch (preset.get()) {
            case "Music Edit" -> style("Follow You", "Karaoke", "Normal", "Bold", "Gradient", 0xFFFF7AA8, 0xFFFFB347, true, false, true, false, true, false, true);
            case "Chill" -> style("Straight Ahead", "Rise", "Chill", "Smooth", "Single", 0xFFA8D8FF, 0xFFFFFFFF, true, false, false, false, false, false, true);
            case "Hype" -> style("Follow You", "Zoom", "Hype", "Bold", "Rainbow", 0xFFFF4D6D, 0xFFFFD84D, true, true, true, true, true, true, true);
            case "Minimal" -> style("Follow You", "Karaoke", "Chill", "Smooth", "Single", 0xFFFFFFFF, 0xFFFFFFFF, false, false, false, false, false, false, false);
            case "Neon" -> style("Follow You", "Glitch", "Normal", "Bold", "Gradient", 0xFF00F0FF, 0xFFFF2BD6, true, true, true, false, true, false, true);
            case "Retro" -> style("Straight Ahead", "Typewriter", "Normal", "Minecraft", "Single", 0xFFFFD84D, 0xFFFFFFFF, false, false, false, false, false, true, true);
            case "Dreamy" -> style("In the Sky", "Wave", "Chill", "Smooth", "Gradient", 0xFFC9B6FF, 0xFFFFB6E1, true, false, true, false, true, false, true);
            default -> {
                // Custom: keep the current settings
            }
        }
    }

    private void style(String place, String anim, String intense, String font, String colours, int c1, int c2, boolean glowOn, boolean split,
                       boolean sparkleOn, boolean zoom, boolean pulse, boolean bars, boolean intro) {
        placement.set(place);
        animation.set(anim);
        intensity.set(intense);
        worldFont.set(font);
        colorMode.set(colours);
        color1.set(c1);
        color2.set(c2);
        glow.set(glowOn);
        chromatic.set(split);
        sparkles.set(sparkleOn);
        beatZoom.set(zoom);
        beatPulse.set(pulse);
        cinematic.set(bars);
        titleIntro.set(intro);
    }

    @Override
    protected void onEnable() {
        NowPlaying.start(onlySpotify.isOn());
    }

    @Override
    public void init() {
        registerWorldRenderer();
    }

    /** 1.1.20: the lyrics follow you (once, after the profile is loaded; other placements stay selectable). */
    public void followByDefault() {
        if (followSet.isOn()) return;
        followSet.set(true);
        if (placement.is("Scattered Around You")) placement.set("Follow You");
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
        beatAmount = 0;
    }

    private NowPlaying.Track track() {
        NowPlaying.start(onlySpotify.isOn()); // cheap when already running; picks up the setting
        Lyrics.allowUnsynced = unsynced.isOn();
        return NowPlaying.current();
    }

    private long now(NowPlaying.Track t) {
        return t.positionNow() + offset.getInt();
    }

    private float amp() {
        return intensity.is("Chill") ? 0.5f : intensity.is("Hype") ? 1.8f : 1f;
    }

    /** Colour of word i of n in a line. */
    private int wordColor(NowPlaying.Track t, int line, int i, int n) {
        float f = n <= 1 ? 0 : i / (float) (n - 1);
        return switch (colorMode.get()) {
            case "Single" -> color1.color();
            case "Rainbow" -> FxDraw.rainbow(line * 0.11f + i * 0.06f + (System.currentTimeMillis() % 20_000L) / 20_000f);
            case "Album" -> {
                int[] c = t == null ? null : Artwork.colors(t);
                yield c == null ? Theme.lerpColor(color1.color(), color2.color(), f) : Theme.lerpColor(c[0], c[1], f * 0.6f);
            }
            default -> Theme.lerpColor(color1.color(), color2.color(), f);
        };
    }

    private Component styled(String s) {
        return switch (worldFont.get()) {
            case "Smooth" -> Theme.ui(s);
            case "Bold" -> Theme.display(s);
            default -> Component.literal(s);
        };
    }

    // ================================================================== karaoke bar

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
        return karaokeBar.isOn() && NowPlaying.current() != null;
    }

    @Override
    public void render(GuiGraphics ctx, boolean preview) {
        if (!karaokeBar.isOn() && !preview) return;
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
                String problem = Lyrics.problem(t);
                next = lines == null ? "Finding lyrics…" : problem != null && !problem.isEmpty() ? problem : "No lyrics found";
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
        int x = pad();
        int y = (H - 8) / 2;
        float clock = (System.currentTimeMillis() % 100_000L) / 1000f;
        int acc = wordColor(t, 0, 0, 1);
        x = drawText(ctx, time, x, y, 0xFFA1A1AA) + 4;
        Theme.round(ctx, x, y + 1, x + 6, y + 7, 3, acc);
        x += 10;
        int right = W - pad() - 14;
        int nextSpace = nextLine.isOn() && !next.isEmpty() ? Math.min(90, textWidth(next)) : 0;
        int lineMax = right - x - (nextSpace > 0 ? nextSpace + 8 : 0);
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

    // ================================================================== cinematic bars and title card

    @Override
    public void renderOverlay(GuiGraphics ctx) {
        NowPlaying.Track t = NowPlaying.current();
        boolean playing = t != null && t.playing();
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        // cinematic bars slide in while a song plays
        float target = cinematic.isOn() && playing ? 1f : 0f;
        barsAnim += (target - barsAnim) * 0.08f;
        if (barsAnim > 0.01f) {
            int bh = Math.round(h * 0.085f * barsAnim);
            ctx.fill(0, 0, w, bh, 0xFF000000);
            ctx.fill(0, h - bh, w, h, 0xFF000000);
        }
        if (t == null || !titleIntro.isOn()) return;
        if (!t.key().equals(introSong)) {
            introSong = t.key();
            introStart = System.currentTimeMillis();
        }
        long a = System.currentTimeMillis() - introStart;
        if (a > 4200) return;
        float in = Math.min(1f, a / 450f);
        float out = Math.min(1f, (4200 - a) / 700f);
        float alpha = Math.min(in, out);
        if (alpha <= 0.02f) return;
        float ease = 1 - (1 - in) * (1 - in) * (1 - in);
        int cy = h / 3 + Math.round((1 - ease) * 12);
        Font f = Theme.font();
        int col = wordColor(t, 0, 0, 1);
        String label = "NOW PLAYING";
        ctx.drawString(f, label, w / 2 - f.width(label) / 2, cy - 22, Theme.withAlpha(col, Math.round(220 * alpha)), true);
        Component title = Theme.display(t.title());
        float scale = Math.min(2.6f, (w * 0.8f) / Math.max(1, f.width(title)));
        ctx.pose().pushMatrix();
        ctx.pose().translate(w / 2f, cy);
        ctx.pose().scale(scale, scale);
        ctx.drawString(f, title, -f.width(title) / 2, 0, Theme.withAlpha(0xFFFFFFFF, Math.round(255 * alpha)), true);
        ctx.pose().popMatrix();
        Component artist = Theme.ui(t.artist());
        ctx.drawString(f, artist, w / 2 - f.width(artist) / 2, cy + Math.round(11 * scale) + 4, Theme.withAlpha(0xFFC9C9D1, Math.round(255 * alpha)), true);
    }

    // ================================================================== words in the world

    @Override
    public void onTick() {
        Lyrics.allowUnsynced = unsynced.isOn();
        if (!worldLyrics.isOn() || mc().player == null || mc().level == null) {
            worldWords.clear();
            return;
        }
        NowPlaying.Track t = NowPlaying.current();
        long wall = System.currentTimeMillis();
        worldWords.removeIf(w -> wall > w.hideAt() + 700);
        if (t == null || !t.playing()) return;
        if (colorMode.is("Album")) Artwork.colors(t);
        List<Lyrics.Line> lines = Lyrics.of(t);
        if (lines == null || lines.isEmpty()) return;
        long ms = now(t);
        int i = Lyrics.lineAt(lines, ms);
        if (!t.key().equals(worldSong)) {
            worldSong = t.key();
            worldLine = -1;
            worldWords.clear();
        }
        if (i >= 0) {
            int sung = i * 1000 + Lyrics.wordAt(lines.get(i), ms);
            if (sung != lastSung) {
                lastSung = sung;
                onBeat(i, sung % 1000);
            }
        }
        if (i < 0 || i == worldLine) return;
        worldLine = i;
        placeLine(lines.get(i), i, ms, wall);
    }

    /** A new word is sung: beat zoom and sparkles. */
    private void onBeat(int line, int index) {
        lastBeat = System.currentTimeMillis();
        beatAmount = beatZoom.isOn() ? 0.012f * amp() : 0f;
        if (!sparkles.isOn() || mc().level == null) return;
        for (WorldWord w : worldWords) {
            if (w.line() != line || w.index() != index) continue;
            Vec3 p = w.surface() ? w.anchor().add(w.right().scale((w.x() + 6) * w.s())).add(w.up().scale(-w.y() * w.s())) : w.anchor();
            for (int k = 0; k < 3; k++) {
                mc().level.addParticle(ParticleTypes.END_ROD, p.x + (random.nextDouble() - 0.5) * 0.6, p.y + random.nextDouble() * 0.4,
                        p.z + (random.nextDouble() - 0.5) * 0.6, 0, 0.02, 0);
            }
            break;
        }
    }

    /** Puts a line's words into the world, wrapped into rows of "Words per Line". */
    private void placeLine(Lyrics.Line line, int index, long songMs, long wall) {
        Player p = mc().player;
        Font f = mc().font;
        String mode = placement.get();
        float scale = 0.09f * worldSize.getFloat();
        double dist = worldDistance.get();
        Vec3 eye = p.getEyePosition();
        Vec3 look = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 anchor;
        Vec3 normal;
        Vec3 up = new Vec3(0, 1, 0);
        boolean surface = !faceYou.isOn();
        boolean follow = false;
        switch (mode) {
            case "Straight Ahead" -> {
                anchor = eye.add(look.scale(dist)).add(0, 0.4, 0);
                normal = look.scale(-1);
            }
            case "Follow You" -> {
                anchor = Vec3.ZERO;
                normal = look.scale(-1);
                surface = false;
                follow = true;
                scale *= 0.62f;
            }
            case "Follow View" -> {
                anchor = Vec3.ZERO;
                normal = look.scale(-1);
                surface = false;
                follow = true;
                scale *= 0.6f;
            }
            case "In the Sky" -> {
                side = -side;
                Vec3 dir = Vec3.directionFromRotation(0, p.getYRot() + side * (5 + random.nextInt(25)));
                anchor = eye.add(dir.scale(dist * 3)).add(0, 14 + random.nextInt(8), 0);
                normal = dir.scale(-1);
                scale *= 3.2f;
                surface = false;
            }
            default -> {
                side = -side;
                float yaw = p.getYRot() + side * (12 + random.nextInt(22));
                Vec3 dir = Vec3.directionFromRotation(-4 + random.nextInt(24), yaw);
                BlockHitResult hit = mc().level.clip(new ClipContext(eye, eye.add(dir.scale(dist)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                if (hit.getType() != HitResult.Type.BLOCK) {
                    dir = Vec3.directionFromRotation(22 + random.nextInt(18), yaw);
                    hit = mc().level.clip(new ClipContext(eye, eye.add(dir.scale(dist)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                }
                Vec3 flat = new Vec3(dir.x, 0, dir.z).normalize();
                if (hit.getType() == HitResult.Type.BLOCK) {
                    Direction face = hit.getDirection();
                    normal = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
                    if (face.getAxis() == Direction.Axis.Y) up = face == Direction.UP ? flat : flat.scale(-1);
                    anchor = hit.getLocation().add(normal.scale(surface ? 0.03 : 0.8));
                } else {
                    Vec3 d = Vec3.directionFromRotation(0, yaw);
                    normal = d.scale(-1);
                    anchor = eye.add(d.scale(Math.min(dist, 6)));
                }
            }
        }
        Vec3 right = up.cross(normal).normalize();

        // rows of at most "Words per Line" words, centred
        int per = Math.max(1, wordsPerLine.getInt());
        List<Lyrics.Word> words = line.words();
        int rows = (words.size() + per - 1) / per;
        int space = f.width(" ");
        int widest = 0;
        for (int r = 0; r < rows; r++) {
            int w = 0;
            for (int k = r * per; k < Math.min(words.size(), (r + 1) * per); k++) w += f.width(styled(words.get(k).text())) + space;
            widest = Math.max(widest, w - space);
        }
        boolean stack = mode.equals("Follow You");
        if (widest * scale > (stack ? 5.5 : 9) && !mode.equals("In the Sky")) scale *= (float) ((stack ? 5.5 : 9) / (widest * scale));
        float rowH = 13;
        long hideAt = wall + Math.max(800, Math.min(9000, line.endMs() - songMs)) + Math.round(stay.get() * 1000);
        // the line before goes away so lines never pile up: in Follow You it slides up and fades, elsewhere it fades out
        for (int k = 0; k < worldWords.size(); k++) {
            WorldWord old = worldWords.get(k);
            if (old.line() != index) worldWords.set(k, old.hideBy(wall + (stack ? 1300 : 0)));
        }
        int slot = follow && !stack ? (index % 3) - 1 : 0;
        float lineH = rows * rowH + 10;
        for (int r = 0; r < rows; r++) {
            int from = r * per;
            int to = Math.min(words.size(), (r + 1) * per);
            int rowW = 0;
            for (int k = from; k < to; k++) rowW += f.width(styled(words.get(k).text())) + space;
            rowW -= space;
            float cx = -rowW / 2f;
            float cy = (r - (rows - 1) / 2f) * rowH - rows * rowH * 0.5f + slot * (rows * rowH + 6);
            for (int k = from; k < to; k++) {
                Lyrics.Word w = words.get(k);
                long showAt = wall + Math.max(0, w.startMs() - songMs);
                if (animation.is("Typewriter") || animation.is("Karaoke") || animation.is("Zoom") || animation.is("Drop") || animation.is("Glitch")) {
                    // words come in as they are sung
                } else {
                    showAt = wall + Math.max(0, w.startMs() - songMs - 120);
                }
                worldWords.add(new WorldWord(w.text(), anchor, right, up, normal, surface, follow, cx, stack ? (r - (rows - 1) / 2f) * rowH : cy, scale, showAt,
                        wall + Math.max(0, w.startMs() - songMs), hideAt, index, k, words.size(), lineH));
                cx += f.width(styled(w.text())) + space;
            }
        }
        while (worldWords.size() > 90) worldWords.remove(0);
    }

    /** Dev test: switches to a preset and places the sample line again. */
    public void testPreset(String name) {
        preset.set(name);
        worldWords.clear();
        testWorldLine();
    }

    /** Dev test: a second sample line, so the first one slides up. */
    public void testNextLine() {
        if (mc().player == null) return;
        String text = "We were dancing all night long";
        List<Lyrics.Word> words = new ArrayList<>();
        long t0 = 0;
        for (String w : text.split(" ")) {
            words.add(new Lyrics.Word(w, t0));
            t0 += 150;
        }
        placeLine(new Lyrics.Line(0, 3000, text, words), 1, 0, System.currentTimeMillis());
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

    private void registerWorldRenderer() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled() || !worldLyrics.isOn() || worldWords.isEmpty() || context.consumers() == null || context.matrices() == null) return;
            PoseStack matrices = context.matrices();
            Vec3 cam = WorldDraw.camera(context);
            Camera camera = mc().gameRenderer.getMainCamera();
            Font f = mc().font;
            long wall = System.currentTimeMillis();
            NowPlaying.Track t = NowPlaying.current();
            List<Lyrics.Line> lines = t == null ? null : Lyrics.of(t);
            int curLine = lines == null || lines.isEmpty() ? -1 : Lyrics.lineAt(lines, now(t));
            int curWord = curLine < 0 ? -1 : Lyrics.wordAt(lines.get(curLine), now(t));
            float k = amp();
            String anim = animation.get();
            Font.DisplayMode mode = throughWalls.isOn() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
            Vec3 lookVec = mc().player != null ? mc().player.getViewVector(WorldDraw.partialTick()) : Vec3.ZERO;
            // Follow You: the stage trails behind your view with a soft delay, like a camera rig
            // follows where you look, but stays between slightly below and above eye height and never inside blocks
            double followDist = worldDistance.get() * 0.6;
            Vec3 target = cam;
            if (mc().player != null) {
                float pitch = Math.max(-25f, Math.min(8f, mc().player.getViewXRot(WorldDraw.partialTick())));
                Vec3 dir = Vec3.directionFromRotation(pitch, mc().player.getViewYRot(WorldDraw.partialTick()));
                target = cam.add(dir.scale(followDist));
                BlockHitResult block = mc().level.clip(new ClipContext(cam, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc().player));
                if (block.getType() == HitResult.Type.BLOCK) target = cam.add(dir.scale(Math.max(1.2, block.getLocation().distanceTo(cam) - 0.5)));
            }
            float dt = rigTime == 0 ? 1f : Math.min(0.25f, (wall - rigTime) / 1000f);
            rigTime = wall;
            if (rigPos == null || rigPos.distanceTo(target) > 24) rigPos = target;
            else rigPos = rigPos.add(target.subtract(rigPos).scale(1 - Math.exp(-dt * 4.5)));
            Vec3 toCam = cam.subtract(rigPos);
            Vec3 flat = new Vec3(toCam.x, 0, toCam.z);
            Vec3 rigNormal = flat.lengthSqr() < 1e-4 ? lookVec.scale(-1) : flat.normalize();
            Vec3 rigUp = new Vec3(0, 1, 0);
            Vec3 rigRight = rigUp.cross(rigNormal).normalize();
            // where each line sits: the newest in the middle, older ones above
            java.util.Map<Integer, Float> lineH = new java.util.HashMap<>();
            for (WorldWord w : worldWords) if (w.follow()) lineH.putIfAbsent(w.line(), w.lineH());
            float acc = 0;
            List<Integer> order = new ArrayList<>(lineH.keySet());
            order.sort(java.util.Comparator.reverseOrder());
            java.util.Map<Integer, Float> targetShift = new java.util.HashMap<>();
            for (int li = 0; li < order.size(); li++) {
                int line = order.get(li);
                if (li > 0) acc += (lineH.get(order.get(li - 1)) + lineH.get(line)) / 2f;
                targetShift.put(line, -acc);
            }
            float ease = 1 - (float) Math.exp(-dt * 10);
            lineShift.keySet().retainAll(targetShift.keySet());
            for (var e : targetShift.entrySet()) {
                Float cur = lineShift.get(e.getKey());
                lineShift.put(e.getKey(), cur == null ? (e.getValue() == 0 ? 0f : e.getValue()) : cur + (e.getValue() - cur) * ease);
            }
            int newest = order.isEmpty() ? -1 : order.get(0);
            for (WorldWord w : worldWords) {
                if (wall < w.showAt()) continue;
                long a = wall - w.showAt();
                float in = Math.min(1f, a / (anim.equals("Rise") ? 420f : 180f));
                float fadeMs = w.follow() ? 500f : 300f;
                float out = Math.max(0f, Math.min(1f, (w.hideAt() + fadeMs - wall) / fadeMs));
                float alpha = Math.min(in, out);
                boolean stacked = w.follow() && placement.is("Follow You");
                if (stacked && w.line() != newest) alpha *= 0.42f;
                if (alpha <= 0.02f) continue;
                float easeIn = 1 - (1 - in) * (1 - in) * (1 - in);
                float dx = 0;
                float dy = 0;
                float pop = 1f;
                String text = w.text();
                switch (anim) {
                    case "Typewriter" -> {
                        int chars = Math.min(text.length(), 1 + (int) (a / 45));
                        text = text.substring(0, chars);
                    }
                    case "Wave" -> dy = (float) Math.sin(wall * 0.005 + w.index() * 0.7) * 2.5f * k;
                    case "Drop" -> {
                        float d = Math.min(1f, a / 380f);
                        float bounce = (float) (Math.abs(Math.cos(d * Math.PI * 1.5)) * (1 - d));
                        dy = -(1 - d) * 26 * k - bounce * 4 * k;
                    }
                    case "Rise" -> dy = (1 - easeIn) * 14 * k;
                    case "Zoom" -> pop = 1 + (1 - easeIn) * 1.3f * k;
                    case "Glitch" -> {
                        boolean glitching = a < 320 || random.nextFloat() < 0.03f * k;
                        if (glitching) {
                            dx = (random.nextFloat() - 0.5f) * 4 * k;
                            dy = (random.nextFloat() - 0.5f) * 2 * k;
                        }
                    }
                    default -> pop = 0.75f + 0.25f * easeIn;
                }
                boolean sung = w.line() == curLine && w.index() == curWord;
                boolean past = w.line() < curLine || w.line() == curLine && w.index() < curWord;
                if (beatPulse.isOn() && sung) {
                    long since = wall - w.sungAt();
                    pop *= 1 + 0.18f * k * (float) Math.exp(-since / 160.0);
                }
                int base = wordColor(t, w.line(), w.index(), w.count());
                int color = sung ? Theme.lerpColor(base, 0xFFFFFFFF, 0.65f) : past ? base : Theme.lerpColor(base, 0xFFFFFFFF, 0.15f);
                Component c = styled(text);
                float tw = f.width(c);

                matrices.pushPose();
                Vec3 anchor = stacked ? rigPos : w.follow() ? cam.add(lookVec.scale(worldDistance.get() * 0.6)) : w.anchor();
                Vec3 o = anchor.subtract(cam);
                if (stacked) {
                    Matrix4f basis = new Matrix4f(
                            (float) rigRight.x, (float) rigRight.y, (float) rigRight.z, 0,
                            (float) rigUp.x, (float) rigUp.y, (float) rigUp.z, 0,
                            (float) rigNormal.x, (float) rigNormal.y, (float) rigNormal.z, 0,
                            (float) o.x, (float) o.y, (float) o.z, 1);
                    matrices.last().pose().mul(basis);
                } else if (w.surface()) {
                    Matrix4f basis = new Matrix4f(
                            (float) w.right().x, (float) w.right().y, (float) w.right().z, 0,
                            (float) w.up().x, (float) w.up().y, (float) w.up().z, 0,
                            (float) w.normal().x, (float) w.normal().y, (float) w.normal().z, 0,
                            (float) o.x, (float) o.y, (float) o.z, 1);
                    matrices.last().pose().mul(basis);
                } else {
                    matrices.translate(o.x, o.y, o.z);
                    matrices.mulPose(camera.rotation());
                }
                float s = w.s();
                matrices.scale(s, -s, s);
                // scale around the word's middle
                float mx = w.x() + tw / 2f + dx;
                float my = w.y() + 4 + dy + (stacked ? lineShift.getOrDefault(w.line(), 0f) : 0f);
                if (stacked && w.line() != newest) pop *= 0.82f;
                matrices.translate(mx, my, 0);
                matrices.scale(pop, pop, 1);
                float x0 = -tw / 2f;
                float y0 = -4;
                if (glow.isOn()) {
                    int g = (Math.max(5, Math.round(alpha * 55)) << 24) | (base & 0xFFFFFF);
                    for (int gx = -1; gx <= 1; gx++) {
                        for (int gy = -1; gy <= 1; gy++) {
                            if (gx == 0 && gy == 0) continue;
                            f.drawInBatch(c, x0 + gx * 0.7f, y0 + gy * 0.7f, g, false, matrices.last().pose(), context.consumers(), mode, 0, 0xF000F0);
                        }
                    }
                    matrices.translate(0, 0, 0.3f);
                } else {
                    f.drawInBatch(c, x0 + 1, y0 + 1, (Math.max(8, Math.round(alpha * 120)) << 24), false, matrices.last().pose(), context.consumers(),
                            mode, 0, 0xF000F0);
                    matrices.translate(0, 0, 0.3f);
                }
                if (chromatic.isOn() || anim.equals("Glitch") && a < 320) {
                    int ca = Math.max(5, Math.round(alpha * 150)) << 24;
                    f.drawInBatch(c, x0 - 0.8f, y0, ca | 0xFF3355, false, matrices.last().pose(), context.consumers(), mode, 0, 0xF000F0);
                    f.drawInBatch(c, x0 + 0.8f, y0, ca | 0x33E0FF, false, matrices.last().pose(), context.consumers(), mode, 0, 0xF000F0);
                    matrices.translate(0, 0, 0.3f);
                }
                int argb = (Math.max(5, Math.round(alpha * 255)) << 24) | (color & 0xFFFFFF);
                f.drawInBatch(c, x0, y0, argb, false, matrices.last().pose(), context.consumers(), mode, 0, 0xF000F0);
                matrices.popPose();
            }
        });
    }
}
