package gg.carbon.client.module.impl.performance;

import gg.carbon.client.module.Category;
import gg.carbon.client.module.Module;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.NumberSetting;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

public final class PerformanceMods {
	private PerformanceMods() {}

	public static class UnfocusedFps extends Module {
		public final NumberSetting limit = add(new NumberSetting("Background FPS", "Frame cap while the window is unfocused", 30, 5, 120, 5));

		public UnfocusedFps() {
			super("unfocusedfps", "Background FPS", "Caps FPS while Minecraft is in the background to save power", Category.PERFORMANCE);
		}

		public int apply(int vanilla) {
			if (!enabled() || mc.isWindowActive()) return vanilla;
			return Math.min(vanilla, limit.i());
		}
	}

	public static class ParticleLimiter extends Module {
		public final NumberSetting amount = add(new NumberSetting("Particle Amount", "Percentage of particles to keep", 50, 0, 100, 5).suffix("%"));

		public ParticleLimiter() {
			super("particlelimiter", "Particle Limiter", "Reduces the number of particles spawned", Category.PERFORMANCE);
		}

		public boolean drop() {
			return enabled() && ThreadLocalRandom.current().nextInt(100) >= amount.i();
		}
	}

	public static class EntityCulling extends Module {
		public final NumberSetting distance = add(new NumberSetting("Entity Distance", "Max distance for non-player entities", 48, 8, 128, 4).suffix(" blocks"));
		public final BoolSetting players = add(new BoolSetting("Cull Players", "Also apply the distance to players", false));
		public final NumberSetting itemDistance = add(new NumberSetting("Item Distance", "Max distance for dropped items and armor stands", 24, 4, 128, 4).suffix(" blocks"));

		public EntityCulling() {
			super("entityculling", "Entity Culling", "Skips rendering far away entities", Category.PERFORMANCE);
		}

		public boolean cull(Entity entity) {
			if (!enabled() || mc.player == null || entity == mc.player) return false;
			if (entity instanceof Player && !players.on()) return false;
			double max = entity instanceof ItemEntity || entity instanceof ArmorStand ? itemDistance.get() : distance.get();
			return entity.distanceToSqr(mc.player) > max * max;
		}
	}
}
