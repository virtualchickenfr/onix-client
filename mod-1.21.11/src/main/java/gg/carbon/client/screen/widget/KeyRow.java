package gg.carbon.client.screen.widget;

import com.mojang.blaze3d.platform.InputConstants;
import gg.carbon.client.setting.KeySetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Smooth;
import net.minecraft.client.gui.GuiGraphics;

public class KeyRow extends SettingRow<KeySetting> {
	private boolean listening;
	private final Smooth listen = new Smooth(0, 14f);
	private final Smooth width = new Smooth(-1, 16f);

	public KeyRow(KeySetting setting) {
		super(setting);
	}

	@Override
	protected void renderControl(GuiGraphics g, double mx, double my) {
		float cy = controlCenterY();
		listen.target(listening ? 1 : 0);
		float l = listen.get();
		String text = listening ? "Press a key…" : setting.display();
		float target = Fonts.width(text, listening ? Fonts.Face.BODY_BOLD : Fonts.Face.MONO_BOLD, 0.8f) + 20;
		if (width.target() < 0) width.snap(target);
		width.target(target);
		float bw = width.get();
		float bx = x + w - 12 - bw;
		float pulse = listening ? 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 180.0) : 0;
		R2D.rrect(g, bx, cy - 8, bw, 16, 3.5f, Theme.lerp(0xFF1A1A1E, 0xFF2C2C32, l));
		R2D.outline(g, bx, cy - 8, bw, 16, 3.5f, 0.75f, Theme.alpha(0xFFFFFFFF, 0.15f + 0.45f * l * pulse));
		Fonts.centered(g, listening ? text : text.toUpperCase(), bx + bw / 2f, cy - 3.2f, listening ? Theme.WHITE : Theme.TEXT, listening ? Fonts.Face.BODY_BOLD : Fonts.Face.MONO_BOLD, 0.8f);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (R2D.hover(mx, my, x, y, w, BASE_HEIGHT - 3)) {
			listening = !listening;
			Ui.click();
			return true;
		}
		listening = false;
		return false;
	}

	@Override
	public boolean keyPressed(int key) {
		if (!listening) return false;
		if (key == InputConstants.KEY_ESCAPE) {
			listening = false;
		} else if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
			setting.set(0);
			listening = false;
		} else {
			setting.set(key);
			listening = false;
		}
		return true;
	}

	@Override
	public boolean capturing() {
		return listening;
	}

	@Override
	public void blur() {
		listening = false;
	}
}
