package gg.carbon.client.screen.menu;

import gg.carbon.client.screen.widget.Ui;
import gg.carbon.client.ui.anim.Easing;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Everything a theme needs for one frame.
 *
 * @param w,h       virtual canvas size (themes lay out in these units)
 * @param mx,my     cursor in canvas units (negative when outside / in previews)
 * @param preview   rendered as a thumbnail in the design picker (no hover, no panorama)
 * @param openedAt  time the menu appeared, drives the intro choreography
 * @param fullIntro first appearance this session: play the long intro
 * @param pressed   index of the entry currently held down, or -1
 */
public record MenuContext(GuiGraphics g, float w, float h, float mx, float my, boolean preview, long openedAt,
	boolean fullIntro, List<MenuEntry> entries, int pressed, String user) {

	/** Eased 0..1 progress of an intro stage. Previews are always fully revealed. */
	public float stage(float start, float duration) {
		if (preview) return 1f;
		float base = fullIntro ? 0 : -0.7f;
		float e = (System.currentTimeMillis() - openedAt) / 1000f * Ui.speed();
		return Easing.SMOOTH.apply(Easing.clamp01((e - start - base) / duration));
	}

	public boolean interactive() {
		return !preview;
	}
}
