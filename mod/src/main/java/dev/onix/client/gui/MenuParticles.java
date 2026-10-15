package dev.onix.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/** Menu background particles matching the theme (petals, snow, embers, sparks). */
public final class MenuParticles {
    private static final int COUNT = 70;
    private static final Random RANDOM = new Random();

    private final float[] x = new float[COUNT];
    private final float[] y = new float[COUNT];
    private final float[] speed = new float[COUNT];
    private final float[] phase = new float[COUNT];
    private final int[] size = new int[COUNT];
    private boolean initialized;
    private float time;
    private long last = System.nanoTime();

    public void render(DrawContext ctx, int width, int height, float opacity) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
        time += dt;
        if (!initialized) {
            for (int i = 0; i < COUNT; i++) reset(i, width, height, true);
            initialized = true;
        }
        ThemeDef theme = Theme.current();
        int color = theme.particle();
        for (int i = 0; i < COUNT; i++) {
            float sway = MathHelper.sin(time * 0.9f + phase[i]);
            switch (theme.ambient()) {
                case PETALS -> {
                    y[i] += speed[i] * dt * 18f;
                    x[i] += (sway * 12f + 8f) * dt;
                }
                case SNOW -> {
                    y[i] += speed[i] * dt * 14f;
                    x[i] += sway * 6f * dt;
                }
                case EMBERS -> {
                    y[i] -= speed[i] * dt * 16f;
                    x[i] += sway * 8f * dt;
                }
                case SPARKS -> {
                    y[i] -= speed[i] * dt * 4f;
                    x[i] += sway * 3f * dt;
                }
            }
            if (y[i] > height + 6 || y[i] < -6 || x[i] > width + 6 || x[i] < -6) reset(i, width, height, false);

            int px = (int) x[i];
            int py = (int) y[i];
            int s = size[i];
            float twinkle = 0.55f + 0.45f * MathHelper.sin(time * 3f + phase[i] * 2f);
            int alpha = (int) (opacity * 255 * (theme.ambient() == ThemeDef.Ambient.SNOW ? 0.8f : twinkle));
            int c = Theme.withAlpha(color, alpha);
            switch (theme.ambient()) {
                case PETALS -> {
                    // tiny blossom: two offset rectangles read as a rotated petal
                    boolean flip = MathHelper.sin(time * 2f + phase[i]) > 0;
                    ctx.fill(px, py, px + s + 1, py + s, c);
                    ctx.fill(px + (flip ? 1 : -1), py + s, px + s + (flip ? 1 : 0), py + s + 1, Theme.fade(c, 0.7f));
                }
                case EMBERS, SPARKS -> {
                    ctx.fill(px - 1, py - 1, px + s + 1, py + s + 1, Theme.fade(c, 0.25f));
                    ctx.fill(px, py, px + s, py + s, c);
                }
                case SNOW -> ctx.fill(px, py, px + s, py + s, c);
            }
        }
    }

    private void reset(int i, int width, int height, boolean anywhere) {
        ThemeDef.Ambient ambient = Theme.current().ambient();
        x[i] = RANDOM.nextFloat() * width;
        if (anywhere) y[i] = RANDOM.nextFloat() * height;
        else if (ambient == ThemeDef.Ambient.EMBERS || ambient == ThemeDef.Ambient.SPARKS) y[i] = height + 4;
        else {
            y[i] = -4;
            x[i] = RANDOM.nextFloat() * (width + 40) - 40;
        }
        speed[i] = 0.4f + RANDOM.nextFloat();
        phase[i] = RANDOM.nextFloat() * 6.28f;
        size[i] = ambient == ThemeDef.Ambient.SPARKS ? 1 : 1 + RANDOM.nextInt(2);
    }
}
