package dev.onix.client.util;

import net.minecraft.core.BlockPos;

/** Remembers when the game and the current world session started, and where the player last died. */
public final class SessionTracker {
    public static final long GAME_START = System.currentTimeMillis();
    private static long joinTime = System.currentTimeMillis();
    private static BlockPos deathPos;
    private static String deathDimension = "";
    private static long deathTime;
    private static boolean wasDead;

    private SessionTracker() {}

    public static void onJoin() {
        joinTime = System.currentTimeMillis();
        wasDead = false;
    }

    public static long joinTime() {
        return joinTime;
    }

    public static void tick(net.minecraft.client.Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        boolean dead = mc.player.isDeadOrDying() || mc.player.getHealth() <= 0;
        if (dead && !wasDead) {
            deathPos = mc.player.blockPosition();
            deathDimension = mc.level.dimension().identifier().getPath();
            deathTime = System.currentTimeMillis();
            CombatTracker.onOwnDeath();
        }
        wasDead = dead;
    }

    public static BlockPos deathPos() { return deathPos; }
    public static String deathDimension() { return deathDimension; }
    public static long deathTime() { return deathTime; }

    public static String formatDuration(long millis, boolean withHours) {
        long s = millis / 1000;
        if (withHours || s >= 3600) return String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60);
        return String.format("%02d:%02d", s / 60, s % 60);
    }
}
