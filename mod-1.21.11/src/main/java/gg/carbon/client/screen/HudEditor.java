package gg.carbon.client.screen;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.HudModule;
import gg.carbon.client.screen.widget.Ui;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.HudRenderer;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Anim;
import gg.carbon.client.ui.anim.Easing;
import gg.carbon.client.ui.anim.Smooth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Drag-and-drop HUD layout editor with snapping guides. */
public class HudEditor extends Screen {
	private static final float SNAP = 4;

	private final @Nullable Screen parent;
	private final Anim open = new Anim(0, Ui.ms(380), Easing.OUT_CUBIC);
	private boolean closing;
	private @Nullable HudModule dragging;
	private float dragOX, dragOY;
	private Float guideX, guideY;
	private final Map<HudModule, Smooth> hover = new HashMap<>();
	private final Smooth guideXA = new Smooth(0, 18f), guideYA = new Smooth(0, 18f);
	private float lastGuideX, lastGuideY;
	private final Smooth btnHover = new Smooth(0, 16f), resetHover = new Smooth(0, 16f);

	public HudEditor(@Nullable Screen parent) {
		super(Component.literal("Carbon HUD Editor"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		if (!closing && open.get() == 0) open.play(0, 1);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private List<HudModule> active() {
		return CarbonClient.modules().hud().stream().filter(HudModule::enabled).toList();
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
		float o = open.get();
		if (minecraft.level == null) CarbonBackground.render(g, width, height, 1f);
		R2D.rect(g, 0, 0, width, height, Theme.withAlpha(0x000000, Math.round(90 * o)));
		// grid
		int ga = Math.round(16 * o);
		for (float x = 0; x < width; x += 20) R2D.rect(g, x, 0, 0.5f, height, Theme.withAlpha(0xFFFFFF, ga));
		for (float y = 0; y < height; y += 20) R2D.rect(g, 0, y, width, 0.5f, Theme.withAlpha(0xFFFFFF, ga));
		R2D.rect(g, width / 2f - 0.25f, 0, 0.5f, height, Theme.withAlpha(0xFFFFFF, Math.round(30 * o)));
		R2D.rect(g, 0, height / 2f - 0.25f, width, 0.5f, Theme.withAlpha(0xFFFFFF, Math.round(30 * o)));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
		if (closing && open.done()) {
			minecraft.setScreen(parent);
			return;
		}
		float o = open.get();
		R2D.pushAlpha(o);

		HudModule hovered = dragging != null ? dragging : at(mouseX, mouseY);
		for (HudModule m : active()) {
			HudRenderer.draw(g, m, true);
			Smooth s = hover.computeIfAbsent(m, k -> new Smooth(0, 16f));
			s.target(m == hovered ? 1 : 0);
			float h = s.get();
			float x = m.x(), y = m.y(), w = m.scaledWidth(), hh = m.scaledHeight();
			R2D.outline(g, x - 2, y - 2, w + 4, hh + 4, 5, 0.75f, Theme.alpha(0xFFFFFFFF, 0.18f + 0.6f * h));
			if (h > 0.01f) {
				R2D.pushAlpha(h);
				String label = m.name() + "  " + Math.round(m.scale.f() * 100) + "%";
				float lw = Fonts.width(label, true, 0.75f) + 10;
				float ly = y - 15 < 2 ? y + hh + 4 : y - 15;
				R2D.rrect(g, x - 2, ly, lw, 11, 5.5f, Theme.WHITE);
				Fonts.draw(g, label, x + 3, ly + 2.2f, 0xFF0A0A0C, true, 0.75f, false);
				R2D.popAlpha();
			}
		}

		// snapping guides
		if (guideX != null) lastGuideX = guideX;
		if (guideY != null) lastGuideY = guideY;
		guideXA.target(guideX != null ? 1 : 0);
		guideYA.target(guideY != null ? 1 : 0);
		if (guideXA.get() > 0.01f) R2D.rect(g, lastGuideX - 0.25f, 0, 0.5f, height, Theme.alpha(0xCCFFFFFF, guideXA.get()));
		if (guideYA.get() > 0.01f) R2D.rect(g, 0, lastGuideY - 0.25f, width, 0.5f, Theme.alpha(0xCCFFFFFF, guideYA.get()));

		// toolbar
		float tw = 236, th = 30;
		float tx = (width - tw) / 2f, ty = height - th - 14 + (1 - o) * 16;
		R2D.invert = gg.carbon.client.CarbonClient.modules().client.menuLight.on();
		R2D.shadow(g, tx, ty, tw, th, 10, 14, 0xA0000000);
		Ui.block(g, tx, ty, tw, th + 3, 9, 3, 0xF20E0E10, Theme.STONE_DARK, 0x26FFFFFF);
		boolean bh = R2D.hover(mouseX, mouseY, tx + 5, ty + 5, 110, 20);
		btnHover.target(bh ? 1 : 0);
		Ui.button(g, "Mod Menu", "grid", tx + 5, ty + 5, 110, 21, true, btnHover.get(), 0, 1);
		boolean rh = R2D.hover(mouseX, mouseY, tx + 121, ty + 5, 110, 20);
		resetHover.target(rh ? 1 : 0);
		Ui.button(g, "Auto Arrange", "move", tx + 121, ty + 5, 110, 21, false, resetHover.get(), 0, 1);
		R2D.invert = false;
		Ui.eyebrow(g, "HUD editor", tx, ty - 26, 0xFFB4B4BC);
		Fonts.draw(g, "Drag to move  ·  Scroll to resize  ·  Right-click for settings", tx, ty - 15, Theme.TEXT_DIM, false, 0.75f, false);

		R2D.popAlpha();
	}

	private @Nullable HudModule at(double mx, double my) {
		List<HudModule> list = active();
		for (int i = list.size() - 1; i >= 0; i--) {
			HudModule m = list.get(i);
			if (R2D.hover(mx, my, m.x() - 2, m.y() - 2, m.scaledWidth() + 4, m.scaledHeight() + 4)) return m;
		}
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing) return true;
		double mx = event.x(), my = event.y();
		float tw = 236, th = 30;
		float tx = (width - tw) / 2f, ty = height - th - 14;
		if (R2D.hover(mx, my, tx + 5, ty + 5, 110, 20)) {
			Ui.click();
			minecraft.setScreen(new ClickGui(parent));
			return true;
		}
		if (R2D.hover(mx, my, tx + 121, ty + 5, 110, 20)) {
			Ui.click();
			for (HudModule m : active()) m.scale.reset();
			gg.carbon.client.ui.HudLayout.arrange(CarbonClient.modules().hud());
			CarbonClient.config().saveSoon();
			return true;
		}
		HudModule m = at(mx, my);
		if (m == null) return true;
		if (event.button() == 1) {
			Ui.click();
			ClickGui gui = new ClickGui(this);
			minecraft.setScreen(gui);
			gui.showModule(m);
			return true;
		}
		dragging = m;
		dragOX = (float) mx - m.x();
		dragOY = (float) my - m.y();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) return true;
		HudModule m = dragging;
		float w = m.scaledWidth(), h = m.scaledHeight();
		float nx = (float) event.x() - dragOX, ny = (float) event.y() - dragOY;
		guideX = null;
		guideY = null;

		if (!event.hasShiftDown()) {
			float[] xs = {0, width / 2f, width};
			float[] ys = {0, height / 2f, height};
			float bestX = SNAP + 1, bestY = SNAP + 1;
			float snapX = nx, snapY = ny;
			for (float gx : xs) {
				for (float edge : new float[] {0, w / 2f, w}) {
					float d = Math.abs(nx + edge - gx);
					if (d < bestX) {
						bestX = d;
						snapX = gx - edge;
						guideX = gx;
					}
				}
			}
			for (float gy : ys) {
				for (float edge : new float[] {0, h / 2f, h}) {
					float d = Math.abs(ny + edge - gy);
					if (d < bestY) {
						bestY = d;
						snapY = gy - edge;
						guideY = gy;
					}
				}
			}
			for (HudModule o : active()) {
				if (o == m) continue;
				float[] ox = {o.x(), o.x() + o.scaledWidth() / 2f, o.x() + o.scaledWidth()};
				float[] oy = {o.y(), o.y() + o.scaledHeight() / 2f, o.y() + o.scaledHeight()};
				for (float gx : ox) {
					for (float edge : new float[] {0, w / 2f, w}) {
						float d = Math.abs(nx + edge - gx);
						if (d < bestX) {
							bestX = d;
							snapX = gx - edge;
							guideX = gx;
						}
					}
				}
				for (float gy : oy) {
					for (float edge : new float[] {0, h / 2f, h}) {
						float d = Math.abs(ny + edge - gy);
						if (d < bestY) {
							bestY = d;
							snapY = gy - edge;
							guideY = gy;
						}
					}
				}
			}
			nx = snapX;
			ny = snapY;
		}
		nx = Math.max(0, Math.min(width - w, nx));
		ny = Math.max(0, Math.min(height - h, ny));
		m.setPosition(nx, ny);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) CarbonClient.config().saveSoon();
		dragging = null;
		guideX = null;
		guideY = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		HudModule m = at(x, y);
		if (m == null) return true;
		float cx = m.x() + m.scaledWidth() / 2f, cy = m.y() + m.scaledHeight() / 2f;
		m.scale.set(m.scale.get() + (scrollY > 0 ? 0.05 : -0.05));
		m.setPosition(cx - m.scaledWidth() / 2f, cy - m.scaledHeight() / 2f);
		CarbonClient.config().saveSoon();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape() || event.key() == CarbonClient.modules().client.hudKey.key()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (closing) return;
		closing = true;
		open.play(open.get(), 0);
		CarbonClient.config().save();
	}
}
