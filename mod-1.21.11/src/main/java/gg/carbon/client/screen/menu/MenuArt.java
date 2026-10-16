package gg.carbon.client.screen.menu;

import gg.carbon.client.screen.widget.Ui;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Fonts.Face;
import gg.carbon.client.ui.PixelIcons;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Easing;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/** Shared pieces for the panorama based menu designs. */
public final class MenuArt {
	private MenuArt() {}

	private static final Identifier PANORAMA_FACE = Identifier.withDefaultNamespace("textures/gui/title/background/panorama_0.png");
	private static final Identifier PANORAMA_OVERLAY = Identifier.withDefaultNamespace("textures/gui/title/background/panorama_overlay.png");
	private static Supplier<PlayerSkin> skin;

	/** A still frame of the panorama, cropped to cover the canvas (previews cannot draw the live cube map). */
	public static void panoramaStill(GuiGraphics g, float w, float h, boolean dim) {
		int srcH = Math.round(1000 * Math.min(1f, h / w));
		int col = R2D.tint(0xFFFFFFFF);
		g.blit(RenderPipelines.GUI_TEXTURED, PANORAMA_FACE, 0, 0, 0, (1000 - srcH) / 2f, (int) Math.ceil(w), (int) Math.ceil(h), 1000, srcH, 1000, 1000, col);
		g.blit(RenderPipelines.GUI_TEXTURED, PANORAMA_OVERLAY, 0, 0, 0, 0, (int) Math.ceil(w), (int) Math.ceil(h), 16, 128, 16, 128, col);
		if (dim) R2D.rect(g, 0, 0, w, h, 0x8A08080A);
	}

	/** The signed-in player's face (falls back to the default skin until the real one has loaded). */
	public static void face(GuiGraphics g, float x, float y, float size) {
		Minecraft mc = Minecraft.getInstance();
		if (skin == null) skin = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
		int[] p = R2D.point(g, x, y);
		float s = R2D.scale(g);
		int px = Math.max(8, Math.round(size * s / 8f) * 8);
		R2D.beginDevice(g);
		PlayerFaceRenderer.draw(g, skin.get(), p[0], p[1], px, R2D.tint(0xFFFFFFFF));
		R2D.endDevice(g);
	}

	/**
	 * Left-aligned navigation button in Carbon's block style: icon, label, and an arrow that slides
	 * in on hover.
	 */
	public static void navButton(GuiGraphics g, String label, String icon, float x, float y, float w, float h,
		boolean primary, float hover, float press, float sheen) {
		float s = R2D.scale(g);
		float lift = Math.round((-1.5f * hover + 1.5f * press) * s) / s;
		float depth = Math.round((3.5f - 2f * press) * s) / s;
		boolean light = Ui.light;
		int face, dark, text, edge;
		if (light) {
			face = primary ? Theme.lerp(0xFF1B1B1F, 0xFF2A2A30, hover) : Theme.lerp(0xF2FFFFFF, 0xFFF4F4F6, hover);
			dark = primary ? 0xFF000000 : 0xFFC4C4CB;
			text = primary ? 0xFFF4F4F6 : 0xFF17171A;
			edge = primary ? 0 : Theme.alpha(0xFF000000, 0.10f + 0.10f * hover);
		} else {
			face = primary ? Theme.lerp(0xFFE6E6E9, 0xFFFFFFFF, hover) : Theme.lerp(0xE01A1A1E, 0xF0252529, hover);
			dark = primary ? 0xFF8C8C94 : 0xF0060607;
			text = primary ? 0xFF0B0B0D : Theme.TEXT;
			edge = primary ? 0 : Theme.alpha(0xFFFFFFFF, 0.12f + 0.16f * hover);
		}
		R2D.push(g);
		R2D.translate(g, 0, lift);
		R2D.shadow(g, x + 2, y + h - 6, w - 4, 8, 4, 9, Theme.alpha(light ? 0x40000000 : 0x90000000, 0.5f + 0.5f * hover));
		Ui.block(g, x, y, w, h, 5, depth, face, dark, edge);
		if (sheen > 0 && sheen < 1) {
			float bw = w * 0.4f;
			R2D.sheen(g, x + 4, x + w - 4, y + 1.5f, h - depth - 2.5f, x - bw + (w + bw) * Easing.SMOOTH.apply(sheen), bw,
				light ? (primary ? 0x30FFFFFF : 0x10000000) : (primary ? 0x70FFFFFF : 0x24FFFFFF));
		}
		float ty = y + (h - depth - 8.5f) / 2f;
		PixelIcons.draw(g, icon, x + 12, ty - 0.2f, 9, text);
		Fonts.draw(g, label, x + 30 + hover * 2, ty, text, Face.MEDIUM, 1f, false);
		R2D.pushAlpha(hover);
		PixelIcons.draw(g, "arrow", x + w - 22 + hover * 4, ty - 0.2f, 9, text);
		R2D.popAlpha();
		R2D.pop(g);
	}

	/** Round icon button with a label pill that appears above it on hover. */
	public static void roundButton(GuiGraphics g, String icon, String label, float cx, float cy, float r, float hover, float press) {
		float s = R2D.scale(g);
		float lift = Math.round((-1.5f * hover + 1f * press) * s) / s;
		R2D.push(g);
		R2D.translate(g, 0, lift);
		R2D.shadow(g, cx - r, cy - r + 2, r * 2, r * 2, r, 6, Theme.alpha(0x80000000, 0.6f + 0.4f * hover));
		R2D.circle(g, cx, cy + 1.5f, r, 0xF0060607);
		R2D.circle(g, cx, cy, r, Theme.lerp(0xE01A1A1E, 0xF02A2A30, hover));
		R2D.circleOutline(g, cx, cy, r, 0.75f, Theme.alpha(0xFFFFFFFF, 0.14f + 0.2f * hover));
		PixelIcons.draw(g, icon, cx - 4.5f, cy - 4.5f, 9, 0xFFEDEDF0);
		R2D.pop(g);
		if (hover > 0.01f) {
			float lw = Fonts.width(label, Face.MEDIUM, 0.85f) + 14;
			float ly = cy - r - 21 + (1 - hover) * 4;
			R2D.pushAlpha(hover);
			R2D.rrect(g, cx - lw / 2f, ly, lw, 15, 4, 0xF0EDEDF0);
			Fonts.centered(g, label, cx, ly + 3.6f, 0xFF0B0B0D, Face.MEDIUM, 0.85f);
			R2D.popAlpha();
		}
	}
}
