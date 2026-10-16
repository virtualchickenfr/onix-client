package gg.carbon.client.screen;

import gg.carbon.client.ui.Pixel;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Night sky over a scrolling block landscape: twinkling pixel stars, rising pixel flakes and
 * three parallax layers of terrain whose top blocks catch the light.
 */
public final class CarbonBackground {
	private CarbonBackground() {}

	private record Layer(float block, float speed, float base, float amp, float f1, float f2, float seed, int body, int top) {}

	private static final Layer[] LIGHT_LAYERS = {
		new Layer(7, 6, 0.32f, 0.060f, 0.09f, 0.031f, 1.7f, 0xFFE3E3E7, 0xFFD2D2D8),
		new Layer(9, 12, 0.235f, 0.048f, 0.12f, 0.043f, 4.2f, 0xFFD9D9DE, 0xFFB9B9C1),
		new Layer(12, 22, 0.15f, 0.032f, 0.15f, 0.057f, 8.9f, 0xFFCDCDD3, 0xFF8F8F98)
	};

	private static final Layer[] LAYERS = {
		new Layer(7, 6, 0.32f, 0.060f, 0.09f, 0.031f, 1.7f, 0xFF121215, 0xFF222227),
		new Layer(9, 12, 0.235f, 0.048f, 0.12f, 0.043f, 4.2f, 0xFF0F0F12, 0xFF34343B),
		new Layer(12, 22, 0.15f, 0.032f, 0.15f, 0.057f, 8.9f, 0xFF0B0B0D, 0xFF5C5C65)
	};

	private static final int STARS = 90, FLAKES = 40;
	private static final float[] STX = new float[STARS], STY = new float[STARS], STP = new float[STARS], STS = new float[STARS];
	private static final float[] FX = new float[FLAKES], FY = new float[FLAKES], FS = new float[FLAKES], FV = new float[FLAKES], FZ = new float[FLAKES];
	private static float mouseX = -1, mouseY = -1;
	private static float pmx, pmy;
	private static long last = System.nanoTime();
	private static final long START = System.currentTimeMillis() - 30_000L;

	static {
		Random r = new Random(42);
		for (int i = 0; i < STARS; i++) {
			STX[i] = r.nextFloat();
			STY[i] = r.nextFloat() * 0.62f;
			STP[i] = r.nextFloat() * 6.28f;
			STS[i] = r.nextFloat() < 0.2f ? 1.5f : 1f;
		}
		for (int i = 0; i < FLAKES; i++) {
			FX[i] = r.nextFloat();
			FY[i] = r.nextFloat();
			FS[i] = 1.5f + r.nextInt(3);
			FV[i] = 0.012f + r.nextFloat() * 0.025f;
			FZ[i] = 0.3f + r.nextFloat() * 0.7f;
		}
	}

	public static void mouse(float x, float y) {
		mouseX = x;
		mouseY = y;
	}

	private static float height(Layer l, int col, float h) {
		double v = Math.sin(col * l.f1() + l.seed()) * 0.6 + Math.sin(col * l.f2() + l.seed() * 2) * 0.4;
		return Math.round(v * l.amp() * h / l.block()) * l.block();
	}

	public static void render(GuiGraphics g, float w, float h, float alpha) {
		render(g, w, h, alpha, mouseX, mouseY);
	}

	/** {@code mx < 0} renders a static preview (no parallax, no cursor light, no shared state touched). */
	public static void render(GuiGraphics g, float w, float h, float alpha, float mouseX, float mouseY) {
		boolean preview = mouseX < 0;
		float pmx = 0, pmy = 0;
		if (!preview) {
			long nowNs = System.nanoTime();
			float dt = Math.min((nowNs - last) / 1e9f, 0.1f);
			last = nowNs;
			float tmx = mouseX / w - 0.5f, tmy = mouseY / h - 0.5f;
			float k = 1 - (float) Math.exp(-3 * dt);
			CarbonBackground.pmx += (tmx - CarbonBackground.pmx) * k;
			CarbonBackground.pmy += (tmy - CarbonBackground.pmy) * k;
			pmx = CarbonBackground.pmx;
			pmy = CarbonBackground.pmy;
		}
		double t = (System.currentTimeMillis() - START) / 1000.0;
		boolean light = gg.carbon.client.screen.widget.Ui.light;

		R2D.pushAlpha(alpha);
		R2D.gradientV(g, 0, 0, w, h, light ? 0xFFF7F7F9 : 0xFF0D0D10, light ? 0xFFE8E8EC : 0xFF070708);
		// moon-ish light
		float r1 = Math.max(w, h) * 0.30f;
		if (!light) R2D.shadow(g, w * 0.5f - r1 * 0.2f, h * 0.18f - r1 * 0.2f, r1 * 0.4f, r1 * 0.4f, r1 * 0.2f, r1 * 0.8f, 0x10FFFFFF);

		// stars
		for (int i = 0; i < STARS; i++) {
			float tw = (float) Math.pow(Math.sin(t * 1.2 + STP[i]), 2);
			int a = Math.round(255 * (0.18f + 0.30f * tw));
			Pixel.block(g, STX[i] * w - pmx * 6, STY[i] * h - pmy * 4, STS[i], light ? Theme.withAlpha(0x2A2A30, Math.round(a * 0.55f)) : Theme.withAlpha(0xE8ECF5, a));
		}

		// graphene lattice, brightening around the cursor in stepped rings
		gg.carbon.client.ui.Carbon.ink = light ? 0xFF000000 : 0xFFFFFFFF;
		gg.carbon.client.ui.Carbon.lattice(g, 0, 0, w, h * 0.78f, 12, light ? 0.07f : 0.06f, mouseX, mouseY, mouseX < 0 ? 0 : 130, 0.55f);
		gg.carbon.client.ui.Carbon.ink = 0xFFFFFFFF;
		R2D.gradientV(g, 0, h * 0.45f, w, h * 0.33f, light ? 0x00E9E9ED : 0x000A0A0C, light ? 0xFFE9E9ED : 0xFF0A0A0C);

		// rising flakes
		for (int i = 0; i < FLAKES; i++) {
			float y = (float) ((FY[i] - t * FV[i] * FZ[i]) % 1.0);
			if (y < 0) y += 1;
			float x = FX[i] + (float) Math.sin(t * 0.6 + i * 0.4) * 0.004f;
			int a = Math.round(255 * FZ[i] * 0.35f * Math.min(1, Math.min(y * 4, (1 - y) * 4)));
			Pixel.block(g, x * w - pmx * 30 * FZ[i], y * h - pmy * 20 * FZ[i], FS[i] * FZ[i], Theme.withAlpha(light ? 0x55555E : 0xDCDCE4, a));
		}

		// terrain
		for (int li = 0; li < LAYERS.length; li++) {
			Layer l = (light ? LIGHT_LAYERS : LAYERS)[li];
			float b = l.block();
			float off = (float) (t * l.speed()) + pmx * 20 * (li + 1);
			int start = (int) Math.floor(off / b);
			float baseY = h - l.base() * h;
			float cap = Math.max(1f, b / 5f);
			for (int col = start - 1; col < start + w / b + 2; col++) {
				// neighbouring columns share their edge coordinate exactly, so they always meet seamlessly
				float x = col * b - off, x1 = (col + 1) * b - off;
				float top = baseY - height(l, col, h);
				R2D.rectEdges(g, x, top, x1, h, l.body());
				R2D.rectEdges(g, x, top, x1, top + cap, l.top());

			}
		}

		if (light) {
			R2D.gradientV(g, 0, 0, w, h * 0.22f, 0x80FFFFFF, 0x00FFFFFF);
			R2D.gradientV(g, 0, h * 0.82f, w, h * 0.18f, 0x00C8C8CE, 0x60C8C8CE);
		} else {
			Pixel.grain(g, 0, 0, w, h, 0.7f);
			R2D.gradientV(g, 0, 0, w, h * 0.22f, 0x90000000, 0x00000000);
			R2D.gradientV(g, 0, h * 0.82f, w, h * 0.18f, 0x00000000, 0x90000000);
		}
		R2D.popAlpha();
	}
}
