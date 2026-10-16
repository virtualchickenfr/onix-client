package gg.carbon.client.module.impl.hud;

import gg.carbon.client.module.HudModule;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.HudStyle;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import net.minecraft.client.gui.GuiGraphics;

/** A horizontal heading strip, like in modern shooters. */
public class Compass extends HudModule {
	private final NumberSetting widthSetting = add(new NumberSetting("Width", "Width of the strip", 160, 90, 300, 2));
	private final NumberSetting fov = add(new NumberSetting("Range", "Degrees visible on the strip", 150, 60, 240, 5).suffix("°"));
	private final NumberSetting bgOpacity = add(new NumberSetting("Background Opacity", "Panel transparency", 0.45, 0, 1, 0.05));

	private static final String[] LABELS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

	public Compass() {
		super("direction", "Direction", "Shows a compass heading strip", 0.5f, 0.01f);
	}

	@Override
	public float width() {
		return widthSetting.f();
	}

	@Override
	public float height() {
		return 20;
	}

	@Override
	public void render(GuiGraphics g, boolean editor) {
		float w = width(), h = height();
		HudStyle.panel(g, 0, 0, w, h, bgOpacity.f());
		float heading = mc.player == null ? 0 : ((mc.player.getYRot() + 180f) % 360f + 360f) % 360f;
		float range = fov.f();
		float pxPerDeg = (w - 12) / range;
		float center = w / 2f;
		g.enableScissor(0, 0, (int) Math.ceil(w), (int) Math.ceil(h));
		int start = (int) Math.floor((heading - range / 2f) / 15f) * 15;
		for (int deg = start; deg <= heading + range / 2f + 15; deg += 15) {
			float x = center + (deg - heading) * pxPerDeg;
			float edge = 1f - Math.min(1f, Math.abs(x - center) / (w / 2f - 4));
			float alpha = Math.max(0, Math.min(1, edge * 1.6f));
			int norm = Math.floorMod(deg, 360);
			if (norm % 45 == 0) {
				String label = LABELS[norm / 45];
				boolean cardinal = label.length() == 1;
				Fonts.centered(g, label, x, 6, Theme.alpha(cardinal ? Theme.WHITE : Theme.TEXT_DIM, alpha), cardinal, cardinal ? 1f : 0.8f);
			} else {
				R2D.rect(g, x - 0.25f, 8, 0.5f, 4, Theme.alpha(0x88FFFFFF, alpha));
			}
		}
		g.disableScissor();
		R2D.rrect(g, center - 1, h - 3, 2, 2, 1, Theme.WHITE);
		String deg = String.valueOf(Math.round(heading) % 360);
		Fonts.centered(g, deg, center, h + 2, Theme.TEXT_DIM, false, 0.75f);
	}
}
