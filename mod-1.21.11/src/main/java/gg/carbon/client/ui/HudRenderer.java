package gg.carbon.client.ui;

import gg.carbon.client.CarbonClient;
import gg.carbon.client.module.HudModule;
import gg.carbon.client.module.impl.utility.Waypoints;
import gg.carbon.client.screen.HudEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Draws every enabled HUD module at its saved position. */
public final class HudRenderer {
	private HudRenderer() {}

	public static void render(GuiGraphics g) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.player == null) return;
		if (mc.screen instanceof HudEditor) return;
		if (mc.debugEntries.isOverlayVisible() && !CarbonClient.modules().client.hudInDebug.on()) return;

		if (HudLayout.pending) {
			HudLayout.pending = false;
			HudLayout.arrange(CarbonClient.modules().hud());
			CarbonClient.config().saveSoon();
		}

		Waypoints wp =CarbonClient.modules().get(Waypoints.class);
		if (wp.enabled()) wp.render(g);

		for (HudModule m : CarbonClient.modules().hud()) {
			if (!m.enabled() || !m.shouldRender()) continue;
			draw(g, m, false);
		}
	}

	public static void draw(GuiGraphics g, HudModule m, boolean editor) {
		R2D.push(g);
		R2D.translate(g, m.x(), m.y());
		float s = m.scale.f();
		float prev = R2D.uiScale();
		if (s != 1f) {
			g.pose().scale(s, s);
			R2D.setUiScale(prev * s);
		}
		m.render(g, editor);
		R2D.setUiScale(prev);
		R2D.pop(g);
	}
}
