package gg.carbon.client.screen.menu;

/** Fixed indices of the shared main menu entries. */
public final class Entries {
	private Entries() {}

	public static final int SINGLEPLAYER = 0;
	public static final int MULTIPLAYER = 1;
	public static final int REALMS = 2;
	public static final int MODS = 3;
	public static final int OPTIONS = 4;
	public static final int LANGUAGE = 5;
	public static final int ACCESSIBILITY = 6;
	public static final int QUIT = 7;
	public static final int DESIGN = 8;
	public static final int COUNT = 9;

	/** Pixel icon per entry, by index. */
	public static final String[] ICONS = {"cube", "server", "cloud", "grid", "gear", "chat", "eye", "power", "hex"};
}
