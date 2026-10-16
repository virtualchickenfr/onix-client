package gg.carbon.client.screen.menu;

/**
 * One main-menu design. A theme draws background and layout into a virtual canvas and reports
 * which entry is under the cursor. Themes are stateful (hover animations) but never perform
 * actions themselves — the host screen does that.
 */
public interface MenuTheme {
	String id();

	String title();

	String description();

	void render(MenuContext ctx);

	/** Index into {@link MenuContext#entries()} under the cursor, or -1. Uses the layout of the last frame. */
	int entryAt(float mx, float my);

	/** A theme-specific action under the cursor (e.g. a quick-join server card), or null. */
	default Runnable extraActionAt(float mx, float my) {
		return null;
	}

	/** Keyboard input; returns an entry index to activate, or -1. */
	default int keyPressed(int key) {
		return -1;
	}

	/** Mouse wheel; returns true when the theme used it. */
	default boolean mouseScrolled(double amount) {
		return false;
	}

	/** Whether this theme draws Minecraft's panorama (only possible full screen). */
	default boolean usesPanorama() {
		return false;
	}

	/** Whether the design has a light (white mode) variant. */
	default boolean supportsLight() {
		return true;
	}

	/** Blur the panorama like vanilla menus do. */
	default boolean blurPanorama() {
		return false;
	}

	/** Lay out in vanilla GUI units (no Carbon canvas scaling) — for the classic layout. */
	default boolean vanillaLayout() {
		return false;
	}
}
