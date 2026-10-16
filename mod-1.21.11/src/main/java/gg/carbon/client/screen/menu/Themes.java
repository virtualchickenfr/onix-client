package gg.carbon.client.screen.menu;

import java.util.List;

/** Registry of main menu designs, in picker order. */
public final class Themes {
	private Themes() {}

	public static final List<String> IDS = List.of("Carbon", "Carbon Client", "Voxel", "Portal", "Classic", "Vanilla");

	public static MenuTheme create(String id) {
		return switch (id) {
			case "Carbon Client" -> new CarbonTheme(true);
			case "Voxel" -> new VoxelTheme();
			case "Portal" -> new PortalTheme();
			case "Classic" -> new ClassicTheme();
			case "Vanilla" -> new VanillaTheme();
			default -> new CarbonTheme();
		};
	}

	public static int index(String id) {
		return Math.max(0, IDS.indexOf(id));
	}
}
