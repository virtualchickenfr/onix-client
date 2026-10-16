package gg.carbon.client.screen.menu;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.screen.widget.Ui;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Fonts.Face;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.anim.Anim;
import gg.carbon.client.ui.anim.Easing;
import gg.carbon.client.ui.anim.Smooth;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** "Classic": Minecraft's panorama and the vanilla layout, drawn with Carbon's buttons. */
public final class ClassicTheme implements MenuTheme {
	private static final Identifier PANORAMA_FACE = Identifier.withDefaultNamespace("textures/gui/title/background/panorama_0.png");
	private static final Identifier PANORAMA_OVERLAY = Identifier.withDefaultNamespace("textures/gui/title/background/panorama_overlay.png");
	private static final String[] ICONS = {null, null, null, null, null, "chat", "eye", null, "hex"};

	private final LogoRenderer logo = new LogoRenderer(false);
	private final Smooth[] hover = new Smooth[Entries.COUNT];
	private final Smooth[] press = new Smooth[Entries.COUNT];
	private final Anim[] sheen = new Anim[Entries.COUNT];
	private final boolean[] wasHover = new boolean[Entries.COUNT];
	private final HitList hits = new HitList();

	public ClassicTheme() {
		for (int i = 0; i < Entries.COUNT; i++) {
			hover[i] = new Smooth(0, 14f);
			press[i] = new Smooth(0, 24f);
			sheen[i] = new Anim(1, 700, Easing.SMOOTH);
		}
	}

	@Override
	public String id() {
		return "Classic";
	}

	@Override
	public String title() {
		return "Classic";
	}

	@Override
	public String description() {
		return "Minecraft's panorama and layout with Carbon buttons";
	}

	@Override
	public boolean usesPanorama() {
		return true;
	}

	@Override
	public boolean supportsLight() {
		return false;
	}

	@Override
	public boolean vanillaLayout() {
		return true;
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

		if (c.preview()) {
			MenuArt.panoramaStill(g, w, h, false);
		}
		R2D.rect(g, 0, 0, w, h, 0x30000000);

		float fade = c.stage(0.2f, 1.0f);
		logo.renderLogo(g, (int) w, fade * R2D.alpha());

		float y = h / 4f + 48;
		float cx = w / 2f;
		button(c, Entries.SINGLEPLAYER, cx - 100, y, 200, 20, fade);
		button(c, Entries.MULTIPLAYER, cx - 100, y + 24, 200, 20, fade);
		button(c, Entries.REALMS, cx - 100, y + 48, 200, 20, fade);
		button(c, Entries.MODS, cx - 100, y + 72, 200, 20, fade);
		button(c, Entries.OPTIONS, cx - 100, y + 108, 98, 20, fade);
		button(c, Entries.QUIT, cx + 2, y + 108, 98, 20, fade);
		button(c, Entries.LANGUAGE, cx - 124, y + 108, 20, 20, fade);
		button(c, Entries.ACCESSIBILITY, cx + 104, y + 108, 20, 20, fade);
		button(c, Entries.DESIGN, cx + 128, y + 108, 20, 20, fade);

		R2D.pushAlpha(fade);
		Fonts.draw(g, "Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().name() + "  ·  Carbon " + CarbonClient.VERSION, 3, h - 8, 0x70E0E0E0, Face.BODY, 0.6f, false);
		String copy = "Copyright Mojang AB. Do not distribute!";
		Fonts.draw(g, copy, w - 3 - Fonts.width(copy, Face.BODY, 0.6f), h - 8, 0x70E0E0E0, Face.BODY, 0.6f, false);
		R2D.popAlpha();
	}

	private void button(MenuContext c, int e, float x, float y, float w, float h, float alpha) {
		hits.add(e, x, y - 2, w, h + 2);
		boolean hov = c.interactive() && R2D.hover(c.mx(), c.my(), x, y - 2, w, h + 2);
		if (hov && !wasHover[e]) sheen[e].play(0, 1);
		wasHover[e] = hov;
		hover[e].target(hov ? 1 : 0);
		press[e].target(c.pressed() == e ? 1 : 0);
		R2D.pushAlpha(alpha);
		String label = ICONS[e] != null ? "" : c.entries().get(e).label();
		Ui.button(c.g(), label, ICONS[e], x, y, w, h, false, hover[e].get(), press[e].get(), sheen[e].get());
		R2D.popAlpha();
	}
}
