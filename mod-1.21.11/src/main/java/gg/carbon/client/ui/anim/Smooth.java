package gg.carbon.client.ui.anim;

/** Frame-rate independent exponential follower — ideal for hover glows and smooth scrolling. */
public final class Smooth {
	private float value;
	private float target;
	private final float speed;
	private long last = System.nanoTime();

	public Smooth(float initial, float speed) {
		this.value = initial;
		this.target = initial;
		this.speed = speed;
	}

	public float get() {
		long now = System.nanoTime();
		float dt = Math.min((now - last) / 1_000_000_000f, 0.1f);
		last = now;
		value += (target - value) * (1 - (float) Math.exp(-speed * dt));
		if (Math.abs(target - value) < 0.0005f) value = target;
		return value;
	}

	public void target(float t) {
		this.target = t;
	}

	public float target() {
		return target;
	}

	public void snap(float v) {
		value = target = v;
		last = System.nanoTime();
	}
}
