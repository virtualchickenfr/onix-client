package net.minecraft.entity.player;

import net.minecraft.nbt.NbtCompound;

public class PlayerAbilities {
	private static final boolean DEFAULT_INVULNERABLE = false;
	private static final boolean DEFAULT_FLYING = false;
	private static final boolean DEFAULT_ALLOW_FLYING = false;
	private static final boolean DEFAULT_CREATIVE_MODE = false;
	private static final boolean DEFAULT_ALLOW_MODIFY_WORLD = true;
	private static final float DEFAULT_FLY_SPEED = 0.05F;
	private static final float DEFAULT_WALK_SPEED = 0.1F;
	public boolean invulnerable;
	public boolean flying;
	public boolean allowFlying;
	public boolean creativeMode;
	public boolean allowModifyWorld = true;
	private float flySpeed = 0.05F;
	private float walkSpeed = 0.1F;

	public void writeNbt(NbtCompound nbt) {
		NbtCompound nbtCompound = new NbtCompound();
		nbtCompound.putBoolean("invulnerable", this.invulnerable);
		nbtCompound.putBoolean("flying", this.flying);
		nbtCompound.putBoolean("mayfly", this.allowFlying);
		nbtCompound.putBoolean("instabuild", this.creativeMode);
		nbtCompound.putBoolean("mayBuild", this.allowModifyWorld);
		nbtCompound.putFloat("flySpeed", this.flySpeed);
		nbtCompound.putFloat("walkSpeed", this.walkSpeed);
		nbt.put("abilities", nbtCompound);
	}

	public void readNbt(NbtCompound nbt) {
		NbtCompound nbtCompound = nbt.getCompoundOrEmpty("abilities");
		this.invulnerable = nbtCompound.getBoolean("invulnerable", false);
		this.flying = nbtCompound.getBoolean("flying", false);
		this.allowFlying = nbtCompound.getBoolean("mayfly", false);
		this.creativeMode = nbtCompound.getBoolean("instabuild", false);
		this.flySpeed = nbtCompound.getFloat("flySpeed", 0.05F);
		this.walkSpeed = nbtCompound.getFloat("walkSpeed", 0.1F);
		this.allowModifyWorld = nbtCompound.getBoolean("mayBuild", true);
	}

	public float getFlySpeed() {
		return this.flySpeed;
	}

	public void setFlySpeed(float flySpeed) {
		this.flySpeed = flySpeed;
	}

	public float getWalkSpeed() {
		return this.walkSpeed;
	}

	public void setWalkSpeed(float walkSpeed) {
		this.walkSpeed = walkSpeed;
	}
}
