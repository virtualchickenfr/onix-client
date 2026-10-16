package gg.carbon.client.ui;

import gg.carbon.client.module.HudModule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Stacks HUD elements into tidy columns based on their default anchor, so nothing overlaps. */
public final class HudLayout {
	private HudLayout() {}

	private static final float MARGIN = 4;
	private static final float GAP = 3;
	private static final float HOTBAR_CLEARANCE = 52;

	/** Set when no config existed; the first HUD frame arranges the layout. */
	public static boolean pending;

	public static void arrange(List<HudModule> modules) {
		Minecraft mc = Minecraft.getInstance();
		float sw = mc.getWindow().getGuiScaledWidth();
		float sh = mc.getWindow().getGuiScaledHeight();
		List<HudModule> enabled = modules.stream().filter(HudModule::enabled).toList();

		for (int column = 0; column < 3; column++) {
			for (int half = 0; half < 2; half++) {
				List<HudModule> group = new ArrayList<>();
				for (HudModule m : enabled) {
					int c = m.defaultRx() < 0.3f ? 0 : m.defaultRx() > 0.7f ? 2 : 1;
					int h = m.defaultRy() < 0.5f ? 0 : 1;
					if (c == column && h == half) group.add(m);
				}
				group.sort(Comparator.comparingDouble(HudModule::defaultRy));
				if (half == 0) {
					float y = MARGIN;
					for (HudModule m : group) {
						m.setPosition(x(column, m, sw), y);
						y += m.scaledHeight() + GAP + (column == 1 ? 10 : 0);
					}
				} else {
					float y = sh - MARGIN - (column == 1 ? HOTBAR_CLEARANCE : 0);
					for (int i = group.size() - 1; i >= 0; i--) {
						HudModule m = group.get(i);
						y -= m.scaledHeight();
						m.setPosition(x(column, m, sw), y);
						y -= GAP;
					}
				}
			}
		}
	}

	private static float x(int column, HudModule m, float sw) {
		return switch (column) {
			case 0 -> MARGIN;
			case 2 -> sw - MARGIN - m.scaledWidth();
			default -> (sw - m.scaledWidth()) / 2f;
		};
	}
}
