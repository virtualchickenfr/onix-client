package gg.carbon.client.ui.anim;

/**
 * Time based tween between two values. Retargeting mid-flight starts a new tween from the
 * current value, so animations never jump.
 */
public final class Anim {
	private float from;
	private float to;
	private long start;
	private final long durationNs;
	private final Easing easing;

	public Anim(float initial, long durationMs, Easing easing) {
		this.from = initial;
		this.to = initial;
		this.durationNs = durationMs * 1_000_000L;
		this.easing = easing;
		this.start = System.nanoTime() - this.durationNs;
	}

	public Anim(float initial, long durationMs) {
		this(initial, durationMs, Easing.OUT_CUBIC);
	}

	public float progress() {
		if (durationNs <= 0) return 1;
		return Easing.clamp01((System.nanoTime() - start) / (float) durationNs);
	}

	public float get() {
		float p = progress();
		return from + (to - from) * easing.apply(p);
	}

	public void target(float value) {
		if (value == to) return;
		from = get();
		to = value;
		start = System.nanoTime();
	}

	/** Restarts from {@code fromValue} towards {@code toValue}. */
	public void play(float fromValue, float toValue) {
		from = fromValue;
		to = toValue;
		start = System.nanoTime();
	}

	public void snap(float value) {
		from = to = value;
		start = System.nanoTime() - durationNs;
	}

	public float target() {
		return to;
	}

	public boolean done() {
		return progress() >= 1;
	}
}
