package gg.carbon.client.screen.menu;

import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Fonts.Face;
import gg.carbon.client.ui.R2D;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * "Onix": Onix's own title screen (with its designs, backgrounds and themes). Picking it hands over to
 * {@code OnixTitleScreen}; this class only paints a thumbnail for the design picker.
 */
public final class OnixTheme implements MenuTheme {
	private static final Identifier ICON = Identifier.fromNamespaceAndPath("onix", "icon.png");
	private static final String[] LABELS = {"Singleplayer", "Multiplayer", "Cosmetics", "Settings"};

	@Override
	public String id() {
		return "Onix";
	}

	@Override
	public String title() {
		return "Onix";
	}

	@Override
	public String description() {
		return "Onix's title screen with its own designs, backgrounds and themes";
	}

	@Override
	public boolean supportsLight() {
		return false;
	}

	@Override
	public int entryAt(float mx, float my) {
		return -1;
	}

	@Override
	public void render(MenuContext c) {
		GuiGraphics g = c.g();
		float w = c.w(), h = c.h();
		int accent = dev.onix.client.gui.Theme.accent();
		R2D.rect(g, 0, 0, w, h, 0xFF0B0B10);
		R2D.rect(g, 0, 0, w, h * 0.55f, (accent & 0x00FFFFFF) | 0x14000000);
		int size = 34;
		int cx = (int) (w / 2);
		int top = (int) (h * 0.22f);
		g.blit(RenderPipelines.GUI_TEXTURED, ICON, cx - size / 2, top, 0, 0, size, size, size, size);
		String name = "ONIX CLIENT";
		Fonts.draw(g, name, cx - Fonts.width(name, Face.DISPLAY, 1.1f) / 2f, top + size + 8, 0xFFFFFFFF, Face.DISPLAY, 1.1f, false);
		float bw = 150, bh = 20, y = top + size + 32;
		for (String label : LABELS) {
			R2D.rrect(g, cx - bw / 2f, y, bw, bh, 6, 0x22FFFFFF);
			Fonts.draw(g, label, cx - Fonts.width(label, Face.MEDIUM, 0.9f) / 2f, y + 6, 0xFFE8E8EE, Face.MEDIUM, 0.9f, false);
			y += bh + 6;
		}
		R2D.rect(g, cx - 18, y + 6, 36, 2, accent | 0xFF000000);
	}
}
