package net.minecraft.client.render.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.SkullBlock;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.EntityPose;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class LivingEntityRenderState extends EntityRenderState {
	public float bodyYaw;
	/**
	 * The entity's head yaw relative to its body yaw. For example, if the entity is facing straight ahead, this value is 0.
	 */
	public float relativeHeadYaw;
	public float pitch;
	public float deathTime;
	public float limbSwingAnimationProgress;
	public float limbSwingAmplitude;
	public float baseScale = 1.0F;
	public float ageScale = 1.0F;
	public boolean flipUpsideDown;
	public boolean shaking;
	public boolean baby;
	public boolean touchingWater;
	public boolean usingRiptide;
	public boolean hurt;
	public boolean invisibleToPlayer;
	public boolean hasOutline;
	@Nullable
	public Direction sleepingDirection;
	@Nullable
	public Text customName;
	public EntityPose pose = EntityPose.STANDING;
	public final ItemRenderState headItemRenderState = new ItemRenderState();
	public float headItemAnimationProgress;
	@Nullable
	public SkullBlock.SkullType wearingSkullType;
	@Nullable
	public ProfileComponent wearingSkullProfile;

	public boolean isInPose(EntityPose pose) {
		return this.pose == pose;
	}
}
