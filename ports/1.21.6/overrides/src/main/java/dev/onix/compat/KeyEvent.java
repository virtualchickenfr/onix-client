package dev.onix.compat;

import org.lwjgl.glfw.GLFW;

/** Minecraft 1.21.8 and older: stand-in for 1.21.9's net.minecraft.client.input.KeyEvent. */
public record KeyEvent(int key, int scancode, int modifiers) {
	public boolean isEscape() {
		return key == GLFW.GLFW_KEY_ESCAPE;
	}

	public boolean isDown() {
		return key == GLFW.GLFW_KEY_DOWN;
	}

	public boolean isUp() {
		return key == GLFW.GLFW_KEY_UP;
	}

	public boolean isLeft() {
		return key == GLFW.GLFW_KEY_LEFT;
	}

	public boolean isRight() {
		return key == GLFW.GLFW_KEY_RIGHT;
	}

	public boolean isConfirmation() {
		return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_KP_ENTER;
	}

	public boolean hasControlDown() {
		return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
	}

	public boolean hasShiftDown() {
		return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
	}
}
