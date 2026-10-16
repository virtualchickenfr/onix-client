package gg.carbon.client.ui.anim;

/** Easing curves. All take t in [0,1] and return the eased progress. */
public enum Easing {
	LINEAR {
		public float apply(float t) { return t; }
	},
	OUT_QUAD {
		public float apply(float t) { return 1 - (1 - t) * (1 - t); }
	},
	OUT_CUBIC {
		public float apply(float t) { float u = 1 - t; return 1 - u * u * u; }
	},
	OUT_QUART {
		public float apply(float t) { float u = 1 - t; return 1 - u * u * u * u; }
	},
	OUT_QUINT {
		public float apply(float t) { float u = 1 - t; return 1 - u * u * u * u * u; }
	},
	OUT_EXPO {
		public float apply(float t) { return t >= 1 ? 1 : 1 - (float) Math.pow(2, -10 * t); }
	},
	IN_OUT_CUBIC {
		public float apply(float t) {
			return t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
		}
	},
	IN_OUT_QUINT {
		public float apply(float t) {
			return t < 0.5f ? 16 * t * t * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 5) / 2;
		}
	},
	/** cubic-bezier(.2, .75, .2, 1) — snappy start, long soft landing. */
	SMOOTH {
		public float apply(float t) {
			return bezier(t, 0.2f, 0.75f, 0.2f, 1f);
		}
	},
	OUT_BACK {
		public float apply(float t) {
			float c1 = 1.25f, c3 = c1 + 1;
			float u = t - 1;
			return 1 + c3 * u * u * u + c1 * u * u;
		}
	};

	public abstract float apply(float t);

	/** Evaluates a CSS-style cubic bezier (P0=(0,0), P3=(1,1)) at x = t. */
	static float bezier(float t, float x1, float y1, float x2, float y2) {
		if (t <= 0) return 0;
		if (t >= 1) return 1;
		float u = t;
		for (int i = 0; i < 8; i++) {
			float x = curve(u, x1, x2) - t;
			float d = (curve(u + 1e-4f, x1, x2) - curve(u - 1e-4f, x1, x2)) / 2e-4f;
			if (Math.abs(d) < 1e-6f) break;
			u = clamp01(u - x / d);
		}
		return curve(u, y1, y2);
	}

	private static float curve(float u, float p1, float p2) {
		float v = 1 - u;
		return 3 * v * v * u * p1 + 3 * v * u * u * p2 + u * u * u;
	}

	public static float clamp01(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}
}
