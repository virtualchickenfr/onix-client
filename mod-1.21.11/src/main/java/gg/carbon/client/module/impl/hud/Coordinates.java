package gg.carbon.client.module.impl.hud;

import gg.carbon.client.module.HudModule;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.ModeSetting;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.HudStyle;
import gg.carbon.client.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;

public class Coordinates extends HudModule {
	private final ModeSetting layout = add(new ModeSetting("Layout", "Single line or stacked", "Vertical", "Vertical", "Horizontal"));
	private final BoolSetting direction = add(new BoolSetting("Direction", "Show the direction you are facing", true));
	private final BoolSetting biome = add(new BoolSetting("Biome", "Show the current biome", false));
	private final BoolSetting nether = add(new BoolSetting("Nether Coords", "Show converted overworld/nether coordinates", false));
	private final BoolSetting decimals = add(new BoolSetting("Decimals", "Show one decimal place", false));
	private final BoolSetting background = add(new BoolSetting("Background", "Draw a panel", true));
	private final NumberSetting bgOpacity = add(new NumberSetting("Background Opacity", "Panel transparency", 0.45, 0, 1, 0.05)).visibleIf(background::on);

	private static final String[] DIRS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
	private static final String[] AXIS = {"+Z", "-X +Z", "-X", "-X -Z", "-Z", "+X -Z", "+X", "+X +Z"};

	public Coordinates() {
		super("coordinates", "Coordinates", "Shows your position in the world", 0.005f, 0.2f);
	}

	private record Line(String label, String value) {}

	private List<Line> lines() {
		List<Line> l = new ArrayList<>();
		double x = 0, y = 64, z = 0;
		float yaw = 0;
		if (mc.player != null) {
			x = mc.player.getX();
			y = mc.player.getY();
			z = mc.player.getZ();
			yaw = mc.player.getYRot();
		}
		String fmt = decimals.on() ? "%.1f" : "%.0f";
		if (layout.is("Horizontal")) {
			l.add(new Line("XYZ", String.format(Locale.ROOT, fmt + "  " + fmt + "  " + fmt, x, y, z)));
		} else {
			l.add(new Line("X", String.format(Locale.ROOT, fmt, x)));
			l.add(new Line("Y", String.format(Locale.ROOT, fmt, y)));
			l.add(new Line("Z", String.format(Locale.ROOT, fmt, z)));
		}
		if (direction.on()) {
			int idx = Math.floorMod(Math.round(yaw / 45f), 8);
			l.add(new Line("DIR", DIRS[idx] + "  " + AXIS[idx]));
		}
		if (nether.on() && mc.level != null) {
			boolean inNether = mc.level.dimension().equals(net.minecraft.world.level.Level.NETHER);
			double f = inNether ? 8 : 0.125;
			l.add(new Line(inNether ? "OW" : "NETHER", String.format(Locale.ROOT, "%.0f  %.0f", x * f, z * f)));
		}
		if (biome.on() && mc.level != null && mc.player != null) {
			BlockPos pos = mc.player.blockPosition();
			String name = mc.level.getBiome(pos).unwrapKey().map(k -> k.identifier().getPath()).orElse("unknown");
			l.add(new Line("BIOME", pretty(name)));
		}
		return l;
	}

	private static String pretty(String id) {
		StringBuilder b = new StringBuilder();
		for (String part : id.split("_")) {
			if (part.isEmpty()) continue;
			if (!b.isEmpty()) b.append(' ');
			b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return b.toString();
	}

	private float labelColumn(List<Line> lines) {
		float w = 0;
		for (Line line : lines) w = Math.max(w, Fonts.width(line.label(), false, 0.8f));
		return w;
	}

	@Override
	public float width() {
		List<Line> lines = lines();
		float lc = labelColumn(lines);
		float vw = 0;
		for (Line line : lines) vw = Math.max(vw, Fonts.width(line.value(), true, 1f));
		return lc + 6 + Math.max(vw, 30) + (background.on() ? HudStyle.PAD_X * 2 : 0);
	}

	@Override
	public float height() {
		return lines().size() * 11 - 2 + (background.on() ? 10 : 0);
	}

	@Override
	public void render(GuiGraphics g, boolean editor) {
		List<Line> lines = lines();
		if (background.on()) HudStyle.panel(g, 0, 0, width(), height(), bgOpacity.f());
		float x = background.on() ? HudStyle.PAD_X : 0, y = background.on() ? 5 : 0;
		float lc = labelColumn(lines);
		for (Line line : lines) {
			Fonts.draw(g, line.label(), x, y + 1, Theme.TEXT_DIM, false, 0.8f, false);
			Fonts.draw(g, line.value(), x + lc + 6, y, Theme.WHITE, true, 1f, false);
			y += 11;
		}
	}
}
