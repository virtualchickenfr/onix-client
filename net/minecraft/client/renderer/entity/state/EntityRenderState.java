package net.minecraft.client.renderer.entity.state;

import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.CrashReportCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * <p>Interface {@link net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState} injected by mod fabric-rendering-v1</p>
 */
@Environment(EnvType.CLIENT)
public class EntityRenderState implements FabricRenderState {
	public EntityType<?> entityType;
	public double x;
	public double y;
	public double z;
	public float ageInTicks;
	public float boundingBoxWidth;
	public float boundingBoxHeight;
	public float eyeHeight;
	public double distanceToCameraSq;
	public boolean isInvisible;
	public boolean isDiscrete;
	public boolean displayFireAnimation;
	@Nullable
	public Vec3 passengerOffset;
	@Nullable
	public Component nameTag;
	@Nullable
	public Vec3 nameTagAttachment;
	@Nullable
	public List<EntityRenderState.LeashState> leashStates;
	@Nullable
	public HitboxesRenderState hitboxesRenderState;
	@Nullable
	public ServerHitboxesRenderState serverHitboxesRenderState;

	public void fillCrashReportCategory(CrashReportCategory crashReportCategory) {
		crashReportCategory.setDetail("EntityRenderState", this.getClass().getCanonicalName());
		crashReportCategory.setDetail("Entity's Exact location", String.format(Locale.ROOT, "%.2f, %.2f, %.2f", this.x, this.y, this.z));
	}

	@Environment(EnvType.CLIENT)
	public static class LeashState {
		public Vec3 offset = Vec3.ZERO;
		public Vec3 start = Vec3.ZERO;
		public Vec3 end = Vec3.ZERO;
		public int startBlockLight = 0;
		public int endBlockLight = 0;
		public int startSkyLight = 15;
		public int endSkyLight = 15;
		public boolean slack = true;
	}
}
