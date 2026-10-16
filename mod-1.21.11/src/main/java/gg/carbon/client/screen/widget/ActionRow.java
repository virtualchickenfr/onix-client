package gg.carbon.client.screen.widget;

import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Fonts.Face;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.anim.Anim;
import gg.carbon.client.ui.anim.Easing;
import gg.carbon.client.ui.anim.Smooth;
import net.minecraft.client.gui.GuiGraphics;

/** A settings row whose control is a button that runs an action (e.g. "open the background switcher"). */
public class ActionRow extends SettingRow<BoolSetting> {
	private final String button;
	private final java.util.function.Supplier<String> action;
	private String status;
	private final Smooth buttonHover = new Smooth(0, 16f);
	private final Anim sheen = new Anim(1, 700, Easing.SMOOTH);
	private boolean was;

	public ActionRow(String name, String description, String button, Runnable action) {
		this(name, description, button, () -> {
			action.run();
			return null;
		});
	}

	/** {@code action} returns a status line shown in place of the description (or null). */
	public ActionRow(String name, String description, String button, java.util.function.Supplier<String> action) {
		super(new BoolSetting(name, description, false));
		this.button = button;
		this.action = action;
	}

	@Override
	protected String description() {
		return status != null ? status : super.description();
	}

	private float bw() {
		return Fonts.width(button, Face.MEDIUM, 1f) + 34;
	}

	@Override
	protected void renderControl(GuiGraphics g, double mx, double my) {
		float bw = bw(), bh = 20;
		float bx = x + w - 10 - bw, by = controlCenterY() - bh / 2f - 1;
		boolean hov = R2D.hover(mx, my, bx, by - 2, bw, bh + 2);
		if (hov && !was) sheen.play(0, 1);
		was = hov;
		buttonHover.target(hov ? 1 : 0);
		Ui.button(g, button, "arrow", bx, by, bw, bh, true, buttonHover.get(), 0, sheen.get());
	}

	@Override
	public boolean mouseClicked(double mx, double my, int b) {
		if (R2D.hover(mx, my, x, y, w, BASE_HEIGHT - 3)) {
			Ui.click();
			String result = action.get();
			if (result != null) status = result;
			return true;
		}
		return false;
	}
}
