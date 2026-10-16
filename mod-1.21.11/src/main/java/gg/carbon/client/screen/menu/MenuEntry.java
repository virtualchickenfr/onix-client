package gg.carbon.client.screen.menu;

/**
 * A main menu action.
 *
 * @param primary  rendered as the highlighted call to action
 * @param instant  opens without the fade-out (overlays such as the mod menu)
 */
public record MenuEntry(String label, String icon, boolean primary, boolean instant, Runnable action) {}
