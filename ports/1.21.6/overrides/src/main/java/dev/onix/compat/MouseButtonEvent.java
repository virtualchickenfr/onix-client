package dev.onix.compat;

/** Minecraft 1.21.8 and older: stand-in for 1.21.9's net.minecraft.client.input.MouseButtonEvent. */
public record MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo) {
	public MouseButtonEvent(double x, double y, int button) {
		this(x, y, new MouseButtonInfo(button, 0));
	}

	public int button() {
		return buttonInfo.button();
	}

	public int modifiers() {
		return buttonInfo.modifiers();
	}
}
