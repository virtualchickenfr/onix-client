package gg.carbon.client.module.impl.pvp;

import com.mojang.blaze3d.platform.InputConstants;
import gg.carbon.client.module.Category;
import gg.carbon.client.module.Module;
import gg.carbon.client.setting.KeySetting;
import gg.carbon.client.setting.NumberSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

/**
 * Port of the AimAssist mod: gently turns your view towards the nearest hostile mob in front of you.
 * Only works in a private singleplayer world (not on servers, not opened to LAN) and switches itself off elsewhere.
 */
public class AimAssist extends Module {
	private final KeySetting key = add(new KeySetting("Toggle Key", "Switches aim assist on and off", InputConstants.KEY_J));
	private final NumberSetting range = add(new NumberSetting("Range", "How far away mobs are picked up", 8, 3, 16, 0.5).suffix(" blocks"));
	private final NumberSetting fov = add(new NumberSetting("FOV", "Half the cone angle in front of you that counts", 30, 5, 90, 1).suffix("°"));
	private final NumberSetting strength = add(new NumberSetting("Strength", "How strongly the view is pulled per tick (1 = snap)", 0.25, 0.05, 1, 0.05));

	public AimAssist() {
		super("aimassist", "Aim Assist", "Gently aims at hostile mobs – singleplayer only", Category.PVP);
	}

	@Override
	public KeySetting toggleKey() {
		return key;
	}

	@Override
	public void onTick() {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return;
		if (!isPrivateSingleplayer()) {
			player.displayClientMessage(Component.literal("§c[Aim Assist] Funktioniert nur im Einzelspieler"), true);
			setEnabled(false);
			return;
		}
		if (mc.screen != null || player.isSpectator() || !player.isAlive()) return;

		LivingEntity target = findTarget(player);
		if (target != null) assist(player, target);
	}

	/** Only a local world that is not opened to LAN. */
	private static boolean isPrivateSingleplayer() {
		IntegratedServer server = mc.getSingleplayerServer();
		return mc.hasSingleplayerServer() && server != null && !server.isPublished();
	}

	private static Vec3 aimPoint(LivingEntity entity) {
		return entity.position().add(0, entity.getBbHeight() * 0.6, 0);
	}

	private LivingEntity findTarget(LocalPlayer player) {
		double maxRange = range.f();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);

		LivingEntity best = null;
		double bestCos = Math.cos(Math.toRadians(fov.f()));
		for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(maxRange),
			e -> e != player && e.isAlive() && e instanceof Enemy && !e.isInvisible())) {
			Vec3 to = aimPoint(entity).subtract(eye);
			double dist = to.length();
			if (dist > maxRange || dist < 0.01) continue;

			double cos = look.dot(to) / dist;
			if (cos < bestCos || !player.hasLineOfSight(entity)) continue;
			best = entity;
			bestCos = cos;
		}
		return best;
	}

	private void assist(LocalPlayer player, LivingEntity target) {
		Vec3 d = aimPoint(target).subtract(player.getEyePosition());
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		float wantedYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
		float wantedPitch = (float) -Math.toDegrees(Math.atan2(d.y, horizontal));

		float s = strength.f();
		player.setYRot(player.getYRot() + Mth.wrapDegrees(wantedYaw - player.getYRot()) * s);
		player.setXRot(Mth.clamp(player.getXRot() + (wantedPitch - player.getXRot()) * s, -90f, 90f));
	}
}
