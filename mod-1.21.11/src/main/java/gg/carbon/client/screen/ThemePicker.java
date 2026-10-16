package gg.carbon.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import gg.carbon.client.CarbonClient;
import gg.carbon.client.screen.menu.MenuContext;
import gg.carbon.client.screen.menu.MenuTheme;
import gg.carbon.client.screen.menu.Themes;
import gg.carbon.client.screen.widget.Ui;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Fonts.Face;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Anim;
import gg.carbon.client.ui.anim.Easing;
import gg.carbon.client.ui.anim.Smooth;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** iOS-style wallpaper picker for the main menu: live previews you swipe through. */
public class ThemePicker extends Screen {
	private final Screen parent;
	private final List<gg.carbon.client.screen.menu.MenuEntry> entries;
	private final List<MenuTheme> themes = new ArrayList<>();
	private final Smooth pos;
	private final Anim open = new Anim(0, Ui.ms(520), Easing.SMOOTH);
	private boolean closing;
	private Runnable after;

	private boolean dragging, dragMoved;
	private double dragStartX, dragStartPos, lastX;
	private long lastT;
	private double velocity;

	private final Smooth applyHover = new Smooth(0, 16f), cancelHover = new Smooth(0, 16f);
	private final Smooth applyPress = new Smooth(0, 24f);
	private final Anim applySheen = new Anim(1, 700, Easing.SMOOTH), cancelSheen = new Anim(1, 700, Easing.SMOOTH);
	private boolean applyWas, cancelWas, applyDown;

	// layout (picker canvas units)
	private float ps = 1f, W, H, cardW, cardH, cardTop, spacing;
	private float applyX, applyY, cancelX, buttonY, buttonW = 132, buttonH = 26;

	public ThemePicker(Screen parent) {
		super(Component.literal("Menu design"));
		this.parent = parent;
		this.entries = parent instanceof CarbonMainMenu menu ? menu.entries() : CarbonMainMenu.previewEntries();
		for (String id : Themes.IDS) themes.add(Themes.create(id));
		this.pos = new Smooth(Themes.index(CarbonClient.modules().client.menuDesign.get()), 9f);
	}

	@Override
	protected void init() {
		if (open.get() == 0 && !closing) open.play(0, 1);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private int current() {
		return Math.max(0, Math.min(themes.size() - 1, Math.round(pos.target())));
	}

	private float previewScale(MenuTheme t) {
		if (t.vanillaLayout()) return 1f;
		float ss = Math.min(1f, 2.4f / minecraft.getWindow().getGuiScale());
		float fit = Math.min(1f, (height / ss - 30) / 320f);
		return fit < 1f ? ss * fit : ss;
	}

	private void layout() {
		ps = Math.min(1f, 2.4f / minecraft.getWindow().getGuiScale());
		W = width / ps;
		H = height / ps;
		float aspect = (float) width / height;
		cardH = Math.min(H * 0.52f, W * 0.5f / aspect);
		cardW = cardH * aspect;
		cardTop = H * 0.15f;
		spacing = cardW * 0.88f + 16;
		buttonY = cardTop + cardH + 100;
		applyX = W / 2f + 5;
		cancelX = W / 2f - 5 - buttonW;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
		R2D.rect(g, 0, 0, width, height, light() ? 0xFFEDEDF1 : 0xFF050506);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
		if (closing && open.done()) {
			minecraft.setScreen(parent);
			if (after != null) after.run();
			return;
		}
		layout();
		float o = open.get();
		float p = pos.get();

		// live, blurred backdrop of the design in focus
		MenuTheme focus = themes.get(Math.max(0, Math.min(themes.size() - 1, Math.round(p))));
		float bs = previewScale(focus);
		R2D.push(g);
		g.pose().scale(bs, bs);
		Ui.light = focus.supportsLight() && CarbonClient.modules().client.menuLight.on();
		focus.render(ctx(g, width / bs, height / bs));
		Ui.light = false;
		R2D.pop(g);
		g.nextStratum();
		g.blurBeforeThisStratum();
		R2D.rect(g, 0, 0, width, height, light() ? Theme.withAlpha(0xEFEFF2, Math.round(200 * o)) : Theme.withAlpha(0x060607, Math.round(205 * o)));
		R2D.invert = light();
		try {
			chrome(g, mouseX, mouseY, o, p);
		} finally {
			R2D.invert = false;
		}
	}

	private static boolean light() {
		return CarbonClient.modules().client.menuLight.on();
	}

	private void chrome(GuiGraphics g, int mouseX, int mouseY, float o, float p) {

		float mx = mouseX / ps, my = mouseY / ps;
		R2D.pushAlpha(o);
		R2D.push(g);
		g.pose().scale(ps, ps);
		R2D.translate(g, 0, Math.round((1 - o) * 16));

		// header
		String eyebrow = "MAIN MENU DESIGN";
		float ew = 0;
		for (char ch : eyebrow.toCharArray()) ew += Fonts.width(String.valueOf(ch), Face.BODY_BOLD, 0.74f) + 1f;
		Ui.eyebrow(g, eyebrow, W / 2f - (ew + 13) / 2f, cardTop - 34, 0xFFB4B4BC);
		Fonts.centered(g, "Drag, scroll or use the arrow keys", W / 2f, cardTop - 21, 0xFF6E6E78, Face.BODY, 0.82f);

		// cards
		for (int i = 0; i < themes.size(); i++) {
			float d = i - p;
			if (Math.abs(d) > 2.3f) continue;
			float ad = Math.min(1, Math.abs(d));
			float scale = 1 - 0.14f * ad;
			float alpha = 1 - 0.45f * ad;
			float w = cardW * scale, h = cardH * scale;
			float x = W / 2f + d * spacing - w / 2f;
			float y = cardTop + (cardH - h) / 2f;
			// align the card to whole GUI units so the clip rectangle matches it exactly
			float gx0 = Math.round(x * ps), gy0 = Math.round(y * ps), gx1 = Math.round((x + w) * ps), gy1 = Math.round((y + h) * ps);
			x = gx0 / ps;
			y = gy0 / ps;
			w = (gx1 - gx0) / ps;
			h = (gy1 - gy0) / ps;
			card(g, themes.get(i), x, y, w, h, alpha, i == current() && !dragging);
		}

		// title + description
		MenuTheme sel = themes.get(current());
		float ty = cardTop + cardH + 16;
		Fonts.centered(g, sel.title(), W / 2f, ty, Theme.WHITE, Face.BODY_BOLD, 1.5f);
		Fonts.centered(g, sel.description(), W / 2f, ty + 18, 0xFF8E8E98, Face.BODY, 0.9f);

		// page dots
		float dotsY = ty + 38;
		float dw = 0;
		for (int i = 0; i < themes.size(); i++) dw += (i == current() ? 14 : 5) + 5;
		float dx = W / 2f - (dw - 5) / 2f;
		for (int i = 0; i < themes.size(); i++) {
			float near = 1 - Math.min(1, Math.abs(i - p));
			float dwid = 5 + 9 * near;
			R2D.rrect(g, dx, dotsY, dwid, 5, 2.5f, Theme.lerp(0x40FFFFFF, Theme.WHITE, near));
			dx += dwid + 5;
		}

		modeToggle(g, mx, my, dotsY + 16);

		// buttons
		boolean ah = R2D.hover(mx, my, applyX, buttonY - 3, buttonW, buttonH + 3);
		boolean ch = R2D.hover(mx, my, cancelX, buttonY - 3, buttonW, buttonH + 3);
		if (ah && !applyWas) applySheen.play(0, 1);
		if (ch && !cancelWas) cancelSheen.play(0, 1);
		applyWas = ah;
		cancelWas = ch;
		applyHover.target(ah ? 1 : 0);
		cancelHover.target(ch ? 1 : 0);
		applyPress.target(applyDown ? 1 : 0);
		boolean isCurrent = sel.id().equals(CarbonClient.modules().client.menuDesign.get());
		Ui.button(g, "Cancel", null, cancelX, buttonY, buttonW, buttonH, false, cancelHover.get(), 0, cancelSheen.get());
		Ui.button(g, isCurrent ? "In use" : "Use design", isCurrent ? "hex" : "arrow", applyX, buttonY, buttonW, buttonH, true,
			applyHover.get(), applyPress.get(), applySheen.get());

		R2D.pop(g);
		R2D.popAlpha();
	}

	private final Smooth modePos = new Smooth(CarbonClient.modules().client.menuLight.on() ? 1 : 0, 16f);
	private final Smooth modeFade = new Smooth(1, 14f);
	private float modeX, modeY, modeW = 132, modeH = 18;

	/** Segmented Dark / Light switch for the menu palette. */
	private void modeToggle(GuiGraphics g, float mx, float my, float y) {
		modeX = W / 2f - modeW / 2f;
		modeY = y;
		boolean supported = themes.get(current()).supportsLight();
		boolean light = supported && CarbonClient.modules().client.menuLight.on();
		modePos.target(light ? 1 : 0);
		modeFade.target(supported ? 1 : 0.35f);
		R2D.pushAlpha(modeFade.get());
		float p = modePos.get();
		R2D.rrect(g, modeX, modeY, modeW, modeH, 9, 0xFF141417);
		R2D.outline(g, modeX, modeY, modeW, modeH, 9, 0.75f, 0x26FFFFFF);
		float half = modeW / 2f;
		R2D.rrect(g, modeX + 2 + (half - 2) * p, modeY + 2, half - 2, modeH - 4, 7, Theme.WHITE);
		for (int i = 0; i < 2; i++) {
			String label = i == 0 ? "Dark" : "Light";
			float near = 1 - Math.min(1, Math.abs(p - i));
			int col = Theme.lerp(0xFF8E8E98, 0xFF0B0B0D, near);
			float lw = Fonts.width(label, Face.MEDIUM, 0.85f) + 13;
			float lx = modeX + half * i + half / 2f - lw / 2f;
			gg.carbon.client.ui.PixelIcons.draw(g, i == 0 ? "moon" : "sun", lx, modeY + 4.5f, 9, col);
			Fonts.draw(g, label, lx + 13, modeY + 4.8f, col, Face.MEDIUM, 0.85f, false);
		}
		R2D.popAlpha();
	}

	private MenuContext ctx(GuiGraphics g, float w, float h) {
		return new MenuContext(g, w, h, -1, -1, true, 0, false, entries, -1, minecraft.getUser().getName());
	}

	private void card(GuiGraphics g, MenuTheme theme, float x, float y, float w, float h, float alpha, boolean focused) {
		R2D.pushAlpha(alpha);
		R2D.shadow(g, x, y + 10, w, h, 8, 28, 0xB0000000);
		R2D.rect(g, x, y, w, h, 0xFF060607);
		float ts = previewScale(theme);
		float canvasW = width / ts, canvasH = height / ts;
		// clip in real GUI units (the card is aligned to them), independent of the picker's own scale
		R2D.push(g);
		g.pose().identity();
		g.enableScissor(Math.round(x * ps), Math.round(y * ps), Math.round((x + w) * ps), Math.round((y + h) * ps));
		R2D.pop(g);
		R2D.push(g);
		R2D.translate(g, x, y);
		g.pose().scale(w / canvasW, h / canvasH);
		boolean inverted = R2D.invert;
		R2D.invert = false;
		Ui.light = theme.supportsLight() && CarbonClient.modules().client.menuLight.on();
		theme.render(ctx(g, canvasW, canvasH));
		Ui.light = false;
		R2D.invert = inverted;
		R2D.pop(g);
		g.disableScissor();
		R2D.outline(g, x, y, w, h, 0, 1f, 0x2EFFFFFF);
		if (focused) R2D.outline(g, x - 5, y - 5, w + 10, h + 10, 7, 1.5f, Theme.alpha(Theme.WHITE, 0.85f));
		R2D.popAlpha();
	}

	// ------------------------------------------------------------------ input

	private void go(int index) {
		index = Math.max(0, Math.min(themes.size() - 1, index));
		if (index != current()) Ui.click();
		pos.target(index);
	}

	private void apply() {
		String id = themes.get(current()).id();
		CarbonClient.modules().client.menuDesign.set(id);
		CarbonClient.config().save();
		if (parent instanceof CarbonMainMenu menu) after = menu::replayIntro;
		close();
	}

	private void close() {
		if (closing) return;
		closing = true;
		open.play(open.get(), 0);
	}

	@Override
	public void onClose() {
		close();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing || event.button() != 0) return true;
		float mx = (float) (event.x() / ps), my = (float) (event.y() / ps);
		if (R2D.hover(mx, my, applyX, buttonY - 3, buttonW, buttonH + 3)) {
			applyDown = true;
			Ui.click();
			return true;
		}
		if (R2D.hover(mx, my, modeX, modeY, modeW, modeH) && themes.get(current()).supportsLight()) {
			Ui.click();
			var setting = CarbonClient.modules().client.menuLight;
			setting.set(mx >= modeX + modeW / 2f);
			CarbonClient.config().saveSoon();
			return true;
		}
		if (R2D.hover(mx, my, cancelX, buttonY - 3, buttonW, buttonH + 3)) {
			Ui.click();
			close();
			return true;
		}
		if (my >= cardTop - 10 && my <= cardTop + cardH + 10) {
			dragging = true;
			dragMoved = false;
			dragStartX = mx;
			dragStartPos = pos.get();
			lastX = mx;
			lastT = System.nanoTime();
			velocity = 0;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (!dragging) return true;
		double mx = event.x() / ps;
		if (Math.abs(mx - dragStartX) > 3) dragMoved = true;
		double np = dragStartPos - (mx - dragStartX) / spacing;
		np = Math.max(-0.35, Math.min(themes.size() - 0.65, np));
		pos.snap((float) np);
		long now = System.nanoTime();
		double dt = Math.max(1e-3, (now - lastT) / 1e9);
		double v = -(mx - lastX) / spacing / dt;
		velocity = velocity * 0.6 + v * 0.4;
		lastX = mx;
		lastT = now;
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (applyDown) {
			applyDown = false;
			float mx = (float) (event.x() / ps), my = (float) (event.y() / ps);
			if (R2D.hover(mx, my, applyX, buttonY - 3, buttonW, buttonH + 3)) apply();
			return true;
		}
		if (!dragging) return true;
		dragging = false;
		if (!dragMoved) {
			// tap: jump to the card that was clicked
			float mx = (float) (event.x() / ps);
			float d = (mx - W / 2f) / spacing;
			int target = Math.round(pos.get() + (Math.abs(d) < 0.5f ? 0 : Math.signum(d)));
			go(target);
			return true;
		}
		double projected = pos.get() + Math.max(-1.2, Math.min(1.2, velocity * 0.18));
		go((int) Math.round(projected));
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		double delta = scrollY != 0 ? scrollY : -scrollX;
		if (delta != 0) go(current() + (delta > 0 ? -1 : 1));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		if (event.isEscape()) {
			close();
			return true;
		}
		if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_A) {
			go(current() - 1);
			return true;
		}
		if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_D) {
			go(current() + 1);
			return true;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER || key == InputConstants.KEY_SPACE) {
			apply();
			return true;
		}
		return super.keyPressed(event);
	}
}
