package gg.carbon.client.screen.menu;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.screen.widget.Ui;
import gg.carbon.client.ui.Carbon;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Fonts.Face;
import gg.carbon.client.ui.Iso;
import gg.carbon.client.ui.Iso.Block;
import gg.carbon.client.ui.MenuImages;
import gg.carbon.client.ui.Pixel;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Anim;
import gg.carbon.client.ui.anim.Easing;
import gg.carbon.client.ui.anim.Smooth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * "Voxel": a floating isometric block island as key art (blocks drop in on open, columns rise
 * under the cursor, debris orbits) beside a clean left-aligned menu.
 */
public final class VoxelTheme implements MenuTheme {
	private static final int[] BUTTONS = {Entries.SINGLEPLAYER, Entries.MULTIPLAYER, Entries.MODS, Entries.OPTIONS};
	private static final int[] LINKS = {Entries.REALMS, Entries.DESIGN, Entries.LANGUAGE, Entries.ACCESSIBILITY, Entries.QUIT};
	private static final int N = 11;

	private record Cube(int i, int j, int k, Block block, float delay) {}

	private static final List<Cube> ISLAND = new ArrayList<>();

	static {
		float c = (N - 1) / 2f;
		for (int i = 0; i < N; i++) {
			for (int j = 0; j < N; j++) {
				float d = (float) Math.hypot(i - c, j - c);
				float edge = 5.1f + (float) (Math.sin(i * 1.7 + j * 0.9) * 0.6);
				if (d > edge) continue;
				int height = 1 + (int) Math.round(1.2 * (Math.sin(i * 0.75 + 0.4) + Math.cos(j * 0.62 - 0.3) + 2) / 2 * (1 - d / 7.5));
				float jitter = (float) (Math.abs(Math.sin(i * 12.9898 + j * 78.233)) % 1) * 0.08f;
				for (int k = 0; k < height; k++) {
					Block b = k == height - 1 ? Block.GRASS : ((i * 7 + j * 3 + k) % 5 == 0 ? Block.ORE : Block.STONE);
					ISLAND.add(new Cube(i, j, k, b, 0.15f + (i + j) * 0.03f + k * 0.04f + jitter));
				}
				int hang = (int) Math.round((1 - d / edge) * 4.2);
				for (int k = -1; k >= -hang; k--) {
					Block b = (i + j + k) % 4 == 0 ? Block.ORE : Block.STONE;
					ISLAND.add(new Cube(i, j, k, b, 0.15f + (i + j) * 0.03f + jitter));
				}
			}
		}
		// a tree
		int ti = 4, tj = 6, base = 3;
		for (int k = base; k < base + 3; k++) ISLAND.add(new Cube(ti, tj, k, Block.WOOD, 0.75f + k * 0.05f));
		for (int di = -1; di <= 1; di++) {
			for (int dj = -1; dj <= 1; dj++) {
				ISLAND.add(new Cube(ti + di, tj + dj, base + 3, Block.LEAVES, 0.95f));
				if (Math.abs(di) + Math.abs(dj) < 2) ISLAND.add(new Cube(ti + di, tj + dj, base + 4, Block.LEAVES, 1.0f));
			}
		}
		ISLAND.sort(Comparator.comparingInt((Cube q) -> q.i() + q.j()).thenComparingInt(Cube::k).thenComparingInt(Cube::i));
	}

	private final Smooth[] lift = new Smooth[N * N];
	private final Smooth[] hover = new Smooth[Entries.COUNT];
	private final Smooth[] press = new Smooth[Entries.COUNT];
	private final Anim[] sheen = new Anim[Entries.COUNT];
	private final boolean[] wasHover = new boolean[Entries.COUNT];
	private final HitList hits = new HitList();

	public VoxelTheme() {
		for (int i = 0; i < lift.length; i++) lift[i] = new Smooth(0, 8f);
		for (int i = 0; i < Entries.COUNT; i++) {
			hover[i] = new Smooth(0, 14f);
			press[i] = new Smooth(0, 24f);
			sheen[i] = new Anim(1, 700, Easing.SMOOTH);
		}
	}

	@Override
	public String id() {
		return "Voxel";
	}

	@Override
	public String title() {
		return "Voxel";
	}

	@Override
	public String description() {
		return "A floating block island that reacts to your cursor";
	}

	@Override
	public int entryAt(float mx, float my) {
		return hits.at(mx, my);
	}

	@Override
	public void render(MenuContext c) {
		GuiGraphics g = c.g();
		hits.clear();
		float w = c.w(), h = c.h();
		boolean light = Ui.light;
		double t = System.currentTimeMillis() / 1000.0 % 100000;

		// backdrop
		R2D.gradientV(g, 0, 0, w, h, light ? 0xFFF7F7F9 : 0xFF0F0F12, light ? 0xFFE3E3E8 : 0xFF050506);
		float ix = w * 0.69f, iy = h * 0.47f;
		float r = Math.min(w, h) * 0.42f;
		R2D.shadow(g, ix - r * 0.3f, iy - r * 0.3f, r * 0.6f, r * 0.6f, r * 0.3f, r * 0.9f, light ? 0x80FFFFFF : 0x14FFFFFF);
		Carbon.ink = light ? 0xFF000000 : 0xFFFFFFFF;
		Carbon.lattice(g, 0, 0, w, h, 16, light ? 0.035f : 0.03f, c.mx(), c.my(), 0, 0);
		Carbon.ink = 0xFFFFFFFF;
		if (!light) Pixel.grain(g, 0, 0, w, h, 0.6f);

		island(c, ix, iy, t);
		content(c);
		chrome(c);
	}

	// ------------------------------------------------------------------ island

	private void island(MenuContext c, float ix, float iy, double t) {
		GuiGraphics g = c.g();
		boolean light = Ui.light;
		float islandW = Math.min(c.w() * 0.46f, c.h() * 0.82f);
		float half = islandW / (2 * N);
		int a = Iso.deviceHalf(g, half);
		float s = R2D.scale(g);
		float bob = (float) Math.sin(t * 0.8) * 3.2f;
		// world origin: centre of the grid lands on (ix, iy)
		float c0 = (N - 1) / 2f;
		// island origin in device pixels; the whole-pixel part keeps the grid exact, the bob stays fractional
		int[] origin = R2D.point(g, ix, iy);
		int ox = origin[0], oy = origin[1] - Math.round(c0 * a) - a;
		float bobPx = bob * s;

		// soft contact shadow below the island
		R2D.shadow(g, ix - islandW * 0.26f, iy + islandW * 0.3f, islandW * 0.52f, 6, 3, 22, light ? 0x1C000000 : 0x60000000);

		// cursor lift per column
		long now = System.currentTimeMillis();
		float elapsed = c.preview() ? 99 : (now - c.openedAt()) / 1000f * Ui.speed() + (c.fullIntro() ? 0 : 0.9f);
		if (c.interactive() && c.mx() >= 0) {
			for (int i = 0; i < N; i++) {
				for (int j = 0; j < N; j++) {
					float sx = (ox + (i - j) * a) / (s), sy = (oy + (i + j) * a / 2f) / s;
					int[] m = R2D.point(g, c.mx(), c.my());
					float dx = (m[0] - (ox + (i - j) * a)) / s, dy = (m[1] - (oy + (i + j) * a / 2f - a)) / s;
					float d = (float) Math.hypot(dx, dy * 1.6f) / (half * 5);
					lift[i * N + j].target(d < 1 ? (1 - d) * (1 - d) : 0);
				}
			}
		} else {
			for (Smooth sm : lift) sm.target(0);
		}

		R2D.beginDevice(g);
		for (Cube q : ISLAND) {
			float e = c.preview() ? 1 : Easing.clamp01((elapsed - q.delay()) / 0.6f);
			if (e <= 0) continue;
			float drop = (1 - Easing.SMOOTH.apply(e)) * a * 7;
			int li = Math.max(0, Math.min(N * N - 1, q.i() * N + q.j()));
			float up = lift[li].get() * a * 0.55f;
			int dx = ox + (q.i() - q.j()) * a;
			float dy = oy + (q.i() + q.j()) * a / 2 - q.k() * a - drop - up + bobPx;
			float depth = q.k() < 0 ? 0.78f + 0.05f * (4 + q.k()) : 1f;
			int tint = Theme.lerp(0xFF000000, tint(q.block(), light), depth);
			R2D.pushAlpha(Math.min(1, e * 1.6f));
			Iso.cube(g, q.block(), a, (float) dx, dy, tint);
			R2D.popAlpha();
		}

		// orbiting debris
		float intro = c.preview() ? 1 : Easing.clamp01((elapsed - 1.2f) / 0.8f);
		for (int k = 0; k < 3; k++) {
			double ang = t * 0.35 + k * 2.1;
			float rx = (float) Math.cos(ang) * c0 * a * 1.55f;
			float ry = (float) Math.sin(ang) * c0 * a * 0.55f - a * (2.5f + k) + (float) Math.sin(t * 1.3 + k) * a * 0.3f;
			R2D.pushAlpha(intro * 0.95f);
			Iso.cube(g, k == 1 ? Block.ORE : Block.STONE, a / 2 + (a / 2 & 1), ox + rx, oy + c0 * a + ry + bobPx * 0.6f, tint(Block.STONE, light));
			R2D.popAlpha();
		}

		// rising motes around the island
		for (int k = 0; k < 18; k++) {
			double p = ((t * (0.05 + k % 5 * 0.012)) + k * 0.173) % 1.0;
			float mx = ox + (float) Math.sin(k * 2.39 + t * 0.4) * c0 * a * 1.4f;
			float my = oy + c0 * a * 1.2f - (float) p * a * 14;
			int alpha = (int) (Math.sin(p * Math.PI) * (light ? 90 : 120));
			int size = Math.max(1, a / 6);
			g.fill(Math.round(mx), Math.round(my), Math.round(mx) + size, Math.round(my) + size,
				R2D.c(Theme.withAlpha(light ? 0x2A2A30 : 0xE6E6EA, alpha)));
		}
		R2D.endDevice(g);
	}

	private static int tint(Block b, boolean light) {
		if (light) {
			return switch (b) {
				case GRASS -> 0xFFFFFFFF;
				case STONE -> 0xFFD6D6DC;
				case ORE -> 0xFFCCCCD2;
				case WOOD -> 0xFFAEAEB6;
				case LEAVES -> 0xFFF2F2F4;
			};
		}
		return switch (b) {
			case GRASS -> 0xFFCDCDD3;
			case STONE -> 0xFF8C8C94;
			case ORE -> 0xFF82828A;
			case WOOD -> 0xFF6C6C74;
			case LEAVES -> 0xFFE0E0E4;
		};
	}

	// ------------------------------------------------------------------ menu

	private void content(MenuContext c) {
		GuiGraphics g = c.g();
		float h = c.h();
		float x0 = Math.max(32, c.w() * 0.085f);
		float blockH = 64 + 20 + BUTTONS.length * 36 + 26;
		float y0 = h / 2f - blockH / 2f;

		float t0 = c.stage(0.2f, 0.9f);
		R2D.pushAlpha(t0);
		Ui.eyebrow(g, "Carbon client", x0, y0 + (1 - t0) * 6, Ink.dim());
		R2D.popAlpha();

		float lt = c.stage(0.3f, 1.0f);
		R2D.pushAlpha(lt);
		if (MenuImages.logoEnabled()) MenuImages.draw(g, MenuImages.LOGO, x0 - 1, y0 + 12 + (1 - lt) * 10, 50);
		else Pixel.logo(g, "CARBON", x0 - 1, y0 + 14 + (1 - lt) * 10, 44, 1f);
		R2D.popAlpha();

		float st = c.stage(0.5f, 0.8f);
		R2D.pushAlpha(st);
		String spec = "MINECRAFT " + net.minecraft.SharedConstants.getCurrentVersion().name() + "  ·  " + CarbonClient.modules().all().size() + " MODULES";
		CarbonTheme.drawTracked(g, spec, x0, y0 + 66 + (1 - st) * 6, Ink.dim());
		R2D.popAlpha();

		float by = y0 + 86;
		float bw = 206, bh = 28;
		for (int i = 0; i < BUTTONS.length; i++) {
			int e = BUTTONS[i];
			float y = by + i * 36;
			hits.add(e, x0, y - 3, bw, bh + 3);
			boolean hov = c.interactive() && R2D.hover(c.mx(), c.my(), x0, y - 3, bw, bh + 3);
			if (hov && !wasHover[e]) sheen[e].play(0, 1);
			wasHover[e] = hov;
			hover[e].target(hov ? 1 : 0);
			press[e].target(c.pressed() == e ? 1 : 0);
			float t = c.stage(0.55f + i * 0.07f, 0.8f);
			R2D.pushAlpha(t);
			R2D.push(g);
			R2D.translate(g, Math.round((1 - t) * -16), 0);
			MenuArt.navButton(g, c.entries().get(e).label(), Entries.ICONS[e], x0, y, bw, bh, c.entries().get(e).primary(),
				hover[e].get(), press[e].get(), sheen[e].get());
			R2D.pop(g);
			R2D.popAlpha();
		}

		float lt2 = c.stage(0.95f, 0.8f);
		R2D.pushAlpha(lt2);
		float lx = x0 + 1, ly = by + BUTTONS.length * 36 + 6;
		for (int i = 0; i < LINKS.length; i++) {
			int e = LINKS[i];
			String label = c.entries().get(e).label();
			float lw = Fonts.width(label, Face.MEDIUM, 0.85f);
			hits.add(e, lx - 3, ly - 3, lw + 6, 14);
			boolean hov = c.interactive() && R2D.hover(c.mx(), c.my(), lx - 3, ly - 3, lw + 6, 14);
			hover[e].target(hov ? 1 : 0);
			float hv = hover[e].get();
			Fonts.draw(g, label, lx, ly, Theme.lerp(Ink.dim(), Ink.strong(), hv), Face.MEDIUM, 0.85f, false);
			if (hv > 0.01f) R2D.rect(g, lx, ly + 10, lw * Easing.SMOOTH.apply(hv), 1, Ink.strong());
			lx += lw + 16;
			if (i < LINKS.length - 1) R2D.rect(g, lx - 8.5f, ly + 3.5f, 2, 2, Ink.faint());
		}
		R2D.popAlpha();
	}

	private void chrome(MenuContext c) {
		GuiGraphics g = c.g();
		float w = c.w(), h = c.h();
		float t = c.stage(1.1f, 0.8f);
		R2D.pushAlpha(t);
		// top-right profile chip
		float pw = Fonts.width(c.user(), Face.BODY_BOLD, 0.88f) + 42;
		float px = w - 22 - pw, py = 16;
		R2D.rrect(g, px, py, pw, 22, 11, Ink.panel());
		R2D.outline(g, px, py, pw, 22, 11, 0.75f, Ink.panelEdge());
		MenuArt.face(g, px + 5, py + 4, 14);
		Fonts.draw(g, c.user(), px + 25, py + 6.5f, Ink.strong(), Face.BODY_BOLD, 0.88f, false);
		// bottom hints
		String key = CarbonClient.modules().client.menuKey.display().toUpperCase();
		float kw = Ui.keycap(g, key, 32, h - 30, 0);
		Fonts.draw(g, "Mod menu", 32 + kw + 6, h - 27.5f, Ink.dim(), Face.MEDIUM, 0.82f, false);
		String v = "carbon " + CarbonClient.VERSION;
		Fonts.draw(g, v, w - 22 - Fonts.width(v, Face.MONO, 0.75f), h - 26, Ink.faint(), Face.MONO, 0.75f, false);
		R2D.popAlpha();
	}
}
