package net.minecraft.entity;

import net.minecraft.util.math.MathHelper;

public class LimbAnimator {
	private float lastSpeed;
	/**
	 * The speed of the limb movement, also affects the amplitude of their swing.
	 */
	private float speed;
	/**
	 * How far the limbs have progressed in swinging over time.
	 * 
	 * <p>This value is uncapped. It's in arbitrary units and is scaled differently by different entity models
	 * (shorter legs need to swing faster to make the entity appear to be running at the same speed).
	 */
	private float animationProgress;
	/**
	 * Like {@link #speed}, affects the speed of the limb movement, but without affecting the amplitude of the limb swings.
	 * Used by baby zombies etc.
	 */
	private float timeScale = 1.0F;

	public void setSpeed(float speed) {
		this.speed = speed;
	}

	/**
	 * Called every tick to update limbs.
	 * 
	 * @param speedChangeRate the rate that the current speed will be updated to the target speed, from 0 to 1.
	 * A value of 1 means the target speed will be reached immediately.
	 * A value of 0.5 means that the speed will be updated half way towards the target speed each time this method is called.
	 * Used to smooth out changes in amplitude
	 * @param timeScale affects the speed of the limb movement, but without affecting the amplitude of the limb swings
	 * @param targetSpeed the target limb speed, that may be reached over multiple ticks.
	 * Limb speed affects the amplitude of the limb swings as well as how fast they move
	 */
	public void updateLimbs(float targetSpeed, float speedChangeRate, float timeScale) {
		this.lastSpeed = this.speed;
		this.speed = this.speed + (targetSpeed - this.speed) * speedChangeRate;
		this.animationProgress = this.animationProgress + this.speed;
		this.timeScale = timeScale;
	}

	public void reset() {
		this.lastSpeed = 0.0F;
		this.speed = 0.0F;
		this.animationProgress = 0.0F;
	}

	public float getSpeed() {
		return this.speed;
	}

	public float getAmplitude(float tickProgress) {
		return Math.min(MathHelper.lerp(tickProgress, this.lastSpeed, this.speed), 1.0F);
	}

	public float getAnimationProgress() {
		return this.animationProgress * this.timeScale;
	}

	public float getAnimationProgress(float tickProgress) {
		return (this.animationProgress - this.speed * (1.0F - tickProgress)) * this.timeScale;
	}

	public boolean isLimbMoving() {
		return this.speed > 1.0E-5F;
	}
}
