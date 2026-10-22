package dev.onix.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.onix.client.config.ConfigFiles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Small arcade games in the Onix look: Blocks, Snake, Pong and 2048. Best scores are kept in config/onix/games.json. */
public class MiniGameScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static JsonObject bests;

    private final Screen parent;
    private final String game;
    private final Random rnd = new Random();
    private int score;
    private boolean over;
    private boolean paused;
    private long last = System.nanoTime();

    public MiniGameScreen(Screen parent, String game) {
        super(Component.literal(game));
        this.parent = parent;
        this.game = game;
        restart();
    }

    // ------------------------------------------------------------------ best scores

    private static Path file() {
        return ConfigFiles.dir().resolve("games.json");
    }

    public static int best(String game) {
        if (bests == null) {
            try {
                bests = Files.exists(file()) ? GSON.fromJson(Files.readString(file()), JsonObject.class) : new JsonObject();
            } catch (Exception e) {
                bests = new JsonObject();
            }
            if (bests == null) bests = new JsonObject();
        }
        return bests.has(game) ? bests.get(game).getAsInt() : 0;
    }

    private void finish() {
        over = true;
        if (score > best(game)) {
            bests.addProperty(game, score);
            try {
                Files.createDirectories(file().getParent());
                Files.writeString(file(), GSON.toJson(bests));
            } catch (Exception ignored) {
                // a lost best score is not worth an error
            }
            Notifications.show(game, "New best: " + score);
        }
    }

    private void restart() {
        score = 0;
        over = false;
        paused = false;
        switch (game) {
            case "Blocks" -> blocksStart();
            case "Snake" -> snakeStart();
            case "Pong" -> pongStart();
            default -> g2048Start();
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xE0050507);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        long now = System.nanoTime();
        float dt = Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        Theme.refresh();
        Theme.pixel = true;
        try {
            if (!paused && !over) update(dt);
            int bw = Math.min(width - 40, 300);
            int bh = Math.min(height - 70, 300);
            int x0 = (width - bw) / 2;
            int y0 = (height - bh) / 2 + 10;
            Theme.bevelWindow(ctx, x0 - 8, y0 - 34, x0 + bw + 8, y0 + bh + 8);
            Theme.pixelTitle(ctx, game, x0, y0 - 26, 1.5f, Theme.TEXT);
            String sc = "Score " + score + "   Best " + Math.max(best(game), score);
            Theme.text(ctx, sc, x0 + bw - Theme.width(sc), y0 - 22, Theme.TEXT_2);
            switch (game) {
                case "Blocks" -> blocksDraw(ctx, x0, y0, bw, bh);
                case "Snake" -> snakeDraw(ctx, x0, y0, bw, bh);
                case "Pong" -> pongDraw(ctx, x0, y0, bw, bh);
                default -> g2048Draw(ctx, x0, y0, bw, bh);
            }
            if (paused || over) {
                ctx.fill(x0, y0, x0 + bw, y0 + bh, 0xB0000000);
                Theme.pixelTitle(ctx, over ? "Game over" : "Paused", x0 + bw / 2 - (int) (font.width(over ? "Game over." : "Paused.") * 1.5f) / 2, y0 + bh / 2 - 16, 1.5f, Theme.TEXT);
                Theme.textCentered(ctx, over ? "Space or Enter: again · Esc: back" : "P: go on · Esc: back", x0 + bw / 2, y0 + bh / 2 + 6, Theme.TEXT_2);
            }
        } finally {
            Theme.pixel = false;
        }
    }

    private void update(float dt) {
        switch (game) {
            case "Blocks" -> blocksUpdate(dt);
            case "Snake" -> snakeUpdate(dt);
            case "Pong" -> pongUpdate(dt);
            default -> {}
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent onix$k) {
        int key = onix$k.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (over && (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER)) {
            restart();
            return true;
        }
        if (key == GLFW.GLFW_KEY_P) {
            paused = !paused;
            return true;
        }
        if (paused || over) return true;
        int dir = switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> 0;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> 1;
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> 2;
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> 3;
            case GLFW.GLFW_KEY_SPACE -> 4;
            default -> -1;
        };
        if (dir < 0) return super.keyPressed(onix$k);
        switch (game) {
            case "Blocks" -> blocksKey(dir);
            case "Snake" -> snakeKey(dir);
            case "Pong" -> pongKey(dir, true);
            default -> g2048Key(dir);
        }
        return true;
    }

    @Override
    public boolean keyReleased(net.minecraft.client.input.KeyEvent onix$k) {
        int key = onix$k.key();
        if (game.equals("Pong")) {
            if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) pongKey(2, false);
            if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) pongKey(3, false);
        }
        return super.keyReleased(onix$k);
    }

    private static void cell(GuiGraphics ctx, int x, int y, int s, int color) {
        ctx.fill(x, y, x + s, y + s, color);
        ctx.fill(x, y, x + s, y + 1, Theme.withAlpha(0xFFFFFFFF, 70));
        ctx.fill(x, y + s - 1, x + s, y + s, Theme.withAlpha(0xFF000000, 90));
    }

    // ------------------------------------------------------------------ Blocks (falling pieces)

    private static final int BW = 10, BH = 20;
    private static final int[][][] PIECES = {
            {{0, 1}, {1, 1}, {2, 1}, {3, 1}}, // I
            {{0, 0}, {1, 0}, {0, 1}, {1, 1}}, // O
            {{1, 0}, {0, 1}, {1, 1}, {2, 1}}, // T
            {{1, 0}, {2, 0}, {0, 1}, {1, 1}}, // S
            {{0, 0}, {1, 0}, {1, 1}, {2, 1}}, // Z
            {{0, 0}, {0, 1}, {1, 1}, {2, 1}}, // J
            {{2, 0}, {0, 1}, {1, 1}, {2, 1}}, // L
    };
    private static final int[] COLORS = {0xFF5EEAD4, 0xFFFBBF24, 0xFFA78BFA, 0xFF34D399, 0xFFFF5A5F, 0xFF4C8DFF, 0xFFFF9A3C};
    private int[][] board;
    private int[][] piece;
    private int pieceColor, px, py, next, lines;
    private float fall;

    private void blocksStart() {
        board = new int[BH][BW];
        lines = 0;
        next = rnd.nextInt(PIECES.length);
        spawn();
    }

    private void spawn() {
        int k = next;
        next = rnd.nextInt(PIECES.length);
        piece = new int[4][];
        for (int i = 0; i < 4; i++) piece[i] = PIECES[k][i].clone();
        pieceColor = COLORS[k];
        px = 3;
        py = 0;
        if (!fits(piece, px, py)) finish();
    }

    private boolean fits(int[][] p, int x, int y) {
        for (int[] c : p) {
            int cx = x + c[0], cy = y + c[1];
            if (cx < 0 || cx >= BW || cy >= BH) return false;
            if (cy >= 0 && board[cy][cx] != 0) return false;
        }
        return true;
    }

    private void lockPiece() {
        for (int[] c : piece) if (py + c[1] >= 0) board[py + c[1]][px + c[0]] = pieceColor;
        int cleared = 0;
        for (int y = BH - 1; y >= 0; y--) {
            boolean full = true;
            for (int x = 0; x < BW; x++) full &= board[y][x] != 0;
            if (full) {
                for (int yy = y; yy > 0; yy--) board[yy] = board[yy - 1].clone();
                board[0] = new int[BW];
                cleared++;
                y++;
            }
        }
        lines += cleared;
        score += new int[]{0, 100, 300, 500, 800}[cleared] * (1 + lines / 10);
        spawn();
    }

    private void blocksUpdate(float dt) {
        fall += dt;
        float speed = Math.max(0.08f, 0.6f - (lines / 10) * 0.05f);
        while (fall >= speed && !over) {
            fall -= speed;
            if (fits(piece, px, py + 1)) py++;
            else lockPiece();
        }
    }

    private void blocksKey(int dir) {
        switch (dir) {
            case 0 -> { if (fits(piece, px - 1, py)) px--; }
            case 1 -> { if (fits(piece, px + 1, py)) px++; }
            case 3 -> { if (fits(piece, px, py + 1)) { py++; score++; } }
            case 4 -> {
                while (fits(piece, px, py + 1)) { py++; score += 2; }
                lockPiece();
            }
            case 2 -> {
                int[][] r = new int[4][];
                for (int i = 0; i < 4; i++) r[i] = new int[]{2 - piece[i][1], piece[i][0]};
                for (int kick : new int[]{0, -1, 1, -2, 2}) {
                    if (fits(r, px + kick, py)) {
                        piece = r;
                        px += kick;
                        break;
                    }
                }
            }
            default -> {}
        }
    }

    private void blocksDraw(GuiGraphics ctx, int x0, int y0, int bw, int bh) {
        int s = Math.min(bh / BH, (bw - 70) / BW);
        int gx = x0 + (bw - 70 - s * BW) / 2;
        ctx.fill(gx - 1, y0 - 1, gx + s * BW + 1, y0 + s * BH + 1, 0xFF000000);
        ctx.fill(gx, y0, gx + s * BW, y0 + s * BH, Theme.SURFACE_SUNKEN);
        for (int y = 0; y < BH; y++) for (int x = 0; x < BW; x++) if (board[y][x] != 0) cell(ctx, gx + x * s, y0 + y * s, s, board[y][x]);
        int gy = py;
        while (fits(piece, px, gy + 1)) gy++;
        for (int[] c : piece) {
            if (gy + c[1] >= 0) ctx.fill(gx + (px + c[0]) * s, y0 + (gy + c[1]) * s, gx + (px + c[0]) * s + s, y0 + (gy + c[1]) * s + s, Theme.withAlpha(pieceColor, 50));
            if (py + c[1] >= 0) cell(ctx, gx + (px + c[0]) * s, y0 + (py + c[1]) * s, s, pieceColor);
        }
        int nx = gx + s * BW + 14;
        Theme.label(ctx, "Next", nx, y0, Theme.TEXT_3);
        for (int[] c : PIECES[next]) cell(ctx, nx + c[0] * 9, y0 + 12 + c[1] * 9, 9, COLORS[next]);
        Theme.label(ctx, "Lines", nx, y0 + 44, Theme.TEXT_3);
        Theme.text(ctx, String.valueOf(lines), nx, y0 + 54, Theme.TEXT);
    }

    // ------------------------------------------------------------------ Snake

    private static final int SW = 20, SH = 16;
    private final Deque<int[]> snake = new ArrayDeque<>();
    private int dirX, dirY, nextDirX, nextDirY, foodX, foodY;
    private float stepTime;

    private void snakeStart() {
        snake.clear();
        for (int i = 0; i < 3; i++) snake.addLast(new int[]{8 - i, 8});
        dirX = nextDirX = 1;
        dirY = nextDirY = 0;
        placeFood();
    }

    private void placeFood() {
        do {
            foodX = rnd.nextInt(SW);
            foodY = rnd.nextInt(SH);
        } while (onSnake(foodX, foodY));
    }

    private boolean onSnake(int x, int y) {
        for (int[] c : snake) if (c[0] == x && c[1] == y) return true;
        return false;
    }

    private void snakeKey(int dir) {
        int dx = dir == 0 ? -1 : dir == 1 ? 1 : 0;
        int dy = dir == 2 ? -1 : dir == 3 ? 1 : 0;
        if ((dx != 0 || dy != 0) && !(dx == -dirX && dy == -dirY)) {
            nextDirX = dx;
            nextDirY = dy;
        }
    }

    private void snakeUpdate(float dt) {
        stepTime += dt;
        float step = Math.max(0.06f, 0.13f - snake.size() * 0.001f);
        while (stepTime >= step && !over) {
            stepTime -= step;
            dirX = nextDirX;
            dirY = nextDirY;
            int[] head = snake.peekFirst();
            int hx = head[0] + dirX, hy = head[1] + dirY;
            if (hx < 0 || hy < 0 || hx >= SW || hy >= SH || onSnake(hx, hy)) {
                finish();
                return;
            }
            snake.addFirst(new int[]{hx, hy});
            if (hx == foodX && hy == foodY) {
                score += 10;
                placeFood();
            } else {
                snake.removeLast();
            }
        }
    }

    private void snakeDraw(GuiGraphics ctx, int x0, int y0, int bw, int bh) {
        int s = Math.min(bw / SW, bh / SH);
        int gx = x0 + (bw - s * SW) / 2;
        int gy = y0 + (bh - s * SH) / 2;
        ctx.fill(gx - 1, gy - 1, gx + s * SW + 1, gy + s * SH + 1, 0xFF000000);
        ctx.fill(gx, gy, gx + s * SW, gy + s * SH, Theme.SURFACE_SUNKEN);
        cell(ctx, gx + foodX * s + 1, gy + foodY * s + 1, s - 2, Theme.RED);
        boolean first = true;
        for (int[] c : snake) {
            cell(ctx, gx + c[0] * s, gy + c[1] * s, s, first ? Theme.accent() : Theme.lerpColor(Theme.accent(), Theme.SURFACE_4, 0.35f));
            first = false;
        }
    }

    // ------------------------------------------------------------------ Pong

    private float ballX, ballY, ballVX, ballVY, padY, cpuY;
    private boolean upHeld, downHeld;
    private int cpuScore;

    private void pongStart() {
        padY = cpuY = 0.5f;
        cpuScore = 0;
        serve(1);
    }

    private void serve(int towards) {
        ballX = 0.5f;
        ballY = 0.5f;
        ballVX = 0.45f * towards;
        ballVY = (rnd.nextFloat() - 0.5f) * 0.5f;
    }

    private void pongKey(int dir, boolean down) {
        if (dir == 2) upHeld = down;
        if (dir == 3) downHeld = down;
    }

    private void pongUpdate(float dt) {
        float pad = 0.18f;
        if (upHeld) padY -= dt * 0.9f;
        if (downHeld) padY += dt * 0.9f;
        padY = Math.max(pad / 2, Math.min(1 - pad / 2, padY));
        cpuY += Math.signum(ballY - cpuY) * Math.min(Math.abs(ballY - cpuY), dt * 0.55f);
        cpuY = Math.max(pad / 2, Math.min(1 - pad / 2, cpuY));
        ballX += ballVX * dt;
        ballY += ballVY * dt;
        if (ballY < 0.02f || ballY > 0.98f) {
            ballVY = -ballVY;
            ballY = Math.max(0.02f, Math.min(0.98f, ballY));
        }
        if (ballX < 0.05f && ballVX < 0 && Math.abs(ballY - padY) < pad / 2 + 0.02f) {
            ballVX = -ballVX * 1.06f;
            ballVY += (ballY - padY) * 2.2f;
            score++;
        }
        if (ballX > 0.95f && ballVX > 0 && Math.abs(ballY - cpuY) < pad / 2 + 0.02f) {
            ballVX = -ballVX * 1.04f;
            ballVY += (ballY - cpuY) * 2f;
        }
        if (ballX < 0) {
            cpuScore++;
            if (cpuScore >= 7) finish();
            else serve(1);
        }
        if (ballX > 1) {
            score += 5;
            serve(-1);
        }
    }

    private void pongDraw(GuiGraphics ctx, int x0, int y0, int bw, int bh) {
        ctx.fill(x0 - 1, y0 - 1, x0 + bw + 1, y0 + bh + 1, 0xFF000000);
        ctx.fill(x0, y0, x0 + bw, y0 + bh, Theme.SURFACE_SUNKEN);
        for (int y = y0 + 4; y < y0 + bh; y += 12) ctx.fill(x0 + bw / 2, y, x0 + bw / 2 + 1, y + 6, Theme.BORDER_STRONG);
        int ph = Math.round(bh * 0.18f);
        ctx.fill(x0 + 6, y0 + Math.round(padY * bh) - ph / 2, x0 + 10, y0 + Math.round(padY * bh) + ph / 2, Theme.accent());
        ctx.fill(x0 + bw - 10, y0 + Math.round(cpuY * bh) - ph / 2, x0 + bw - 6, y0 + Math.round(cpuY * bh) + ph / 2, Theme.TEXT_2);
        int bx = x0 + Math.round(ballX * bw), by = y0 + Math.round(ballY * bh);
        ctx.fill(bx - 3, by - 3, bx + 3, by + 3, Theme.TEXT);
        Theme.textCentered(ctx, "Computer " + cpuScore + "/7", x0 + bw * 3 / 4, y0 + 6, Theme.TEXT_3);
    }

    // ------------------------------------------------------------------ 2048

    private int[][] tiles;

    private void g2048Start() {
        tiles = new int[4][4];
        addTile();
        addTile();
    }

    private void addTile() {
        int free = 0;
        for (int[] row : tiles) for (int v : row) if (v == 0) free++;
        if (free == 0) return;
        int k = rnd.nextInt(free);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) if (tiles[y][x] == 0 && k-- == 0) tiles[y][x] = rnd.nextInt(10) == 0 ? 4 : 2;
    }

    private void g2048Key(int dir) {
        if (dir > 3) return;
        boolean moved = false;
        for (int i = 0; i < 4; i++) {
            int[] line = new int[4];
            for (int j = 0; j < 4; j++) line[j] = get(dir, i, j);
            int[] out = new int[4];
            int n = 0;
            int lastV = 0;
            for (int v : line) {
                if (v == 0) continue;
                if (lastV == v) {
                    out[n - 1] = v * 2;
                    score += v * 2;
                    lastV = 0;
                } else {
                    out[n++] = v;
                    lastV = v;
                }
            }
            for (int j = 0; j < 4; j++) {
                if (get(dir, i, j) != out[j]) moved = true;
                set(dir, i, j, out[j]);
            }
        }
        if (moved) addTile();
        if (!canMove()) finish();
    }

    /** Cell j of line i, read in the direction of the move (j = 0 is where tiles slide to). */
    private int get(int dir, int i, int j) {
        return switch (dir) {
            case 0 -> tiles[i][j];
            case 1 -> tiles[i][3 - j];
            case 2 -> tiles[j][i];
            default -> tiles[3 - j][i];
        };
    }

    private void set(int dir, int i, int j, int v) {
        switch (dir) {
            case 0 -> tiles[i][j] = v;
            case 1 -> tiles[i][3 - j] = v;
            case 2 -> tiles[j][i] = v;
            default -> tiles[3 - j][i] = v;
        }
    }

    private boolean canMove() {
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (tiles[y][x] == 0) return true;
                if (x < 3 && tiles[y][x] == tiles[y][x + 1]) return true;
                if (y < 3 && tiles[y][x] == tiles[y + 1][x]) return true;
            }
        }
        return false;
    }

    private void g2048Draw(GuiGraphics ctx, int x0, int y0, int bw, int bh) {
        int size = Math.min(bw, bh);
        int gx = x0 + (bw - size) / 2;
        int s = (size - 5 * 6) / 4;
        ctx.fill(gx, y0, gx + size, y0 + size, Theme.SURFACE_SUNKEN);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                int v = tiles[y][x];
                int cx = gx + 6 + x * (s + 6), cy = y0 + 6 + y * (s + 6);
                if (v == 0) {
                    ctx.fill(cx, cy, cx + s, cy + s, Theme.SURFACE_2);
                    continue;
                }
                int level = Integer.numberOfTrailingZeros(v);
                int color = Theme.lerpColor(Theme.SURFACE_4, Theme.accent(), Math.min(1f, level / 11f));
                cell(ctx, cx, cy, s, color);
                String t = String.valueOf(v);
                float scale = t.length() > 3 ? 1.2f : 1.8f;
                int tw = Math.round(font.width(t) * scale);
                ctx.pose().pushMatrix();
                ctx.pose().translate(cx + (s - tw) / 2f, cy + (s - 8 * scale) / 2f);
                ctx.pose().scale(scale, scale);
                ctx.drawString(font, t, 0, 0, level > 6 ? Theme.onAccent() : Theme.TEXT, false);
                ctx.pose().popMatrix();
            }
        }
    }
}
