package gg.carbon.client.ui.text;

import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

/** Draws {@link CarbonFont} text 1:1 in device pixels. */
public final class TextDraw {
	private TextDraw() {}

	/**
	 * @param x,y   top-left of the line box in local units
	 * @param em    em size in local units
	 */
	public static void draw(GuiGraphics g, CarbonFont font, String text, float x, float y, float em, int color, boolean shadow) {
		int[] origin = R2D.point(g, x, y);
		float s = R2D.scale(g);
		int px = Math.max(4, Math.round(em * s));
		CarbonFont.Atlas atlas = font.atlas(px);
		int baseline = origin[1] + Math.round(font.baseline() * px);

		R2D.beginDevice(g);
		if (shadow) {
			int off = Math.max(1, Math.round(px / 12f));
			run(g, font, atlas, text, origin[0] + off, baseline + off, px, Theme.withAlpha(0x000000, Math.round(((color >>> 24) & 0xFF) * 0.6f)));
		}
		run(g, font, atlas, text, origin[0], baseline, px, color);
		R2D.endDevice(g);
	}

	private static void run(GuiGraphics g, CarbonFont font, CarbonFont.Atlas atlas, String text, int x, int baseline, int px, int color) {
		float pen = x;
		for (int i = 0; i < text.length(); ) {
			int cp = text.codePointAt(i);
			i += Character.charCount(cp);
			int ip = (int) Math.floor(pen);
			int sub = Math.round((pen - ip) * CarbonFont.SUBPIXELS);
			if (sub == CarbonFont.SUBPIXELS) {
				ip++;
				sub = 0;
			}
			CarbonFont.Glyph gl = atlas.glyph(cp, sub);
			if (gl.page() != null) {
				gl.page().flush();
				int gx = ip + gl.left();
				int gy = baseline - gl.top();
				g.blit(RenderPipelines.GUI_TEXTURED, gl.page().id, gx, gy, gl.u(), gl.v(), gl.w(), gl.h(), gl.w(), gl.h(),
					gl.page().size, gl.page().size, color);
			}
			pen += font.advance(cp) * px;
		}
	}
}
