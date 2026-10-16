package gg.carbon.client.module.impl.visual;

import gg.carbon.client.module.Category;
import gg.carbon.client.module.Module;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.NumberSetting;

/** Dawn "Dark Mode": darkens every container GUI (inventory, chests, furnaces…) while keeping its shading. */
public class DarkMode extends Module {
	public final NumberSetting brightness = add(new NumberSetting("Brightness", "How light the darkened GUI stays", 0.30, 0.12, 0.6, 0.02));
	public final BoolSetting lightText = add(new BoolSetting("Light Titles", "Make container titles readable on the dark background", true));
	public final BoolSetting creative = add(new BoolSetting("Creative Inventory", "Also darken the creative inventory", true));

	public DarkMode() {
		super("darkmode", "Dark Mode", "A dark coat of paint for every container", Category.VISUAL);
	}

	/** Multiplies a texture tint with the dark-mode factor. */
	public int apply(int color) {
		float f = brightness.f();
		int a = (color >>> 24) & 0xFF, r = (color >> 16) & 0xFF, gr = (color >> 8) & 0xFF, b = color & 0xFF;
		return (a << 24) | (Math.round(r * f) << 16) | (Math.round(gr * f) << 8) | Math.round(b * f);
	}
}
