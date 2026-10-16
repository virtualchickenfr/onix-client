package dev.onix.client.gui;

/** Weiche, bildratenunabhaengige Animation eines Werts. */
public final class Anim {
    private float value;
    private long last = System.nanoTime();

    public Anim(float start) {
        this.value = start;
    }

    public float to(float target, float speed) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - last) / 1_000_000_000f);
        last = now;
        value += (target - value) * (1f - (float) Math.exp(-dt * speed));
        if (Math.abs(target - value) < 0.002f) value = target;
        return value;
    }

    public float get() {
        return value;
    }

    public void set(float v) {
        value = v;
        last = System.nanoTime();
    }

    /** Ease-out for values in 0..1. */
    public static float easeOut(float t) {
        float inv = 1f - Math.max(0f, Math.min(1f, t));
        return 1f - inv * inv * inv;
    }
}
