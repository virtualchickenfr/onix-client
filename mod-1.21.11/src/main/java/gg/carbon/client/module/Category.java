package gg.carbon.client.module;

public enum Category {
	HUD("HUD", "On-screen displays"),
	PVP("PvP", "Combat utilities"),
	VISUAL("Visual", "Rendering & camera"),
	UTILITY("Utility", "Quality of life"),
	PERFORMANCE("Performance", "FPS & lag reduction");

	public final String title;
	public final String subtitle;

	Category(String title, String subtitle) {
		this.title = title;
		this.subtitle = subtitle;
	}
}
