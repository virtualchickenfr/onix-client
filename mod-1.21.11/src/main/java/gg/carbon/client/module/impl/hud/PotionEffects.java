package gg.carbon.client.module.impl.hud;

import gg.carbon.client.module.HudModule;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.HudStyle;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class PotionEffects extends HudModule {
	public final BoolSetting hideVanilla = add(new BoolSetting("Hide Vanilla", "Hide the vanilla effect icons in the top right", true));
	private final BoolSetting background = add(new BoolSetting("Background", "Draw a panel", true));
	private final NumberSetting bgOpacity = add(new NumberSetting("Background Opacity", "Panel transparency", 0.45, 0, 1, 0.05)).visibleIf(background::on);
	private final BoolSetting showLevel = add(new BoolSetting("Show Level", "Append the amplifier (II, III…)", true));
	private final BoolSetting blink = add(new BoolSetting("Blink When Ending", "Fade the icon in the last 10 seconds", true));

	private static final String[] ROMAN = {"", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

	public PotionEffects() {
		super("potionEffects", "Potion Effects", "Lists your active potion effects", 0.995f, 0.30f);
	}

	private List<MobEffectInstance> effects(boolean editor) {
		List<MobEffectInstance> list = new ArrayList<>();
		if (mc.player != null) list.addAll(mc.player.getActiveEffects());
		if (list.isEmpty() && editor) {
			list.add(new MobEffectInstance(MobEffects.SPEED, 20 * 95, 1));
			list.add(new MobEffectInstance(MobEffects.STRENGTH, 20 * 42, 0));
			list.add(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 300, 0));
		}
		return list;
	}

	private String name(MobEffectInstance e) {
		String n = e.getEffect().value().getDisplayName().getString();
		int amp = e.getAmplifier();
		if (showLevel.on() && amp > 0) n += amp < ROMAN.length ? ROMAN[amp] : " " + (amp + 1);
		return n;
	}

	private String duration(MobEffectInstance e) {
		if (e.isInfiniteDuration()) return "--:--";
		int s = e.getDuration() / 20;
		return String.format("%d:%02d", s / 60, s % 60);
	}

	@Override
	public float width() {
		float w = 60;
		for (MobEffectInstance e : effects(true)) {
			w = Math.max(w, 22 + Math.max(Fonts.width(name(e), true, 1f), Fonts.width(duration(e), false, 0.85f)));
		}
		return w + (background.on() ? 10 : 0);
	}

	@Override
	public float height() {
		int n = Math.max(1, effects(true).size());
		return n * 22 - 2 + (background.on() ? 8 : 0);
	}

	@Override
	public void render(GuiGraphics g, boolean editor) {
		List<MobEffectInstance> list = effects(editor);
		if (list.isEmpty()) return;
		if (background.on()) HudStyle.panel(g, 0, 0, width(), height(), bgOpacity.f());
		float x = background.on() ? 5 : 0, y = background.on() ? 4 : 0;
		for (MobEffectInstance e : list) {
			float alpha = 1f;
			if (blink.on() && !e.isInfiniteDuration() && e.getDuration() < 200) {
				alpha = 0.45f + 0.55f * (float) (0.5 + 0.5 * Math.cos(System.currentTimeMillis() / 160.0));
			}
			int col = Theme.alpha(Theme.WHITE, alpha);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, Gui.getMobEffectSprite(e.getEffect()), (int) x, (int) (y + 1), 18, 18, col);
			Fonts.draw(g, name(e), x + 22, y + 1.5f, Theme.WHITE, true, 1f, false);
			Fonts.draw(g, duration(e), x + 22, y + 11, Theme.TEXT_DIM, false, 0.85f, false);
			y += 22;
		}
	}
}
