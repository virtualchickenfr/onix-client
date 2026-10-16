package gg.carbon.client.screen.menu;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * "Vanilla": the untouched Minecraft title screen. When selected, the menu host hands over to the
 * real {@code TitleScreen}; this class only paints a faithful thumbnail for the design picker.
 */
public final class VanillaTheme implements MenuTheme {
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final String[] LABELS = {"Singleplayer", "Multiplayer", "Minecraft Realms"};

	private final LogoRenderer logo = new LogoRenderer(false);

	@Override
	public String id() {
		return "Vanilla";
	}

	@Override
	public String title() {
		return "Vanilla";
	}

	@Override
	public String description() {
		return "The untouched Minecraft title screen";
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
		return -1;
	}

	@Override
	public void render(MenuContext c) {
		GuiGraphics g = c.g();
		float w = c.w(), h = c.h();
		MenuArt.panoramaStill(g, w, h, false);
		logo.renderLogo(g, (int) w, 1f);
		var font = Minecraft.getInstance().font;
		int cx = (int) (w / 2), y = (int) (h / 4 + 48);
		for (int i = 0; i < 3; i++) button(g, font, LABELS[i], cx - 100, y + i * 24, 200);
		button(g, font, "Options...", cx - 100, y + 72 + 12, 98);
		button(g, font, "Quit Game", cx + 2, y + 72 + 12, 98);
		g.drawString(font, "Minecraft " + net.minecraft.SharedConstants.getCurrentVersion().name(), 2, (int) h - 10, 0xFFFFFFFF, true);
		String copy = "Copyright Mojang AB. Do not distribute!";
		g.drawString(font, copy, (int) w - font.width(copy) - 2, (int) h - 10, 0xFFFFFFFF, true);
	}

	private static void button(GuiGraphics g, net.minecraft.client.gui.Font font, String label, int x, int y, int w) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, BUTTON, x, y, w, 20);
		g.drawCenteredString(font, label, x + w / 2, y + 6, 0xFFFFFFFF);
	}
}
