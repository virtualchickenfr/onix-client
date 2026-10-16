package gg.carbon.client.module.impl.hud;

import gg.carbon.client.module.Category;
import gg.carbon.client.module.HudModule;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.ColorSetting;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Smooth;
import gg.carbon.client.util.Stats;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;

public class Keystrokes extends HudModule {
	private final BoolSetting mouse = add(new BoolSetting("Mouse Buttons", "Show LMB and RMB", true));
	private final BoolSetting showCps = add(new BoolSetting("Show CPS", "Show CPS on the mouse buttons", true)).visibleIf(mouse::on);
	private final BoolSetting space = add(new BoolSetting("Space Bar", "Show the jump key", true));
	private final NumberSetting size = add(new NumberSetting("Key Size", "Size of a key", 22, 16, 32, 1));
	private final NumberSetting gap = add(new NumberSetting("Gap", "Space between keys", 2, 0, 6, 1));
	private final NumberSetting radius = add(new NumberSetting("Corner Radius", "Roundness of keys", 4, 0, 10, 0.5));
	private final NumberSetting bgOpacity = add(new NumberSetting("Background Opacity", "Idle key transparency", 0.45, 0, 1, 0.05));
	private final ColorSetting pressed = add(new ColorSetting("Pressed Color", "Fill of a pressed key", 0xFFFFFFFF));
	private final ColorSetting text = add(new ColorSetting("Text Color", "Key label colour", 0xFFFFFFFF));
	private final ColorSetting pressedText = add(new ColorSetting("Pressed Text Color", "Key label colour while pressed", 0xFF0A0A0C));

	private final Smooth[] anims = new Smooth[7];

	public Keystrokes() {
		super("keystrokes", "Keystrokes", "Displays your movement and mouse inputs", Category.PVP, 0.005f, 0.5f);
		for (int i = 0; i < anims.length; i++) anims[i] = new Smooth(0, 22f);
	}

	@Override
	public float width() {
		return size.f() * 3 + gap.f() * 2;
	}

	@Override
	public float height() {
		float s = size.f(), gp = gap.f();
		float h = s * 2 + gp;
		if (mouse.on()) h += gp + s * 0.9f;
		if (space.on()) h += gp + s * 0.45f;
		return h;
	}

	@Override
	public void render(GuiGraphics g, boolean editor) {
		float s = size.f(), gp = gap.f();
		var o = mc.options;
		key(g, 0, "W", s + gp, 0, s, s, o.keyUp);
		key(g, 1, "A", 0, s + gp, s, s, o.keyLeft);
		key(g, 2, "S", s + gp, s + gp, s, s, o.keyDown);
		key(g, 3, "D", (s + gp) * 2, s + gp, s, s, o.keyRight);
		float y = (s + gp) * 2;
		float full = width();
		if (mouse.on()) {
			float mw = (full - gp) / 2f, mh = s * 0.9f;
			key(g, 4, showCps.on() ? "LMB" : "LMB", 0, y, mw, mh, o.keyAttack, showCps.on() ? Stats.leftCps() + " CPS" : null);
			key(g, 5, "RMB", mw + gp, y, mw, mh, o.keyUse, showCps.on() ? Stats.rightCps() + " CPS" : null);
			y += mh + gp;
		}
		if (space.on()) {
			float sh = s * 0.45f;
			float t = anims[6].get();
			anims[6].target(o.keyJump.isDown() ? 1 : 0);
			R2D.rrect(g, 0, y, full, sh, radius.f(), Theme.lerp(Theme.withAlpha(0x0A0A0C, (int) (bgOpacity.f() * 255)), pressed.color(), t));
			float bw = full * 0.28f;
			R2D.rrect(g, (full - bw) / 2f, y + sh / 2f - 0.75f, bw, 1.5f, 0.75f, Theme.lerp(text.color(), pressedText.color(), t));
		}
	}

	private void key(GuiGraphics g, int idx, String label, float x, float y, float w, float h, KeyMapping mapping) {
		key(g, idx, label, x, y, w, h, mapping, null);
	}

	private void key(GuiGraphics g, int idx, String label, float x, float y, float w, float h, KeyMapping mapping, String sub) {
		Smooth a = anims[idx];
		a.target(mapping.isDown() ? 1 : 0);
		float t = a.get();
		int idle = Theme.withAlpha(0x0A0A0C, (int) (bgOpacity.f() * 255));
		R2D.push(g);
		R2D.scaleAt(g, x + w / 2f, y + h / 2f, 1f - 0.06f * t);
		R2D.rrect(g, x, y, w, h, radius.f(), Theme.lerp(idle, pressed.color(), t));
		int tc = Theme.lerp(text.color(), pressedText.color(), t);
		if (sub == null) {
			float tw = Fonts.width(label, true, 1f);
			Fonts.draw(g, label, x + (w - tw) / 2f, y + (h - 8) / 2f, tc, true, 1f, false);
		} else {
			float tw = Fonts.width(label, true, 0.9f);
			Fonts.draw(g, label, x + (w - tw) / 2f, y + h / 2f - 7.5f, tc, true, 0.9f, false);
			float sw = Fonts.width(sub, false, 0.6f);
			Fonts.draw(g, sub, x + (w - sw) / 2f, y + h / 2f + 1.5f, Theme.alpha(tc, 0.75f), false, 0.6f, false);
		}
		R2D.pop(g);
	}
}
