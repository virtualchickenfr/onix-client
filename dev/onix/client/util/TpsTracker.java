package dev.onix.client.util;

import net.minecraft.client.Minecraft;

/**
 * Estimates server TPS from how fast the world time advances compared to real time.
 * Rough but works on every server without plugins.
 */
public final class TpsTracker {
    private static long lastWorldTime = -1;
    private static long lastRealTime;
    private static double tps = 20;

    private TpsTracker() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            lastWorldTime = -1;
            return;
        }
        long now = System.currentTimeMillis();
        long worldTime = mc.level.getGameTime();
        if (lastWorldTime < 0) {
            lastWorldTime = worldTime;
            lastRealTime = now;
            return;
        }
        long dtReal = now - lastRealTime;
        if (dtReal >= 1000) {
            long dtWorld = worldTime - lastWorldTime;
            double sample = Math.max(0, Math.min(20, dtWorld / (dtReal / 1000.0)));
            tps = tps * 0.7 + sample * 0.3;
            lastWorldTime = worldTime;
            lastRealTime = now;
        }
    }

    public static double tps() {
        return tps;
    }
}
