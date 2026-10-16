package gg.carbon.client.screen;

import com.mojang.realmsclient.RealmsMainScreen;
import gg.carbon.client.CarbonClient;
import gg.carbon.client.screen.menu.MenuContext;
import gg.carbon.client.screen.menu.MenuEntry;
import gg.carbon.client.screen.menu.MenuTheme;
import gg.carbon.client.screen.menu.Themes;
import gg.carbon.client.screen.widget.Ui;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Smooth;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Carbon's title screen: hosts the selected {@link MenuTheme}. */
public class CarbonMainMenu extends Screen {
	private static boolean firstOpen = true;

	private final List<MenuEntry> entries = new ArrayList<>();
	private MenuTheme theme;
	private long openedAt;
	private boolean fullIntro;
	private final Smooth exit = new Smooth(0, 9f);
	private Runnable pending;
	private int pressed = -1;
	private boolean handingOver;

	private float ss = 1f, vw, vh;

	public CarbonMainMenu() {
		super(Component.literal("Onix"));
	}

	@Override
	protected void init() {
		if (openedAt == 0) {
			openedAt = System.currentTimeMillis();
			fullIntro = firstOpen;
			firstOpen = false;
		}
		buildEntries();
		syncTheme();
	}

	/** Menu entries for live previews (the picker opened from the mod menu has no title screen behind it). */
	public static List<MenuEntry> previewEntries() {
		CarbonMainMenu menu = new CarbonMainMenu();
		menu.buildEntries();
		return menu.entries;
	}

	private void buildEntries() {
		entries.clear();
		entries.add(new MenuEntry("Singleplayer", "cube", true, false, () -> minecraft.setScreen(new SelectWorldScreen(this))));
		entries.add(new MenuEntry("Multiplayer", "server", false, false, () -> minecraft.setScreen(
			minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this))));
		entries.add(new MenuEntry("Realms", "cloud", false, false, () -> minecraft.setScreen(new RealmsMainScreen(this))));
		entries.add(new MenuEntry("Mods", "grid", false, true, () -> minecraft.setScreen(new ClickGui(this))));
		entries.add(new MenuEntry("Options", "gear", false, false, () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
		entries.add(new MenuEntry("Language", "chat", false, false,
			() -> minecraft.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager()))));
		entries.add(new MenuEntry("Accessibility", "eye", false, false,
			() -> minecraft.setScreen(new AccessibilityOptionsScreen(this, minecraft.options))));
		entries.add(new MenuEntry("Quit", "power", false, false, () -> minecraft.stop()));
		entries.add(new MenuEntry("Design", "hex", false, true, () -> minecraft.setScreen(new ThemePicker(this))));
	}

	public List<MenuEntry> entries() {
		return entries;
	}

	/** Picks up design changes; the Vanilla design hands over to Minecraft's own title screen. */
	private boolean syncTheme() {
		String id = CarbonClient.modules().client.menuDesign.get();
		if (id.equals("Vanilla")) {
			if (!handingOver) {
				handingOver = true;
				minecraft.execute(() -> {
					handingOver = false;
					if (minecraft.screen == this) minecraft.setScreen(new TitleScreen());
				});
			}
			return false;
		}
		if (theme == null || !theme.id().equals(id)) theme = Themes.create(id);
		return true;
	}

	/** Restarts the intro choreography (after picking a new design). */
	void replayIntro() {
		openedAt = System.currentTimeMillis();
		fullIntro = false;
	}

	private boolean light() {
		return theme.supportsLight() && CarbonClient.modules().client.menuLight.on();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	private void layout() {
		if (theme.vanillaLayout()) {
			ss = 1f;
		} else {
			ss = Math.min(1f, 2.4f / minecraft.getWindow().getGuiScale());
			float fit = Math.min(1f, (height / ss - 30) / 330f);
			if (fit < 1f) ss *= fit;
		}
		vw = width / ss;
		vh = height / ss;
	}

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float a) {
		if (!syncTheme()) return;
		if (theme.usesPanorama()) {
			renderPanorama(g, a);
			if (theme.blurPanorama()) g.blurBeforeThisStratum();
		} else {
			R2D.rect(g, 0, 0, width, height, light() ? 0xFFF2F2F4 : 0xFF060607);
		}
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float a) {
		if (pending != null && exit.get() > 0.97f) {
			Runnable r = pending;
			pending = null;
			exit.snap(0);
			r.run();
			return;
		}
		if (theme == null) return;
		layout();
		float mx = mouseX / ss, my = mouseY / ss;
		float ex = exit.get();
		boolean light = light();

		Ui.light = light;
		R2D.pushAlpha(1 - ex);
		R2D.push(g);
		g.pose().scale(ss, ss);
		R2D.translate(g, 0, Math.round(-ex * 8));
		theme.render(new MenuContext(g, vw, vh, pending == null ? mx : -1, pending == null ? my : -1, false, openedAt, fullIntro,
			entries, pressed, minecraft.getUser().getName()));
		R2D.pop(g);
		R2D.popAlpha();
		Ui.light = false;

		// intro fade
		float e = (System.currentTimeMillis() - openedAt) / 1000f * Ui.speed();
		float intro = fullIntro ? Math.min(1, e / 1.2f) : 1;
		if (intro < 1) R2D.rect(g, 0, 0, width, height, Theme.withAlpha(light ? 0xF4F4F6 : 0x000000, Math.round(255 * (1 - intro))));
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (pending != null || event.button() != 0 || theme == null) return false;
		float mx = (float) (event.x() / ss), my = (float) (event.y() / ss);
		int e = theme.entryAt(mx, my);
		if (e >= 0) {
			pressed = e;
			Ui.click();
			return true;
		}
		Runnable extra = theme.extraActionAt(mx, my);
		if (extra != null) {
			Ui.click();
			pending = extra;
			exit.target(1);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		int p = pressed;
		pressed = -1;
		if (p >= 0 && pending == null && theme.entryAt((float) (event.x() / ss), (float) (event.y() / ss)) == p) {
			MenuEntry entry = entries.get(p);
			if (entry.instant()) entry.action().run();
			else {
				pending = entry.action();
				exit.target(1);
			}
		}
		return true;
	}

	@Override
	public void added() {
		exit.snap(0);
		pending = null;
		pressed = -1;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == CarbonClient.modules().client.menuKey.key()) {
			minecraft.setScreen(new ClickGui(this));
			return true;
		}
		if (theme != null && pending == null) {
			int e = theme.keyPressed(event.key());
			if (e >= 0) {
				activate(e);
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (theme != null && pending == null && theme.mouseScrolled(scrollY != 0 ? scrollY : -scrollX)) return true;
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	private void activate(int index) {
		Ui.click();
		MenuEntry entry = entries.get(index);
		if (entry.instant()) entry.action().run();
		else {
			pending = entry.action();
			exit.target(1);
		}
	}
}
