package gg.carbon.client.screen.menu;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.screen.CarbonBackground;
import gg.carbon.client.screen.widget.Ui;
import gg.carbon.client.setting.KeySetting;
import gg.carbon.client.ui.Carbon;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Fonts.Face;
import gg.carbon.client.ui.MenuImages;
import gg.carbon.client.ui.Pixel;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Anim;
import gg.carbon.client.ui.anim.Easing;
import gg.carbon.client.ui.anim.Smooth;
import net.minecraft.client.gui.GuiGraphics;

/** The original Carbon design: graphene sky, block terrain, extruded wordmark, block buttons. */
public final class CarbonTheme implements MenuTheme {
	private static final int[] BUTTONS = {Entries.SINGLEPLAYER, Entries.MULTIPLAYER, Entries.MODS};
	private static final int[] LINKS = {Entries.REALMS, Entries.OPTIONS, Entries.DESIGN, Entries.LANGUAGE, Entries.ACCESSIBILITY, Entries.QUIT};
	private static final String[] ICONS = Entries.ICONS;

	private final Smooth[] hover = new Smooth[Entries.COUNT];
	private final Smooth[] press = new Smooth[Entries.COUNT];
	private final Anim[] sheen = new Anim[Entries.COUNT];
	private final boolean[] wasHover = new boolean[Entries.COUNT];
	private final HitList hits = new HitList();
	/** The "Carbon Client" design: this layout with the CARBON CLIENT logo and the character render. */
	private final boolean showcase;

	public CarbonTheme() {
		this(false);
	}

	public CarbonTheme(boolean showcase) {
		this.showcase = showcase;
		for (int i = 0; i < Entries.COUNT; i++) {
			hover[i] = new Smooth(0, 14f);
			press[i] = new Smooth(0, 24f);
			sheen[i] = new Anim(1, 700, Easing.SMOOTH);
		}
	}

	@Override
	public String id() {
		return showcase ? "Carbon Client" : "Carbon";
	}

	@Override
	public String title() {
		return showcase ? "Carbon Client" : "Carbon";
	}

	@Override
	public String description() {
		return showcase ? "The CARBON CLIENT logo with your character beside the menu" : "Graphene sky over a drifting block landscape";
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
		CarbonBackground.render(g, w, h, 1f, c.preview() ? -1 : c.mx(), c.preview() ? -1 : c.my());

		float cx = w / 2f, cy = h / 2f - 6;

		// brand
		float tb = c.stage(0.9f, 0.8f);
		R2D.pushAlpha(tb);
		Carbon.mark(g, 26, 22, 14, Ink.accent(), Ink.accent());
		Fonts.draw(g, "Carbon", 39, 17.5f, Ink.strong(), Face.BODY_BOLD, 1f, false);
		R2D.popAlpha();

		if (showcase || MenuImages.logoEnabled()) {
			float lo = c.stage(0.1f, 1.0f);
			R2D.pushAlpha(lo);
			MenuImages.drawCentered(g, MenuImages.LOGO, cx, cy - 122 + (1 - lo) * 12, 100);
			R2D.popAlpha();
		} else {
		float m = c.stage(0.1f, 0.9f);
		R2D.pushAlpha(m);
		float markY = cy - 104 + (1 - m) * 8;
		if (!Ui.light) R2D.shadow(g, cx - 10, markY - 10, 20, 20, 10, 18, 0x22FFFFFF);
		Carbon.mark(g, cx, markY, 26, Ink.accent(), Ink.accent());
		R2D.popAlpha();

		float lo = c.stage(0.25f, 1.0f);
		float size = Math.min(56, w / 9f);
		float lw = Fonts.displayWidth("CARBON", size);
		R2D.pushAlpha(lo);
		Pixel.logo(g, "CARBON", cx - lw / 2f, cy - 84 + (1 - lo) * 12, size, 1f);
		R2D.popAlpha();
		}

		float sp = c.stage(0.5f, 0.8f);
		R2D.pushAlpha(sp);
		spec(g, cx, cy - 6 + (1 - sp) * 6);
		R2D.popAlpha();

		// buttons
		float bw = 118, bh = 27, gap = 10;
		float total = bw * BUTTONS.length + gap * (BUTTONS.length - 1);
		float bx = cx - total / 2f, by = cy + 16;
		if (showcase || MenuImages.skinEnabled()) skin(c, bx, cy);
		for (int i = 0; i < BUTTONS.length; i++) {
			int e = BUTTONS[i];
			MenuEntry entry = c.entries().get(e);
			float x = bx + i * (bw + gap);
			hits.add(e, x, by - 3, bw, bh + 3);
			boolean hov = c.interactive() && R2D.hover(c.mx(), c.my(), x, by - 3, bw, bh + 3);
			if (hov && !wasHover[e]) sheen[e].play(0, 1);
			wasHover[e] = hov;
			hover[e].target(hov ? 1 : 0);
			press[e].target(c.pressed() == e ? 1 : 0);
			float t = c.stage(0.65f + i * 0.07f, 0.8f);
			R2D.pushAlpha(t);
			R2D.push(g);
			R2D.translate(g, 0, Math.round((1 - t) * 14));
			Ui.button(g, entry.label(), ICONS[e], x, by, bw, bh, entry.primary(), hover[e].get(), press[e].get(), sheen[e].get());
			R2D.pop(g);
			R2D.popAlpha();
		}

		// links
		float lt = c.stage(0.95f, 0.8f);
		R2D.pushAlpha(lt);
		float linksW = 0;
		for (int e : LINKS) linksW += Fonts.width(c.entries().get(e).label(), Face.MEDIUM, 0.9f) + 24;
		linksW -= 24;
		float lx = cx - linksW / 2f, ly = by + bh + 16;
		for (int i = 0; i < LINKS.length; i++) {
			int e = LINKS[i];
			String label = c.entries().get(e).label();
			float lw2 = Fonts.width(label, Face.MEDIUM, 0.9f);
			hits.add(e, lx - 3, ly - 3, lw2 + 6, 14);
			boolean hov = c.interactive() && R2D.hover(c.mx(), c.my(), lx - 3, ly - 3, lw2 + 6, 14);
			hover[e].target(hov ? 1 : 0);
			float hh = hover[e].get();
			Fonts.draw(g, label, lx, ly, Theme.lerp(Ink.dim(), Ink.strong(), hh), Face.MEDIUM, 0.9f, false);
			if (hh > 0.01f) R2D.rect(g, lx, ly + 10.5f, lw2 * Easing.SMOOTH.apply(hh), 1, Ink.strong());
			lx += lw2 + 24;
			if (i < LINKS.length - 1) Carbon.hexagon(g, lx - 12, ly + 4, 1.6f, 0, Ink.faint());
		}
		R2D.popAlpha();

		keyBar(c);
		footer(c);
	}

	private void spec(GuiGraphics g, float cx, float y) {
		String[] parts = {"MINECRAFT " + net.minecraft.SharedConstants.getCurrentVersion().name(), "FABRIC", CarbonClient.modules().all().size() + " MODULES", "V" + CarbonClient.VERSION};
		float total = 0;
		for (String p : parts) total += tracked(p) + 22;
		total -= 22;
		float x = cx - total / 2f;
		for (int i = 0; i < parts.length; i++) {
			drawTracked(g, parts[i], x, y, Ink.dim());
			x += tracked(parts[i]) + 22;
			if (i < parts.length - 1) Carbon.hexagon(g, x - 11, y + 3.4f, 1.6f, 0, Ink.faint());
		}
	}

	static float tracked(String s) {
		float w = 0;
		for (char ch : s.toCharArray()) w += Fonts.width(String.valueOf(ch), Face.MEDIUM, 0.78f) + 1.1f;
		return w - 1.1f;
	}

	static void drawTracked(GuiGraphics g, String s, float x, float y, int color) {
		for (char ch : s.toCharArray()) {
			String str = String.valueOf(ch);
			Fonts.draw(g, str, x, y, color, Face.MEDIUM, 0.78f, false);
			x += Fonts.width(str, Face.MEDIUM, 0.78f) + 1.1f;
		}
	}

	/** The character artwork, centred in the space left of the button row. */
	private void skin(MenuContext c, float buttonsLeft, float cy) {
		float height = Math.min(c.h() * 0.6f, 240);
		float width = MenuImages.width(MenuImages.SKIN, height);
		if (buttonsLeft < width + 16) return;
		float t = c.stage(0.55f, 1.0f);
		R2D.pushAlpha(t);
		MenuImages.drawCentered(c.g(), MenuImages.SKIN, buttonsLeft / 2f, cy + 92 - height + (1 - t) * 14, height);
		R2D.popAlpha();
	}

	private void keyBar(MenuContext c) {
		GuiGraphics g = c.g();
		float t = c.stage(1.15f, 0.9f);
		R2D.pushAlpha(t);
		String k = key(CarbonClient.modules().client.menuKey);
		String label = "Mod menu";
		float total = Math.max(16, Fonts.width(k, Face.MONO_BOLD, 0.72f) + 10) + 6 + Fonts.width(label, Face.MEDIUM, 0.85f);
		float x = c.w() / 2f - total / 2f, y = c.h() - 25.5f;
		float kw = Ui.keycap(g, k, x, y, 0);
		Fonts.draw(g, label, x + kw + 6, y + 2.5f, Ink.dim(), Face.MEDIUM, 0.85f, false);
		R2D.popAlpha();
	}

	static KeySetting firstKey(java.util.List<gg.carbon.client.setting.Setting<?>> settings) {
		for (var s : settings) if (s instanceof KeySetting k) return k;
		return null;
	}

	static String key(KeySetting k) {
		return k == null ? "-" : k.display().toUpperCase();
	}

	private void footer(MenuContext c) {
		GuiGraphics g = c.g();
		float ft = c.stage(1.3f, 0.8f);
		R2D.pushAlpha(ft);
		float h = c.h();
		R2D.rrect(g, 17, h - 26, 16, 16, 3, Ink.line());
		MenuArt.face(g, 18, h - 25, 14);
		Fonts.draw(g, c.user(), 39, h - 22.2f, Ink.dim(), Face.MEDIUM, 0.85f, false);
		String version = "carbon " + CarbonClient.VERSION;
		Fonts.draw(g, version, c.w() - 18 - Fonts.width(version, Face.MONO, 0.78f), h - 21.5f, Ink.faint(), Face.MONO, 0.78f, false);
		R2D.popAlpha();
	}
}
