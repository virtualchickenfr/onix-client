package gg.carbon.client.screen;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.Category;
import gg.carbon.client.module.Module;
import gg.carbon.client.screen.widget.ActionRow;
import gg.carbon.client.screen.widget.SettingRow;
import gg.carbon.client.screen.widget.TextField;
import gg.carbon.client.screen.widget.Ui;
import gg.carbon.client.setting.Setting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Pixel;
import gg.carbon.client.ui.PixelIcons;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Anim;
import gg.carbon.client.ui.anim.Easing;
import gg.carbon.client.ui.anim.Smooth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Carbon's mod menu. */
public class ClickGui extends Screen {
	private static final float HEADER = 46;
	private static final float SIDEBAR = 150;
	private static final float NAV_H = 22;
	private static final float CARD_H = 96;
	private static final float GAP = 10;
	private static final String[] CATEGORY_ICONS = {"monitor", "swords", "eye", "wrench", "bolt"};

	/** Remembered between openings. */
	private static int lastNav = 0;

	private final @Nullable Screen parent;
	private final Anim open = new Anim(0, Ui.ms(520), Easing.OUT_EXPO);
	private boolean closing;
	private long openedAt;

	// navigation: 0 = all, 1..n = categories, n+1 = HUD editor (action), n+2 = design page, n+3 = settings page
	private int nav;
	private final Smooth navY = new Smooth(-1, 18f);
	private final Smooth[] navHover = new Smooth[Category.values().length + 4];
	private boolean designPage;

	private final TextField search = new TextField("", "Search modules…", 32, s -> resetGrid());
	private final Smooth searchHover = new Smooth(0, 16f);
	private final Smooth scroll = new Smooth(0, 16f);
	private float scrollTarget, maxScroll;
	private final Map<Module, Card> cards = new HashMap<>();
	private long gridShownAt;

	// detail page
	private @Nullable Module detail;
	private final Anim page = new Anim(0, 420, Easing.OUT_QUINT);
	private final List<SettingRow<?>> rows = new ArrayList<>();
	private final Smooth detailScroll = new Smooth(0, 16f);
	private float detailScrollTarget, detailMax;
	private final Smooth detailToggle = new Smooth(0, 18f);
	private final Smooth backHover = new Smooth(0, 16f);
	private final Smooth resetHover = new Smooth(0, 16f);

	// layout (virtual UI units)
	private float wx, wy, ww, wh;
	private float ui = 1f, vw, vh;

	private static final class Card {
		final Smooth hover = new Smooth(0, 16f);
		final Smooth toggle;
		final Smooth link = new Smooth(0, 16f);
		float x, y, w;

		Card(Module m) {
			toggle = new Smooth(m.enabled() ? 1 : 0, 16f);
		}
	}

	public ClickGui(@Nullable Screen parent) {
		super(Component.literal("Onix"));
		this.parent = parent;
		this.nav = lastNav;
		for (int i = 0; i < navHover.length; i++) navHover[i] = new Smooth(0, 16f);
	}

	@Override
	protected void init() {
		if (openedAt == 0) {
			openedAt = System.currentTimeMillis();
			gridShownAt = openedAt;
			open.play(0, 1);
			if (nav == settingsNav() || nav == designNav()) {
				designPage = nav == designNav();
				openDetail(CarbonClient.modules().client, false);
			}
		}
	}

	private int settingsNav() {
		return Category.values().length + 3;
	}

	private int designNav() {
		return Category.values().length + 2;
	}

	private static boolean lightMode() {
		return CarbonClient.modules().client.menuLight.on();
	}

	/** Jumps straight to the Design page (used by the "Design" entry of the main menu shortcuts). */
	public ClickGui design() {
		nav = designNav();
		return this;
	}

	private int hudNav() {
		return Category.values().length + 1;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ------------------------------------------------------------------ data

	private List<Module> visibleModules() {
		String q = search.text().trim().toLowerCase(Locale.ROOT);
		List<Module> list = new ArrayList<>();
		for (Module m : CarbonClient.modules().all()) {
			if (nav >= 1 && nav <= Category.values().length && m.category() != Category.values()[nav - 1]) continue;
			if (!q.isEmpty() && !m.name().toLowerCase(Locale.ROOT).contains(q) && !m.description().toLowerCase(Locale.ROOT).contains(q)) continue;
			list.add(m);
		}
		return list;
	}

	private void resetGrid() {
		gridShownAt = System.currentTimeMillis();
		scrollTarget = 0;
		if (detail != null && page.target() > 0 && !search.text().isEmpty()) closeDetail();
	}

	private void openDetail(Module m, boolean animate) {
		detail = m;
		rows.clear();
		var client = CarbonClient.modules().client;
		if (m == client && designPage) {
			rows.add(new ActionRow("Background Switcher", "Swipe through every main menu design with live previews", "Open",
				() -> minecraft.setScreen(new ThemePicker(this))));
			for (Setting<?> s : client.designSettings()) rows.add(SettingRow.of(s));
		} else {
			if (m == client) rows.add(new ActionRow("Import Dawn Settings", "Keybinds, game options, servers and resource packs from Dawn", "Import",
				() -> gg.carbon.client.util.DawnImport.run(minecraft).summary()));
			if (m == client) rows.add(new ActionRow("Import Dawn Pack", "Install the mods of a Dawn profile export (.dawnpack)", "Import", () -> {
				gg.carbon.client.util.DawnPackImport.pickAndRun();
				return null;
			}) {
				@Override
				protected String description() {
					return gg.carbon.client.util.DawnPackImport.status();
				}
			});
			for (Setting<?> s : m == client ? client.generalSettings() : m.settings()) rows.add(SettingRow.of(s));
		}
		detailScrollTarget = 0;
		detailScroll.snap(0);
		detailToggle.snap(m.enabled() ? 1 : 0);
		search.setFocused(false);
		if (animate) page.play(page.get(), 1);
		else page.snap(1);
	}

	/** Opens straight into a module's settings page (used by the HUD editor). */
	public void showModule(Module m) {
		if (!m.settings().isEmpty()) openDetail(m, false);
	}

	private void closeDetail() {
		for (SettingRow<?> r : rows) r.blur();
		page.play(page.get(), 0);
		if (nav == settingsNav() || nav == designNav()) nav = lastNav = 0;
		CarbonClient.config().saveSoon();
	}

	private boolean onDetail() {
		return detail != null && page.target() > 0;
	}

	private void close() {
		if (closing) return;
		closing = true;
		search.setFocused(false);
		for (SettingRow<?> r : rows) r.blur();
		open.play(open.get(), 0);
		CarbonClient.config().save();
	}

	@Override
	public void onClose() {
		close();
	}

	// ------------------------------------------------------------------ layout

	/** Sizes the window from physical pixels so it looks the same at every GUI scale. */
	private void updateLayout() {
		float gs = minecraft.getWindow().getGuiScale();
		float physicalW = width * gs, physicalH = height * gs;
		float target = Math.max(1f, Math.min(2f, Math.min(physicalW / 780f, physicalH / 480f)));
		ui = Math.min(1f, target / gs);
		vw = width / ui;
		vh = height / ui;
		ww = Math.min(vw - 32, 700);
		wh = Math.min(vh - 32, 430);
		wx = Math.round((vw - ww) / 2f);
		wy = Math.round((vh - wh) / 2f);
	}

	private float navItemY(int idx) {
		float y = wy + HEADER + 30;
		if (idx <= Category.values().length) return y + idx * (NAV_H + 3);
		y += (Category.values().length + 1) * (NAV_H + 3) + 26;
		return y + (idx - hudNav()) * (NAV_H + 3);
	}

	private float contentX() {
		return wx + SIDEBAR + 22;
	}

	private float contentW() {
		return ww - SIDEBAR - 44;
	}

	private float searchW() {
		return Math.min(230, ww * 0.34f);
	}

	private float searchX() {
		return wx + ww - 16 - searchW() - 52;
	}

	// ------------------------------------------------------------------ render

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
		float o = Easing.clamp01(open.get());
		boolean light = lightMode();
		if (minecraft.level == null) {
			Ui.light = light;
			CarbonBackground.render(g, width, height, 1f);
			Ui.light = false;
		} else if (CarbonClient.modules().client.blur.on()) {
			g.blurBeforeThisStratum();
		}
		R2D.rect(g, 0, 0, width, height, light ? Theme.withAlpha(0xE4E4E9, Math.round(120 * o)) : Theme.withAlpha(0x030304, Math.round(170 * o)));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
		if (closing && open.done()) {
			// a fresh title screen picks up a changed menu design (Carbon <-> Vanilla)
			minecraft.setScreen(parent instanceof net.minecraft.client.gui.screens.TitleScreen ? new net.minecraft.client.gui.screens.TitleScreen() : parent);
			return;
		}
		float o = open.get();
		updateLayout();
		double mx = mouseX / ui, my = mouseY / ui;

		R2D.invert = lightMode();
		try {
			renderWindow(g, mx, my, o);
		} finally {
			R2D.invert = false;
		}
	}

	private void renderWindow(GuiGraphics g, double mx, double my, float o) {
		R2D.pushAlpha(Easing.clamp01(o));
		R2D.push(g);
		g.pose().scale(ui, ui);
		R2D.setUiScale(ui);
		R2D.translate(g, 0, Math.round((1 - o) * 14));

		// window shell
		R2D.shadow(g, wx, wy + 10, ww, wh, 12, 34, 0xD0000000);
		Ui.block(g, wx, wy, ww, wh + 5, 12, 5, 0xFC0C0C0E, Theme.STONE_DARK, 0);
		Pixel.grain(g, wx, wy, ww, wh, 0.8f);

		renderHeader(g, mx, my);
		renderSidebar(g, mx, my);

		float p = page.get();
		g.enableScissor((int) (wx + SIDEBAR), (int) (wy + HEADER), (int) (wx + ww), (int) (wy + wh));
		if (p < 0.999f) {
			R2D.pushAlpha(1 - p);
			R2D.push(g);
			R2D.translate(g, -36 * p, 0);
			renderGrid(g, mx, my, p < 0.05f);
			R2D.pop(g);
			R2D.popAlpha();
		}
		if (p > 0.001f && detail != null) {
			R2D.pushAlpha(p);
			R2D.push(g);
			R2D.translate(g, 36 * (1 - p), 0);
			renderDetail(g, mx, my, p > 0.95f);
			R2D.pop(g);
			R2D.popAlpha();
		}
		g.disableScissor();

		R2D.outline(g, wx, wy, ww, wh + 5, 12, 0.75f, 0x2AFFFFFF);
		R2D.setUiScale(1f);
		R2D.pop(g);
		R2D.popAlpha();
	}

	private void renderHeader(GuiGraphics g, double mx, double my) {
		R2D.rrect(g, wx, wy, ww, HEADER, 12, 0xFF09090B);
		R2D.rect(g, wx, wy + HEADER - 12, ww, 12, 0xFF09090B);

		// scrolling pixel horizon along the header's bottom edge
		float t = (System.currentTimeMillis() % 64000L) / 1000f;
		g.enableScissor((int) wx, (int) wy, (int) (wx + ww), (int) (wy + HEADER));
		Pixel.horizon(g, wx, wy + HEADER - 12, ww, 1.5f, 0.10f, t * 0.6f);
		g.disableScissor();
		R2D.rect(g, wx, wy + HEADER, ww, 1, 0x1AFFFFFF);

		// brand
		float bx = wx + 16, by = wy + (HEADER - 22) / 2f;
		Pixel.logo(g, "CARBON", bx, by + 2, 15, 1f);
		float lw = Fonts.displayWidth("CARBON", 15);
		R2D.rect(g, bx + lw + 10, by + 3, 1, 12, 0x26FFFFFF);
		Fonts.draw(g, "Mod menu", bx + lw + 18, by + 5, Theme.TEXT_DIM, Fonts.Face.MEDIUM, 0.9f, false);

		// search
		float sx = searchX(), sw = searchW(), sy = wy + (HEADER - 22) / 2f;
		boolean sh = R2D.hover(mx, my, sx, sy, sw, 22);
		searchHover.target(sh || search.focused() ? 1 : 0);
		float hv = searchHover.get();
		R2D.rrect(g, sx, sy, sw, 22, 5, Theme.STONE_DARK);
		R2D.rect(g, sx + 3, sy + 1, sw - 6, 1.5f, 0x60000000);
		R2D.outline(g, sx, sy, sw, 22, 5, 0.75f, Theme.lerp(Theme.STONE_EDGE, 0xFF6A6A72, hv));
		PixelIcons.draw(g, "magnifier", sx + 8, sy + 6.5f, 9, Theme.lerp(Theme.TEXT_MUTED, Theme.WHITE, hv));
		search.render(g, sx + 14, sy, sw - 14, 22, mx, my, false);

		// esc chip
		float ex = wx + ww - 16 - 40, ey = sy + 3;
		boolean eh = R2D.hover(mx, my, ex, ey, 40, 16);
		Fonts.draw(g, "close", ex - 4, ey + 4, eh ? Theme.WHITE : Theme.TEXT_MUTED, Fonts.Face.BODY, 0.75f, false);
		Ui.keycap(g, "ESC", ex + 18, ey + 1, eh ? 1 : 0);
	}

	private void renderSidebar(GuiGraphics g, double mx, double my) {
		float top = wy + HEADER + 1;
		R2D.rect(g, wx + SIDEBAR, top + 10, 1, wh - HEADER - 20, 0x12FFFFFF);

		Ui.eyebrow(g, "Modules", wx + 14, top + 12, Theme.TEXT_MUTED);
		Ui.eyebrow(g, "Client", wx + 14, navItemY(hudNav()) - 14, Theme.TEXT_MUTED);

		int active = onDetail() && nav != settingsNav() && nav != designNav() ? -1 : nav;
		if (active >= 0) {
			float target = navItemY(active);
			if (navY.target() < 0) navY.snap(target);
			navY.target(target);
			float y = navY.get();
			Ui.block(g, wx + 10, y, SIDEBAR - 20, NAV_H + 2, 5, 2, 0xFFEDEDF0, 0xFF8C8C94, 0);
		}

		navItem(g, 0, "grid", "All Modules", String.valueOf(CarbonClient.modules().all().size()), mx, my, active == 0);
		for (int i = 0; i < Category.values().length; i++) {
			Category c = Category.values()[i];
			navItem(g, i + 1, CATEGORY_ICONS[i], c.title, String.valueOf(CarbonClient.modules().byCategory(c).size()), mx, my, active == i + 1);
		}
		navItem(g, hudNav(), "move", "Edit HUD", minecraft.level == null ? "in-game" : "", mx, my, false);
		navItem(g, designNav(), "sparkle", "Design", "", mx, my, active == designNav());
		navItem(g, settingsNav(), "gear", "Settings", "", mx, my, active == settingsNav());

		// profile
		float py = wy + wh - 42;
		R2D.rrect(g, wx + 10, py, SIDEBAR - 20, 32, 5, 0xFF101012);
		R2D.outline(g, wx + 10, py, SIDEBAR - 20, 32, 5, 0.75f, 0x14FFFFFF);
		String name = minecraft.getUser().getName();
		R2D.rrect(g, wx + 17, py + 7, 18, 18, 3, 0xFF202024);
		Fonts.centered(g, name.substring(0, 1).toUpperCase(Locale.ROOT), wx + 26, py + 11.5f, Theme.WHITE, Fonts.Face.BODY_BOLD, 0.95f);
		Fonts.draw(g, Fonts.trim(name, SIDEBAR - 64, true), wx + 41, py + 8, Theme.TEXT, true, 0.82f, false);
		R2D.rect(g, wx + 41, py + 20.5f, 3, 3, 0xFFB8B8BF);
		Fonts.draw(g, "online", wx + 47, py + 19, Theme.TEXT_MUTED, false, 0.68f, false);
	}

	private void navItem(GuiGraphics g, int idx, String icon, String label, String count, double mx, double my, boolean selected) {
		float y = navItemY(idx);
		boolean disabled = idx == hudNav() && minecraft.level == null;
		boolean hovered = !disabled && R2D.hover(mx, my, wx + 10, y, SIDEBAR - 20, NAV_H);
		navHover[idx].target(hovered && !selected ? 1 : 0);
		float h = navHover[idx].get();
		if (h > 0.01f) R2D.rrect(g, wx + 10, y, SIDEBAR - 20, NAV_H, 5, Theme.alpha(0x10FFFFFF, h));
		int col = selected ? 0xFF0A0A0C : disabled ? Theme.TEXT_MUTED : Theme.lerp(Theme.TEXT_DIM, Theme.WHITE, h);
		PixelIcons.draw(g, icon, wx + 19 + h * 1.5f, y + 6.5f, 9, col);
		Fonts.draw(g, label, wx + 35 + h * 1.5f, y + 7, col, selected ? Fonts.Face.BODY_BOLD : Fonts.Face.BODY, 0.9f, false);
		if (!count.isEmpty()) {
			float cw = Fonts.width(count, false, 0.72f);
			Fonts.draw(g, count, wx + SIDEBAR - 20 - cw, y + 8, selected ? 0x990A0A0C : Theme.TEXT_MUTED, false, 0.72f, false);
		} else if (idx == hudNav()) {
			PixelIcons.draw(g, "arrow", wx + SIDEBAR - 30 + h * 2, y + 6.5f, 9, Theme.lerp(Theme.TEXT_MUTED, Theme.WHITE, h));
		}
	}

	private void renderGrid(GuiGraphics g, double mx, double my, boolean interactive) {
		float x0 = contentX(), y0 = wy + HEADER + 18;
		float cw = contentW();
		List<Module> mods = visibleModules();

		boolean filtered = !search.text().isBlank();
		Category cat = nav >= 1 && nav <= Category.values().length && !filtered ? Category.values()[nav - 1] : null;
		long on = mods.stream().filter(Module::enabled).count();
		Ui.eyebrow(g, filtered ? "Search" : mods.size() + " modules  ·  " + on + " on", x0, y0 - 2, 0xFFB4B4BC);
		String title = filtered ? "Results" : cat == null ? "All modules" : cat.title;
		Pixel.headline(g, title, x0, y0 + 10, Theme.WHITE, 2f, true);
		String sub = filtered ? "for \"" + search.text() + "\"" : cat == null ? "everything Onix ships with" : cat.subtitle.toLowerCase(Locale.ROOT);
		Fonts.draw(g, sub, x0 + Pixel.width(title, 2f) + 9, y0 + 17, Theme.TEXT_MUTED, false, 0.82f, false);
		float top = y0 + 34, bottom = wy + wh - 6;
		int cols = Math.max(2, (int) ((cw + GAP) / (160 + GAP)));
		float cardW = (cw - (cols - 1) * GAP) / cols;
		int rowsCount = (mods.size() + cols - 1) / cols;
		float contentH = rowsCount * (CARD_H + GAP) - GAP;
		maxScroll = Math.max(0, contentH - (bottom - top) + 6);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		scroll.target(scrollTarget);
		float sc = scroll.get();

		g.enableScissor((int) (wx + SIDEBAR + 1), (int) top - 3, (int) (wx + ww), (int) bottom);
		long now = System.currentTimeMillis();
		for (int i = 0; i < mods.size(); i++) {
			Module m = mods.get(i);
			Card c = cards.computeIfAbsent(m, Card::new);
			c.x = x0 + (i % cols) * (cardW + GAP);
			c.y = top + (i / cols) * (CARD_H + GAP) - sc;
			c.w = cardW;
			if (c.y > bottom || c.y + CARD_H < top - 3) continue;
			float delay = Ui.ms(Math.min(i, 18) * 24L);
			float t = Easing.OUT_QUART.apply(Easing.clamp01((now - gridShownAt - delay) / (float) Ui.ms(420)));
			R2D.pushAlpha(t);
			R2D.push(g);
			R2D.translate(g, 0, (1 - t) * 10);
			renderCard(g, m, c, mx, my, interactive && my >= top && my <= bottom);
			R2D.pop(g);
			R2D.popAlpha();
		}
		if (mods.isEmpty()) {
			PixelIcons.draw(g, "magnifier", x0 + cw / 2f - 9, top + 30, 18, Theme.TEXT_MUTED);
			Fonts.centered(g, "Nothing matches \"" + search.text() + "\"", x0 + cw / 2f, top + 56, Theme.TEXT_DIM, true, 0.9f);
		}
		g.disableScissor();

		if (sc > 1) R2D.gradientV(g, wx + SIDEBAR + 1, top - 3, ww - SIDEBAR - 2, 12, 0xFF0B0B0D, 0x000B0B0D);
		if (maxScroll > 0) {
			float trackH = bottom - top;
			float barH = Math.max(20, trackH * (trackH / (contentH + 6)));
			float barY = top + (trackH - barH) * (sc / maxScroll);
			R2D.rrect(g, wx + ww - 8, barY, 2, barH, 1, 0x40FFFFFF);
		}
	}

	private float linkWidth() {
		return Fonts.width("Configure", true, 0.78f) + 12;
	}

	private void renderCard(GuiGraphics g, Module m, Card c, double mx, double my, boolean interactive) {
		boolean hovered = interactive && R2D.hover(mx, my, c.x, c.y, c.w, CARD_H);
		c.hover.target(hovered ? 1 : 0);
		c.toggle.target(m.enabled() ? 1 : 0);
		float h = c.hover.get(), t = c.toggle.get();
		float lift = -2f * Easing.SMOOTH.apply(h);

		R2D.push(g);
		R2D.translate(g, 0, lift);
		Ui.card(g, c.x, c.y, c.w, CARD_H, h, t, mx, my - lift);

		Ui.slot(g, PixelIcons.forModule(m.id()), c.x + 12, c.y + 12, 28, t, h);
		Ui.toggle(g, c.x + c.w - 12 - Ui.toggleWidth(), c.y + 14, t, h);

		Fonts.draw(g, Fonts.trim(m.name(), c.w - 24, true), c.x + 12, c.y + 48, Theme.WHITE, Fonts.Face.BODY_BOLD, 1.1f, false);
		List<String> lines = wrap(m.description(), c.w - 24, 0.78f, 2);
		for (int i = 0; i < lines.size(); i++) {
			Fonts.draw(g, lines.get(i), c.x + 12, c.y + 62 + i * 9.5f, Theme.TEXT_DIM, false, 0.78f, false);
		}

		if (!m.settings().isEmpty()) {
			float ly = c.y + CARD_H - 4 - 13;
			boolean lh = interactive && R2D.hover(mx, my - lift, c.x + c.w - 70, ly - 2, 62, 14);
			c.link.target(lh ? 1 : 0);
			float l = c.link.get();
			int col = Theme.lerp(Theme.TEXT_MUTED, Theme.WHITE, Math.max(l, h * 0.5f));
			float lw = linkWidth();
			float lx = c.x + c.w - 12 - lw;
			Fonts.draw(g, "Configure", lx, ly + 1, col, true, 0.75f, false);
			PixelIcons.draw(g, "arrow", lx + lw - 9 + l * 2.5f, ly, 9, col);
		}
		R2D.pop(g);
	}
	private static List<String> wrap(String text, float width, float scale, int maxLines) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String test = line.isEmpty() ? word : line + " " + word;
			if (Fonts.width(test, false, scale) > width && !line.isEmpty()) {
				out.add(line.toString());
				line = new StringBuilder(word);
				if (out.size() == maxLines) break;
			} else {
				line = new StringBuilder(test);
			}
		}
		if (out.size() < maxLines && !line.isEmpty()) out.add(line.toString());
		return out;
	}

	private void renderDetail(GuiGraphics g, double mx, double my, boolean interactive) {
		Module m = detail;
		float x0 = contentX(), y0 = wy + HEADER + 14;
		float cw = contentW();
		boolean isClient = m == CarbonClient.modules().client;

		boolean bh = interactive && R2D.hover(mx, my, x0 - 4, y0 - 3, 60, 16);
		backHover.target(bh ? 1 : 0);
		float b = backHover.get();
		int bc = Theme.lerp(Theme.TEXT_MUTED, Theme.WHITE, b);
		PixelIcons.draw(g, "arrowleft", x0 - b * 2, y0, 9, bc);
		Fonts.draw(g, isClient ? "Back" : "Back to " + (nav >= 1 && nav <= Category.values().length ? Category.values()[nav - 1].title : "modules"),
			x0 + 13, y0 + 1, bc, true, 0.78f, false);

		float ty = y0 + 18;
		Ui.iconTile(g, isClient ? (designPage ? "sparkle" : "gear") : PixelIcons.forModule(m.id()), x0, ty, 32, isClient ? 0 : detailToggle.get(), 0);
		Pixel.headline(g, isClient ? (designPage ? "Design" : "Settings") : m.name(), x0 + 42, ty + 2, Theme.WHITE, 2f, true);
		Fonts.draw(g, isClient ? (designPage ? "Menu designs, light mode and backgrounds" : "Onix client preferences") : m.description(),
			x0 + 42, ty + 21, Theme.TEXT_DIM, false, 0.8f, false);

		float rx = x0 + cw;
		if (!isClient) {
			detailToggle.target(m.enabled() ? 1 : 0);
			R2D.push(g);
			R2D.scaleAt(g, rx - 14, ty + 14, 1.3f);
			Ui.toggle(g, rx - 25, ty + 8, detailToggle.get(), 0);
			R2D.pop(g);
			rx -= 48;
		}
		String reset = "Reset";
		float rw = Fonts.width(reset, true, 0.78f) + 18;
		boolean rh = interactive && R2D.hover(mx, my, rx - rw, ty + 7, rw, 16);
		resetHover.target(rh ? 1 : 0);
		float r = resetHover.get();
		R2D.rrect(g, rx - rw, ty + 7, rw, 16, 3, Theme.lerp(0xFF151518, 0xFF222226, r));
		R2D.outline(g, rx - rw, ty + 7, rw, 16, 3, 0.75f, Theme.alpha(0xFFFFFFFF, 0.1f + 0.2f * r));
		Fonts.centered(g, reset, rx - rw / 2f, ty + 11.5f, Theme.lerp(Theme.TEXT_DIM, Theme.WHITE, r), true, 0.78f);

		float top = ty + 44, bottom = wy + wh - 10;
		Pixel.horizon(g, x0, top - 14, cw, 1f, 0.06f, 0);
		R2D.rect(g, x0, top - 6, cw, 1, 0x12FFFFFF);
		float total = 0;
		for (SettingRow<?> row : rows) total += row.height() * row.visibleFactor();
		detailMax = Math.max(0, total - (bottom - top));
		detailScrollTarget = Math.max(0, Math.min(detailMax, detailScrollTarget));
		detailScroll.target(detailScrollTarget);
		float y = top - detailScroll.get();

		if (rows.isEmpty()) {
			Fonts.centered(g, "This module has no settings — just toggle it.", x0 + cw / 2f, top + 30, Theme.TEXT_MUTED, false, 0.9f);
		}
		g.enableScissor((int) (wx + SIDEBAR + 1), (int) top - 2, (int) (wx + ww), (int) bottom);
		long now = System.currentTimeMillis();
		int index = 0;
		for (SettingRow<?> row : rows) {
			float vis = row.visibleFactor();
			if (vis < 0.01f) continue;
			row.layout(x0, y, cw);
			if (y + row.height() > top - 40 && y < bottom + 10) {
				float appear = Easing.OUT_CUBIC.apply(Easing.clamp01((now - pageStart() - Math.min(index, 12) * 22L) / 300f));
				R2D.pushAlpha(vis * appear);
				R2D.push(g);
				R2D.translate(g, (1 - appear) * 10, 0);
				row.render(g, interactive ? mx : -1, interactive ? my : -1);
				R2D.pop(g);
				R2D.popAlpha();
			}
			y += row.height() * vis;
			index++;
		}
		g.disableScissor();
		if (detailScroll.get() > 1) R2D.gradientV(g, wx + SIDEBAR + 1, top - 2, ww - SIDEBAR - 2, 12, 0xFF0B0B0D, 0x000B0B0D);
	}

	private long detailOpenedAt;

	private long pageStart() {
		return detailOpenedAt;
	}

	// ------------------------------------------------------------------ input

	private boolean ready() {
		return !closing && open.get() > 0.9f;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!ready()) return true;
		double mx = event.x() / ui, my = event.y() / ui;
		int button = event.button();

		if (!R2D.hover(mx, my, wx, wy, ww, wh)) {
			close();
			return true;
		}

		// header
		float sy = wy + (HEADER - 22) / 2f;
		boolean inSearch = R2D.hover(mx, my, searchX(), sy, searchW(), 22);
		search.setFocused(inSearch);
		if (inSearch) return true;
		if (R2D.hover(mx, my, wx + ww - 56, sy + 3, 40, 16)) {
			Ui.click();
			close();
			return true;
		}
		if (my < wy + HEADER) return true;

		// sidebar
		for (int i = 0; i <= settingsNav(); i++) {
			if (!R2D.hover(mx, my, wx + 10, navItemY(i), SIDEBAR - 20, NAV_H)) continue;
			if (i == hudNav() && minecraft.level == null) return true;
			Ui.click();
			if (i == hudNav()) {
				CarbonClient.config().save();
				minecraft.setScreen(new HudEditor(this));
			} else if (i == settingsNav() || i == designNav()) {
				if (nav == i && onDetail()) return true;
				nav = i;
				designPage = i == designNav();
				openDetailAnimated(CarbonClient.modules().client);
			} else {
				boolean wasDetail = onDetail();
				if (wasDetail) closeDetail();
				if (nav != i || wasDetail || !search.text().isEmpty()) {
					nav = lastNav = i;
					search.setText("");
					resetGrid();
				}
			}
			return true;
		}

		if (onDetail()) return clickDetail(mx, my, button);
		return clickGrid(mx, my, button);
	}

	private void openDetailAnimated(Module m) {
		openDetail(m, true);
		detailOpenedAt = System.currentTimeMillis();
	}

	private boolean clickGrid(double mx, double my, int button) {
		float top = wy + HEADER + 52, bottom = wy + wh - 6;
		if (my < top - 3 || my > bottom) return true;
		for (Module m : visibleModules()) {
			Card c = cards.get(m);
			if (c == null || !R2D.hover(mx, my, c.x, c.y, c.w, CARD_H)) continue;
			Ui.click();
			float by = c.y + CARD_H - 19;
			boolean link = !m.settings().isEmpty() && R2D.hover(mx, my, c.x + c.w - 70, c.y + CARD_H - 19, 62, 14);
			if (link || button == 1) {
				if (!m.settings().isEmpty()) openDetailAnimated(m);
			} else {
				m.toggle();
				CarbonClient.config().saveSoon();
			}
			return true;
		}
		return true;
	}

	private boolean clickDetail(double mx, double my, int button) {
		Module m = detail;
		float x0 = contentX(), y0 = wy + HEADER + 14;
		float cw = contentW();
		boolean isClient = m == CarbonClient.modules().client;
		if (R2D.hover(mx, my, x0 - 4, y0 - 3, 120, 16)) {
			Ui.click();
			closeDetail();
			return true;
		}
		float ty = y0 + 18;
		float rx = x0 + cw;
		if (!isClient) {
			if (R2D.hover(mx, my, rx - 40, ty + 4, 40, 22)) {
				Ui.click();
				m.toggle();
				CarbonClient.config().saveSoon();
				return true;
			}
			rx -= 48;
		}
		float rw = Fonts.width("Reset", true, 0.78f) + 18;
		if (R2D.hover(mx, my, rx - rw, ty + 7, rw, 16)) {
			Ui.click();
			var client = CarbonClient.modules().client;
			for (Setting<?> s : m == client ? (designPage ? client.designSettings() : client.generalSettings()) : m.settings()) s.reset();
			openDetail(m, false);
			CarbonClient.config().saveSoon();
			return true;
		}
		float top = ty + 44, bottom = wy + wh - 10;
		boolean handled = false;
		for (SettingRow<?> row : rows) {
			if (row.visibleFactor() < 0.5f) {
				row.blur();
				continue;
			}
			if (!handled && my >= top && my <= bottom && row.mouseClicked(mx, my, button)) {
				handled = true;
				CarbonClient.config().saveSoon();
			} else {
				row.blur();
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (detail != null) for (SettingRow<?> row : rows) row.mouseDragged(event.x() / ui, event.y() / ui);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (detail != null) {
			for (SettingRow<?> row : rows) row.mouseReleased(event.x() / ui, event.y() / ui);
			CarbonClient.config().saveSoon();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (onDetail()) detailScrollTarget -= (float) scrollY * 30;
		else scrollTarget -= (float) scrollY * 30;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		boolean ctrl = event.hasControlDown();
		if (onDetail()) {
			for (SettingRow<?> row : rows) {
				if (row.capturing()) {
					row.keyPressed(key);
					CarbonClient.config().saveSoon();
					return true;
				}
			}
			if (event.isEscape()) {
				closeDetail();
				return true;
			}
			return true;
		}
		if (search.focused()) {
			if (event.isEscape()) {
				if (!search.text().isEmpty()) search.setText("");
				else search.setFocused(false);
				return true;
			}
			search.keyPressed(key, ctrl);
			return true;
		}
		if (event.isEscape() || key == CarbonClient.modules().client.menuKey.key()) {
			close();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		String s = event.codepointAsString();
		if (onDetail()) {
			for (SettingRow<?> row : rows) {
				if (row.capturing()) return row.charTyped(s);
			}
			return true;
		}
		if (!search.focused() && !s.isBlank()) search.setFocused(true);
		return search.charTyped(s);
	}

	@Override
	public void removed() {
		search.setFocused(false);
		for (SettingRow<?> r : rows) r.blur();
		CarbonClient.config().save();
	}
}
