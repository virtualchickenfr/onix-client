package dev.onix.client.util;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;

/** Counts rendered frames for the dev test's performance check. */
public final class PerfProbe {
    private PerfProbe() {}

    private static long start = -1, last;
    private static int frames;
    private static long worst;
    private static boolean hooked;

    /** Called every frame (from the HUD render). */
    public static void frame() {
        if (start < 0) return;
        long now = System.nanoTime();
        if (last > 0) worst = Math.max(worst, now - last);
        last = now;
        frames++;
    }

    public static void start() {
        start = System.nanoTime();
        last = 0;
        frames = 0;
        worst = 0;
    }

    /** Average frames per second since start. */
    public static int stop() {
        double s = (System.nanoTime() - start) / 1e9;
        start = -1;
        return (int) Math.round(frames / Math.max(0.001, s));
    }

    public static long worst() {
        return worst / 1_000_000;
    }
}
