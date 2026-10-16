package gg.carbon.client.util;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Lightweight client-side statistics fed by Carbon's mixins. */
public final class Stats {
	private Stats() {}

	private static final Deque<Long> LEFT = new ArrayDeque<>();
	private static final Deque<Long> RIGHT = new ArrayDeque<>();

	// TPS estimation from server time packets
	private static long lastTimePacketMs = -1;
	private static long lastGameTime = -1;
	private static double tps = 20;

	// combat
	private static int pendingTarget = -1;
	private static int pendingTicks;
	private static int pendingHurt;
	private static double pendingReach;
	private static double lastReach = -1;
	private static long lastReachTime;
	private static int combo;
	private static long lastComboHit;
	private static int lastPlayerHurt;

	// jump reset
	private static int ticksSinceHurt = -1;
	private static int lastJumpDelay = -1;
	private static long lastJumpResetTime;
	private static boolean wasOnGround = true;

	// totems
	private static int totemPops;

	// mouse movement (for mousestrokes)
	private static double mouseDX, mouseDY;

	public static final long SESSION_START = System.currentTimeMillis();

	// ---------------------------------------------------------------- clicks

	public static void click(int button) {
		long now = System.currentTimeMillis();
		if (button == 0) LEFT.add(now);
		else if (button == 1) RIGHT.add(now);
	}

	private static int cps(Deque<Long> q) {
		long cutoff = System.currentTimeMillis() - 1000;
		while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
		return q.size();
	}

	public static int leftCps() {
		return cps(LEFT);
	}

	public static int rightCps() {
		return cps(RIGHT);
	}

	// ---------------------------------------------------------------- tps

	public static void onTimePacket(long gameTime) {
		long now = System.currentTimeMillis();
		if (lastTimePacketMs > 0 && lastGameTime >= 0) {
			double seconds = (now - lastTimePacketMs) / 1000.0;
			long ticks = gameTime - lastGameTime;
			if (seconds > 0.2 && ticks >= 0) {
				double sample = Math.min(20, ticks / seconds);
				tps = tps * 0.6 + sample * 0.4;
			}
		}
		lastTimePacketMs = now;
		lastGameTime = gameTime;
	}

	public static double tps() {
		return tps;
	}

	public static void resetWorld() {
		lastTimePacketMs = -1;
		lastGameTime = -1;
		tps = 20;
		combo = 0;
		totemPops = 0;
	}

	// ---------------------------------------------------------------- combat

	public static void onAttack(Entity target) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || !(target instanceof LivingEntity living)) return;
		Vec3 eye = mc.player.getEyePosition();
		Vec3 hit = mc.hitResult != null ? mc.hitResult.getLocation() : target.getBoundingBox().getCenter();
		pendingTarget = target.getId();
		pendingTicks = 8;
		pendingHurt = living.hurtTime;
		pendingReach = eye.distanceTo(hit);
	}

	public static void tick() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;

		if (pendingTicks > 0) {
			pendingTicks--;
			Entity e = mc.level.getEntity(pendingTarget);
			if (e instanceof LivingEntity living && living.hurtTime > pendingHurt) {
				lastReach = pendingReach;
				lastReachTime = System.currentTimeMillis();
				combo++;
				lastComboHit = System.currentTimeMillis();
				pendingTicks = 0;
			}
		}
		if (combo > 0 && System.currentTimeMillis() - lastComboHit > 3000) combo = 0;

		int hurt = mc.player.hurtTime;
		if (hurt > lastPlayerHurt && hurt > 0) {
			combo = 0;
			ticksSinceHurt = 0;
		} else if (ticksSinceHurt >= 0) {
			ticksSinceHurt++;
			if (ticksSinceHurt > 20) ticksSinceHurt = -1;
		}
		lastPlayerHurt = hurt;

		boolean onGround = mc.player.onGround();
		if (wasOnGround && !onGround && mc.player.getDeltaMovement().y > 0 && ticksSinceHurt >= 0) {
			lastJumpDelay = ticksSinceHurt;
			lastJumpResetTime = System.currentTimeMillis();
			ticksSinceHurt = -1;
		}
		wasOnGround = onGround;

		mouseDX *= 0.5;
		mouseDY *= 0.5;
	}

	public static double lastReach() {
		return System.currentTimeMillis() - lastReachTime < 4000 ? lastReach : -1;
	}

	public static int combo() {
		return combo;
	}

	public static int lastJumpDelay() {
		return System.currentTimeMillis() - lastJumpResetTime < 4000 ? lastJumpDelay : -1;
	}

	// ---------------------------------------------------------------- totems

	public static void onTotemPop() {
		totemPops++;
	}

	public static int totemPops() {
		return totemPops;
	}

	// ---------------------------------------------------------------- mouse

	public static void onMouseTurn(double dx, double dy) {
		mouseDX += dx;
		mouseDY += dy;
	}

	public static double mouseDX() {
		return mouseDX;
	}

	public static double mouseDY() {
		return mouseDY;
	}
}
