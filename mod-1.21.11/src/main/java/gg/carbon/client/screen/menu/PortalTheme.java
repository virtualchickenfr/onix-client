package gg.carbon.client.screen.menu;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import gg.carbon.client.CarbonClient;
import gg.carbon.client.screen.widget.Ui;
import gg.carbon.client.ui.Carbon;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Fonts.Face;
import gg.carbon.client.ui.MenuImages;
import gg.carbon.client.ui.Pixel;
import gg.carbon.client.ui.PixelIcons;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Anim;
import gg.carbon.client.ui.anim.Easing;
import gg.carbon.client.ui.anim.Smooth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * "Portal": an obsidian frame is built block by block, then ignites into a swirling portal that
 * sheds particles. The menu is a hotbar — nine slots, picked by hover, scroll wheel or 1–9.
 */
public final class PortalTheme implements MenuTheme {
	/** Hotbar order: entry index per slot. */
	private static final int[] SLOTS = {Entries.SINGLEPLAYER, Entries.MULTIPLAYER, Entries.REALMS, Entries.MODS, Entries.OPTIONS,
		Entries.DESIGN, Entries.LANGUAGE, Entries.ACCESSIBILITY, Entries.QUIT};
	private static final String[] HINTS = new String[Entries.COUNT];
	private static final int FRAMES = 32;
	/** Portal surface size in texels: the 2 x 3 block opening. */
	private static final int SW = 32, SH = 48;
	private static final float IGNITE = 1.55f;

	static {
		HINTS[Entries.SINGLEPLAYER] = "Play your worlds";
		HINTS[Entries.MULTIPLAYER] = "Join a server";
		HINTS[Entries.REALMS] = "Minecraft Realms";
		HINTS[Entries.MODS] = "Carbon mod menu";
		HINTS[Entries.OPTIONS] = "Game settings";
		HINTS[Entries.LANGUAGE] = "Choose a language";
		HINTS[Entries.ACCESSIBILITY] = "Accessibility settings";
		HINTS[Entries.QUIT] = "Close the game";
		HINTS[Entries.DESIGN] = "Background switcher";
	}

	/** Frame cells (column, row) of the 4 x 5 obsidian frame in build order. */
	private static final int[][] FRAME = {
		{0, 4}, {1, 4}, {2, 4}, {3, 4},
		{0, 3}, {3, 3}, {0, 2}, {3, 2}, {0, 1}, {3, 1},
		{0, 0}, {1, 0}, {2, 0}, {3, 0}
	};

	private static final Identifier OBSIDIAN = Identifier.fromNamespaceAndPath("carbon", "dynamic/portal_obsidian");
	private static final Identifier SWIRL_DARK = Identifier.fromNamespaceAndPath("carbon", "dynamic/portal_swirl_dark");
	private static final Identifier SWIRL_LIGHT = Identifier.fromNamespaceAndPath("carbon", "dynamic/portal_swirl_light");
	private static boolean texturesReady;

	private final HitList hits = new HitList();
	private int selected;
	private final Smooth selX = new Smooth(-1, 20f);
	private final Smooth[] slotHover = new Smooth[SLOTS.length];
	private final Anim nameIn = new Anim(1, 360, Easing.SMOOTH);
	private final Anim pop = new Anim(1, 420, Easing.OUT_BACK);
	private final Smooth near = new Smooth(0, 4f);
	private final Smooth press = new Smooth(0, 18f);
	private int lastHovered = -1;

	public PortalTheme() {
		for (int i = 0; i < slotHover.length; i++) slotHover[i] = new Smooth(0, 16f);
	}

	@Override
	public String id() {
		return "Portal";
	}

	@Override
	public String title() {
		return "Portal";
	}

	@Override
	public String description() {
		return "Light the portal, pick a slot from your hotbar";
	}

	@Override
	public int entryAt(float mx, float my) {
		return hits.at(mx, my);
	}

	@Override
	public int keyPressed(int key) {
		if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
			select(key - InputConstants.KEY_1);
			return -1;
		}
		if (key == InputConstants.KEY_LEFT) select(Math.floorMod(selected - 1, SLOTS.length));
		if (key == InputConstants.KEY_RIGHT) select(Math.floorMod(selected + 1, SLOTS.length));
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) return SLOTS[selected];
		return -1;
	}

	@Override
	public boolean mouseScrolled(double amount) {
		if (amount == 0) return false;
		select(Math.floorMod(selected + (amount > 0 ? -1 : 1), SLOTS.length));
		return true;
	}

	private void select(int slot) {
		if (slot == selected) return;
		selected = slot;
		nameIn.play(0, 1);
		pop.play(0, 1);
	}

	// ------------------------------------------------------------------ textures

	private static float hash(int x, int y, int seed) {
		int h = x * 374761393 + y * 668265263 + seed * 1442695041;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
	}

	private static void textures() {
		if (texturesReady) return;
		texturesReady = true;
		var tm = Minecraft.getInstance().getTextureManager();

		// four obsidian variants side by side: a calm glassy-black base, short diagonal glints, and a
		// one-texel bevel so every block of the frame reads on its own
		NativeImage obs = new NativeImage(64, 16, true);
		for (int v = 0; v < 4; v++) {
			float[][] val = new float[16][16];
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					float n = (hash(x / 4, y / 4, v + 20) * 0.6f + hash(x / 2, y / 2, v + 40) * 0.4f);
					val[y][x] = 0.12f + n * 0.035f + (hash(x, y, v + 60) - 0.5f) * 0.015f;
				}
			}
			for (int k = 0; k < 4; k++) {
				int sx = 2 + (int) (hash(k, v, 81) * 11), sy = 3 + (int) (hash(k, v, 82) * 11);
				int len = 2 + (int) (hash(k, v, 83) * 3);
				for (int i = 0; i < len; i++) {
					int x = sx + i, y = sy - i;
					if (x < 1 || x > 14 || y < 1 || y > 14) break;
					val[y][x] = i == 0 ? 0.38f : 0.29f - i * 0.02f;
					if (y + 1 <= 14) val[y + 1][x] = Math.max(val[y + 1][x], 0.18f);
				}
			}
			for (int i = 0; i < 16; i++) {
				val[0][i] += 0.07f;
				val[i][0] += 0.035f;
				val[15][i] -= 0.045f;
				val[i][15] -= 0.03f;
			}
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 16; x++) {
					int g = Math.round(Math.max(0, Math.min(1, val[y][x])) * 255);
					obs.setPixel(v * 16 + x, y, 0xFF000000 | (g << 16) | (g << 8) | Math.min(255, g + 5));
				}
			}
		}
		tm.register(OBSIDIAN, new DynamicTexture(() -> "carbon obsidian", obs));
		tm.register(SWIRL_DARK, new DynamicTexture(() -> "carbon swirl", swirl(false)));
		tm.register(SWIRL_LIGHT, new DynamicTexture(() -> "carbon swirl light", swirl(true)));
	}

	/**
	 * Portal surface, one field across the whole 2 x 3 block opening (no visible tiling): rotating
	 * spiral arms around the centre, a finer counter-rotating swirl and a slow warp. Every time term
	 * has a whole-number frequency, so the {@link #FRAMES}-frame loop closes seamlessly.
	 */
	private static NativeImage swirl(boolean light) {
		NativeImage img = new NativeImage(SW, SH * FRAMES, true);
		double tau = Math.PI * 2;
		for (int f = 0; f < FRAMES; f++) {
			double t = f * tau / FRAMES;
			for (int y = 0; y < SH; y++) {
				for (int x = 0; x < SW; x++) {
					double nx = (x + 0.5) / SW * 2 - 1, ny = ((y + 0.5) / SH * 2 - 1) * 1.5;
					double r = Math.sqrt(nx * nx + ny * ny);
					double th = Math.atan2(ny, nx);
					double warp = Math.sin(nx * 4 + t) * Math.sin(ny * 3 - t);
					double arms = Math.sin(3 * th + r * 7.5 - t + warp * 0.8);
					double fine = Math.sin(-2 * th + r * 11 + 2 * t);
					double i = (0.62 * arms + 0.26 * fine + 0.12 * warp + 1) / 2;
					i = Math.pow(Math.max(0, Math.min(1, i)), 1.6);
					i += 0.28 * Math.pow(Math.max(0, 1 - r * 0.8), 2);
					double edge = Math.min(Math.min(x + 0.5, SW - x - 0.5) / SW, Math.min(y + 0.5, SH - y - 0.5) / SH);
					i *= 0.5 + 0.5 * Math.min(1, edge / 0.16);
					float sparkle = hash(x, y + f * SH, 77) > 0.992f ? 0.2f : 0;
					float val = light ? (float) (0.4 + 0.58 * i) + sparkle * 0.4f : (float) (0.05 + 0.9 * i) + sparkle;
					int g = Math.round(Math.max(0, Math.min(1, val)) * 255);
					img.setPixel(x, f * SH + y, 0xFF000000 | (g << 16) | (g << 8) | Math.min(255, g + (light ? 2 : 6)));
				}
			}
		}
		return img;
	}
	// ------------------------------------------------------------------ render

	@Override
	public void render(MenuContext c) {
		GuiGraphics g = c.g();
		hits.clear();
		textures();
		float w = c.w(), h = c.h();
		boolean light = Ui.light;
		double t = System.currentTimeMillis() / 1000.0 % 100000;
		float elapsed = c.preview() ? 99 : (System.currentTimeMillis() - c.openedAt()) / 1000f * Ui.speed() + (c.fullIntro() ? 0 : 0.95f);

		// backdrop
		R2D.gradientV(g, 0, 0, w, h, light ? 0xFFF7F7F9 : 0xFF0E0E12, light ? 0xFFE3E3E9 : 0xFF050506);
		Carbon.ink = light ? 0xFF000000 : 0xFFFFFFFF;
		Carbon.lattice(g, 0, 0, w, h, 16, light ? 0.03f : 0.025f, c.mx(), c.my(), 0, 0);
		Carbon.ink = 0xFFFFFFFF;
		if (!light) Pixel.grain(g, 0, 0, w, h, 0.6f);

		// layout
		float logoY = Math.max(18, h * 0.065f);
		float hotbarY = h - 50;
		float top = logoY + 58, bottom = hotbarY - 46;
		float block = Math.max(16, Math.min(40, (bottom - top) / 5.3f));
		float cx = w / 2f, cy = (top + bottom) / 2f;

		portal(c, cx, cy, block, t, elapsed);
		header(c, cx, logoY);
		hotbar(c, cx, hotbarY);
		chrome(c);
	}

	private void portal(MenuContext c, float cx, float cy, float block, double t, float elapsed) {
		GuiGraphics g = c.g();
		boolean light = Ui.light;
		float s = R2D.scale(g);
		int texel = Math.max(1, Math.round(block * s / 16f));
		int bp = texel * 16;
		float bl = bp / s;
		float x0 = cx - 2 * bl, y0 = cy - 2.5f * bl;
		int[] o = R2D.point(g, x0, y0);

		float ignite = c.preview() ? 1 : Easing.clamp01((elapsed - IGNITE) / 0.55f);
		float flash = c.preview() ? 0 : Math.max(0, 1 - (elapsed - IGNITE) / 0.7f) * (elapsed >= IGNITE ? 1 : 0);
		float ix = x0 + bl, iy = y0 + bl, iw = 2 * bl, ih = 3 * bl;

		// cursor proximity brightens the portal; pressing an entry makes it surge
		float prox = 0;
		if (c.interactive() && c.mx() >= 0) {
			float dx = (c.mx() - (ix + iw / 2f)) / (iw * 2.2f), dy = (c.my() - (iy + ih / 2f)) / (ih * 1.6f);
			prox = Easing.clamp01(1 - (float) Math.sqrt(dx * dx + dy * dy));
		}
		near.target(prox);
		press.target(c.pressed() >= 0 ? 1 : 0);
		float energy = ignite * (0.75f + 0.25f * (float) Math.sin(t * 1.6)) + near.get() * 0.35f + press.get() * 0.5f;

		// light cast by the portal
		if (light) {
			R2D.shadow(g, x0 + bl * 0.4f, y0 + 5 * bl - bl * 0.2f, 3.2f * bl, bl * 0.3f, bl * 0.15f, bl * 0.9f, 0x26000000);
		} else {
			R2D.shadow(g, ix + iw * 0.2f, iy + ih * 0.2f, iw * 0.6f, ih * 0.6f, iw * 0.3f, bl * 2.6f, Theme.alpha(0x34FFFFFF, Math.min(1.4f, energy + flash)));
			R2D.shadow(g, x0 + bl * 0.4f, y0 + 5 * bl + bl * 0.15f, 3.2f * bl, bl * 0.25f, bl * 0.12f, bl * 0.8f, 0x90000000);
		}

		R2D.beginDevice(g);
		// portal surface: two neighbouring animation frames cross-faded, so the swirl flows instead of ticking
		if (ignite > 0) {
			Identifier swirl = light ? SWIRL_LIGHT : SWIRL_DARK;
			double ft = t * 12 % FRAMES;
			int f0 = (int) ft, f1 = (f0 + 1) % FRAMES;
			float frac = (float) (ft - f0);
			int px0 = o[0] + bp, py0 = o[1] + bp;
			g.blit(RenderPipelines.GUI_TEXTURED, swirl, px0, py0, 0, f0 * SH, 2 * bp, 3 * bp, SW, SH, SW, SH * FRAMES,
				R2D.tint(Theme.alpha(0xFFFFFFFF, ignite)));
			g.blit(RenderPipelines.GUI_TEXTURED, swirl, px0, py0, 0, f1 * SH, 2 * bp, 3 * bp, SW, SH, SW, SH * FRAMES,
				R2D.tint(Theme.alpha(0xFFFFFFFF, ignite * frac)));
			if (flash > 0) g.fill(px0, py0, px0 + 2 * bp, py0 + 3 * bp, R2D.tint(Theme.alpha(light ? 0xFFFFFFFF : 0xFFF4F4F8, flash)));
		}

		// obsidian frame, built block by block
		for (int i = 0; i < FRAME.length; i++) {
			float e = c.preview() ? 1 : Easing.clamp01((elapsed - 0.35f - i * 0.07f) / 0.38f);
			if (e <= 0) continue;
			int col = FRAME[i][0], row = FRAME[i][1];
			int drop = Math.round((1 - Easing.SMOOTH.apply(e)) * -bp * 0.55f);
			int variant = (col * 3 + row * 5) % 4;
			int x = o[0] + col * bp, y = o[1] + row * bp + drop;
			g.blit(RenderPipelines.GUI_TEXTURED, OBSIDIAN, x, y, variant * 16, 0, bp, bp, 16, 16, 64, 16,
				R2D.tint(Theme.alpha(light ? 0xFFE8E8EE : 0xFFFFFFFF, Math.min(1, e * 1.4f))));
		}
		// inner rim catches the portal light
		if (ignite > 0) {
			int rim = R2D.tint(Theme.alpha(light ? 0x30FFFFFF : 0x40FFFFFF, Math.min(1, energy + flash)));
			int px0 = o[0] + bp, py0 = o[1] + bp;
			g.fill(px0, py0, px0 + 2 * bp, py0 + texel, rim);
			g.fill(px0, py0 + texel, px0 + texel, py0 + 3 * bp, rim);
			g.fill(px0 + 2 * bp - texel, py0 + texel, px0 + 2 * bp, py0 + 3 * bp, rim);
		}

		// particles drifting out of the portal
		if (ignite > 0) {
			int pcx = o[0] + 2 * bp, pcy = o[1] + bp * 5 / 2;
			for (int k = 0; k < 46; k++) {
				float period = 2.2f + hash(k, 1, 5) * 2.6f;
				double life = ((t + hash(k, 2, 5) * 40) / period) % 1.0;
				float sx = (hash(k, 3, 5) - 0.5f) * 2 * bp * 0.9f, sy = (hash(k, 4, 5) - 0.5f) * 3 * bp * 0.9f;
				float dir = hash(k, 6, 5) < 0.5f ? -1 : 1;
				float dist = (float) life * bp * (1.4f + hash(k, 7, 5) * 1.8f);
				float px = pcx + sx + dir * dist + (float) Math.sin(t * 2 + k) * texel * 2;
				float py = pcy + sy - (float) life * bp * (0.6f + hash(k, 8, 5)) * 0.8f;
				float a = (float) Math.sin(life * Math.PI) * ignite * (0.5f + 0.5f * hash(k, 9, 5));
				int size = hash(k, 10, 5) > 0.7f ? texel * 2 : texel;
				int col = Theme.alpha(light ? 0xFF3A3A44 : 0xFFE6E6EE, a);
				int ipx = Math.round(px), ipy = Math.round(py);
				g.fill(ipx, ipy, ipx + size, ipy + size, R2D.tint(col));
			}
		}
		R2D.endDevice(g);
	}

	private void header(MenuContext c, float cx, float y) {
		GuiGraphics g = c.g();
		float lt = c.stage(0.15f, 1.0f);
		float size = 30;
		float lw = Fonts.displayWidth("CARBON", size);
		R2D.pushAlpha(lt);
		if (MenuImages.logoEnabled()) MenuImages.drawCentered(g, MenuImages.LOGO, cx, y - 10 + (1 - lt) * -8, 48);
		else Pixel.logo(g, "CARBON", cx - lw / 2f, y + (1 - lt) * -8, size, 1f);
		R2D.popAlpha();
		float st = c.stage(0.45f, 0.8f);
		R2D.pushAlpha(st);
		String spec = "MINECRAFT " + net.minecraft.SharedConstants.getCurrentVersion().name() + "  ·  " + CarbonClient.modules().all().size() + " MODULES";
		CarbonTheme.drawTracked(g, spec, cx - CarbonTheme.tracked(spec) / 2f, y + 40, Ink.dim());
		R2D.popAlpha();
	}

	private void hotbar(MenuContext c, float cx, float y) {
		GuiGraphics g = c.g();
		boolean light = Ui.light;
		float slot = 22, gap = 2, pad = 3;
		float bw = SLOTS.length * slot + (SLOTS.length - 1) * gap + pad * 2, bh = slot + pad * 2;
		float bx = Math.round(cx - bw / 2f);
		float t = c.stage(1.0f, 0.9f);
		R2D.pushAlpha(t);
		R2D.push(g);
		R2D.translate(g, 0, Math.round((1 - t) * 18));

		// hover picks the slot, like moving through the hotbar
		int hovered = -1;
		for (int i = 0; i < SLOTS.length; i++) {
			float sx = bx + pad + i * (slot + gap);
			hits.add(SLOTS[i], sx - 1, y + pad - 1, slot + 2, slot + 2);
			if (c.interactive() && R2D.hover(c.mx(), c.my(), sx - 1, y + pad - 1, slot + 2, slot + 2)) hovered = i;
		}
		if (hovered >= 0 && hovered != lastHovered) select(hovered);
		lastHovered = hovered;

		R2D.shadow(g, bx + 4, y + bh - 4, bw - 8, 8, 4, 12, light ? 0x30000000 : 0xA0000000);
		Ui.block(g, bx, y, bw, bh + 3, 6, 3, light ? 0xFFFFFFFF : 0xF2141417, light ? 0xFFC6C6CD : 0xFF060607, light ? 0x1A000000 : 0x24FFFFFF);

		float target = bx + pad + selected * (slot + gap);
		if (selX.target() < 0) selX.snap(target);
		selX.target(target);
		for (int i = 0; i < SLOTS.length; i++) {
			int e = SLOTS[i];
			float sx = bx + pad + i * (slot + gap), sy = y + pad;
			boolean sel = i == selected;
			slotHover[i].target(sel ? 1 : 0);
			float hv = slotHover[i].get();
			R2D.rrect(g, sx, sy, slot, slot, 3, light ? Theme.lerp(0xFFF0F0F3, 0xFFE6E6EA, hv) : Theme.lerp(0xFF0B0B0D, 0xFF1A1A1E, hv));
			R2D.rect(g, sx + 2, sy + 0.5f, slot - 4, 1, light ? 0x14000000 : 0x70000000);
			float lift = sel ? Math.round((1 - pop.get()) * -3 * R2D.scale(g)) / R2D.scale(g) : 0;
			float down = c.pressed() == e ? 1 : 0;
			int col = Theme.lerp(Ink.dim(), Ink.strong(), hv);
			PixelIcons.draw(g, Entries.ICONS[e], sx + (slot - 11) / 2f, sy + (slot - 11) / 2f + lift + down, 11, col);
			Fonts.draw(g, String.valueOf(i + 1), sx + 2.5f, sy + 2, Theme.alpha(Ink.faint(), 0.8f), Face.MONO, 0.5f, false);
		}
		// selection frame slides between slots
		float fx = selX.get();
		R2D.shadow(g, fx - 1, y + pad - 1, slot + 2, slot + 2, 4, 8, light ? 0x22000000 : 0x40FFFFFF);
		R2D.outline(g, fx - 2, y + pad - 2, slot + 4, slot + 4, 4.5f, 1.5f, Ink.strong());

		// item name, like the hotbar tooltip in game
		int e = SLOTS[selected];
		float nt = nameIn.get();
		R2D.pushAlpha(nt);
		String name = c.entries().get(e).label();
		Fonts.centered(g, name, cx, y - 31 + (1 - nt) * 4, Ink.strong(), Face.BODY_BOLD, 1.1f);
		Fonts.centered(g, HINTS[e], cx, y - 17 + (1 - nt) * 4, Ink.dim(), Face.MEDIUM, 0.8f);
		R2D.popAlpha();

		R2D.pop(g);
		R2D.popAlpha();
	}

	private void chrome(MenuContext c) {
		GuiGraphics g = c.g();
		float w = c.w(), h = c.h();
		float t = c.stage(1.4f, 0.8f);
		R2D.pushAlpha(t);
		// profile chip
		float pw = Fonts.width(c.user(), Face.BODY_BOLD, 0.88f) + 42;
		float px = w - 22 - pw, py = 16;
		R2D.rrect(g, px, py, pw, 22, 11, Ink.panel());
		R2D.outline(g, px, py, pw, 22, 11, 0.75f, Ink.panelEdge());
		MenuArt.face(g, px + 5, py + 4, 14);
		Fonts.draw(g, c.user(), px + 25, py + 6.5f, Ink.strong(), Face.BODY_BOLD, 0.88f, false);
		// brand
		Carbon.mark(g, 30, 27, 14, Ink.accent(), Ink.accent());
		Fonts.draw(g, "Carbon", 43, 22.5f, Ink.strong(), Face.BODY_BOLD, 1f, false);
		// hints
		String key = CarbonClient.modules().client.menuKey.display().toUpperCase();
		float kw = Ui.keycap(g, key, 22, h - 30, 0);
		Fonts.draw(g, "Mod menu", 22 + kw + 6, h - 27.5f, Ink.dim(), Face.MEDIUM, 0.82f, false);
		String v = "carbon " + CarbonClient.VERSION;
		Fonts.draw(g, v, w - 22 - Fonts.width(v, Face.MONO, 0.75f), h - 26, Ink.faint(), Face.MONO, 0.75f, false);
		R2D.popAlpha();
	}
}
