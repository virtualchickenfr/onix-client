package gg.carbon.client.screen.menu;

import gg.carbon.client.screen.widget.Ui;

/** Text and line colours for the menu designs, in dark or light mode. */
public final class Ink {
	private Ink() {}

	public static int strong() {
		return Ui.light ? 0xFF141417 : 0xFFF4F4F6;
	}

	public static int normal() {
		return Ui.light ? 0xFF2E2E35 : 0xFFD2D2D8;
	}

	public static int dim() {
		return Ui.light ? 0xFF6A6A74 : 0xFF8E8E98;
	}

	public static int faint() {
		return Ui.light ? 0xFF9C9CA4 : 0xFF5F5F68;
	}

	public static int line() {
		return Ui.light ? 0x1C000000 : 0x16FFFFFF;
	}

	public static int panel() {
		return Ui.light ? 0xE6FFFFFF : 0xC80B0B0D;
	}

	public static int panelEdge() {
		return Ui.light ? 0x1E000000 : 0x24FFFFFF;
	}

	public static int accent() {
		return Ui.light ? 0xFF141417 : 0xFFEDEDF0;
	}
}
