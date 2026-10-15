package dev.onix.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Shared combat state: last target, reach, combo, kills and deaths.
 * Fed by the attack callback and the client tick; read by many modules.
 */
public final class CombatTracker {
    private static final List<Consumer<Entity>> HIT_LISTENERS = new ArrayList<>();
    private static final List<Consumer<Entity>> KILL_LISTENERS = new ArrayList<>();

    private static Entity target;
    private static long lastHit;
    private static double lastReach = -1;
    private static int combo;
    private static int bestCombo;
    private static int hits;
    private static int kills;
    private static int deaths;
    private static int lastHurtTime;

    private CombatTracker() {}

    public static void onHit(Consumer<Entity> listener) {
        HIT_LISTENERS.add(listener);
    }

    public static void onKill(Consumer<Entity> listener) {
        KILL_LISTENERS.add(listener);
    }

    /** Called from the attack callback (client side). */
    public static void onAttack(Entity entity) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Vec3d eye = mc.player.getCameraPosVec(1f);
        Box box = entity.getBoundingBox();
        double x = MathHelper.clamp(eye.x, box.minX, box.maxX);
        double y = MathHelper.clamp(eye.y, box.minY, box.maxY);
        double z = MathHelper.clamp(eye.z, box.minZ, box.maxZ);
        lastReach = eye.distanceTo(new Vec3d(x, y, z));
        if (entity instanceof LivingEntity living && living.hurtTime <= 0 && living.isAlive()) {
            combo++;
            bestCombo = Math.max(bestCombo, combo);
            hits++;
        }
        target = entity;
        lastHit = System.currentTimeMillis();
        for (Consumer<Entity> l : HIT_LISTENERS) l.accept(entity);
    }

    public static void tick(MinecraftClient mc) {
        if (mc.player == null) {
            combo = 0;
            return;
        }
        int hurt = mc.player.hurtTime;
        if (hurt > lastHurtTime) combo = 0;
        lastHurtTime = hurt;
        if (System.currentTimeMillis() - lastHit > 3000) combo = 0;

        if (target != null && System.currentTimeMillis() - lastHit < 3000) {
            boolean dead = target instanceof LivingEntity living
                    ? living.isDead() || living.getHealth() <= 0
                    : target.isRemoved();
            if (dead) {
                kills++;
                Entity killed = target;
                target = null;
                for (Consumer<Entity> l : KILL_LISTENERS) l.accept(killed);
            }
        }
    }

    static void onOwnDeath() {
        deaths++;
        combo = 0;
    }

    public static void resetStats() {
        kills = 0;
        deaths = 0;
        hits = 0;
        bestCombo = 0;
    }

    public static Entity target() { return target; }
    public static long lastHitTime() { return lastHit; }
    public static double lastReach() { return lastReach; }
    public static int combo() { return combo; }
    public static int bestCombo() { return bestCombo; }
    public static int hits() { return hits; }
    public static int kills() { return kills; }
    public static int deaths() { return deaths; }
}
