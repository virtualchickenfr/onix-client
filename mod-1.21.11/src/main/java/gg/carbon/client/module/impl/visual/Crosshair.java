package gg.carbon.client.module.impl.visual;

import gg.carbon.client.module.Category;
import gg.carbon.client.module.Module;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.ColorSetting;
import gg.carbon.client.setting.ModeSetting;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Smooth;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;

public class Crosshair extends Module {
	private final ModeSetting style = add(new ModeSetting("Style", "Crosshair shape", "Cross", "Cross", "Cross + Dot", "Dot", "Circle", "T-Shape"));
	private final NumberSetting length = add(new NumberSetting("Length", "Arm length", 4, 1, 12, 0.5));
	private final NumberSetting gap = add(new NumberSetting("Gap", "Space from the centre", 2, 0, 8, 0.5));
	private final NumberSetting thickness = add(new NumberSetting("Thickness", "Line thickness", 1, 0.5, 4, 0.5));
	private final ColorSetting color = add(new ColorSetting("Color", "Crosshair colour", 0xFFFFFFFF));
	private final BoolSetting outline = add(new BoolSetting("Outline", "Dark outline for visibility", true));
	private final BoolSetting targetColor = add(new BoolSetting("Highlight Target", "Change colour when aiming at an entity", true));
	private final ColorSetting target = add(new ColorSetting("Target Color", "Colour while aiming at an entity", 0xFFB4B4BC)).visibleIf(targetColor::on);
	private final BoolSetting dynamic = add(new BoolSetting("Dynamic Gap", "Widen while moving", false));
	private final BoolSetting attackIndicator = add(new BoolSetting("Attack Cooldown", "Show attack cooldown under the crosshair", true));
	private final BoolSetting thirdPerson = add(new BoolSetting("Show In Third Person", "Keep drawing in third person", false));

	private final Smooth spread = new Smooth(0, 12f);
	private final Smooth cooldownAlpha = new Smooth(0, 10f);

	public Crosshair() {
		super("crosshair", "Crosshair", "Replace the crosshair with a custom one", Category.VISUAL);
	}

	public void render(GuiGraphics g) {
		if (!thirdPerson.on() && !mc.options.getCameraType().isFirstPerson()) return;
		if (mc.player == null) return;
		float cx = g.guiWidth() / 2f, cy = g.guiHeight() / 2f;

		boolean aiming = mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity;
		int col = aiming && targetColor.on() ? target.color() : color.color();
		float moving = 0;
		if (dynamic.on()) {
			double dx = mc.player.getX() - mc.player.xo, dz = mc.player.getZ() - mc.player.zo;
			moving = (float) Math.min(1, Math.sqrt(dx * dx + dz * dz) * 6);
		}
		spread.target(moving * 3);
		float gp = gap.f() + spread.get();
		float len = length.f(), th = thickness.f();

		switch (style.get()) {
			case "Dot" -> dot(g, cx, cy, th * 1.5f, col);
			case "Circle" -> {
				if (outline.on()) R2D.circleOutline(g, cx, cy, gp + len / 2f + 0.5f, th + 1, 0x99000000);
				R2D.circleOutline(g, cx, cy, gp + len / 2f, th, col);
			}
			case "T-Shape" -> {
				arm(g, cx - gp - len, cy - th / 2f, len, th, col);
				arm(g, cx + gp, cy - th / 2f, len, th, col);
				arm(g, cx - th / 2f, cy + gp, th, len, col);
			}
			default -> {
				arm(g, cx - gp - len, cy - th / 2f, len, th, col);
				arm(g, cx + gp, cy - th / 2f, len, th, col);
				arm(g, cx - th / 2f, cy - gp - len, th, len, col);
				arm(g, cx - th / 2f, cy + gp, th, len, col);
				if (style.is("Cross + Dot")) dot(g, cx, cy, th, col);
			}
		}

		if (attackIndicator.on()) {
			float strength = mc.player.getAttackStrengthScale(0f);
			cooldownAlpha.target(strength < 1f ? 1 : 0);
			float a = cooldownAlpha.get();
			if (a > 0.01f) {
				R2D.pushAlpha(a);
				float w = 16, y = cy + gp + len + 5;
				R2D.rrect(g, cx - w / 2f, y, w, 2, 1, 0x66000000);
				R2D.rrect(g, cx - w / 2f, y, w * strength, 2, 1, Theme.WHITE);
				R2D.popAlpha();
			}
		}
	}

	private void arm(GuiGraphics g, float x, float y, float w, float h, int col) {
		if (outline.on()) R2D.rect(g, x - 0.5f, y - 0.5f, w + 1, h + 1, 0x99000000);
		R2D.rect(g, x, y, w, h, col);
	}

	private void dot(GuiGraphics g, float cx, float cy, float r, int col) {
		if (outline.on()) R2D.circle(g, cx, cy, r + 0.5f, 0x99000000);
		R2D.circle(g, cx, cy, r, col);
	}
}
