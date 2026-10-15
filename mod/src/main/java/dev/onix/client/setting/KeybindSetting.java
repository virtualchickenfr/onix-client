package dev.onix.client.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * A key or mouse button that triggers an action. -1 means "not bound".
 * Keyboard keys are stored as their GLFW key code, mouse buttons as {@link #MOUSE} + button index.
 */
public final class KeybindSetting extends Setting<Integer, KeybindSetting> {
    public static final int NONE = -1;
    /** Mouse buttons are stored from here on: MOUSE + 2 is the middle button, MOUSE + 3 / + 4 the side buttons. */
    public static final int MOUSE = 1000;
    private boolean wasDown;

    public KeybindSetting(String key, String name, int defaultKey) {
        super(key, name, defaultKey);
    }

    public static int mouse(int button) {
        return MOUSE + button;
    }

    public boolean isBound() {
        return value != NONE;
    }

    public boolean isMouse() {
        return value >= MOUSE;
    }

    /** Whether this setting is bound to the given code (keyboard key or {@link #mouse} button). */
    public boolean matches(int code) {
        return isBound() && value == code;
    }

    /** True while the key is held and no screen is open. */
    public boolean isDown() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!isBound() || mc.currentScreen != null || mc.getWindow() == null) return false;
        return isPhysicallyDown(mc.getWindow().getHandle(), value);
    }

    /** True while the key or button is held, regardless of open screens. */
    public static boolean isPhysicallyDown(long window, int code) {
        if (code == NONE) return false;
        if (code >= MOUSE) return GLFW.glfwGetMouseButton(window, code - MOUSE) == GLFW.GLFW_PRESS;
        return InputUtil.isKeyPressed(window, code);
    }

    /** True exactly once per key press. Call once per tick. */
    public boolean poll() {
        boolean down = isDown();
        boolean pressed = down && !wasDown;
        wasDown = down;
        return pressed;
    }

    public String display() {
        return describe(value);
    }

    public static String describe(int code) {
        if (code == NONE) return "None";
        if (code >= MOUSE) {
            int button = code - MOUSE;
            return switch (button) {
                case 0 -> "Left Click";
                case 1 -> "Right Click";
                case 2 -> "Middle Click";
                default -> "Mouse " + (button + 1);
            };
        }
        return InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString();
    }

    /** The vanilla key object for this code, e.g. to compare with a KeyBinding. */
    public static InputUtil.Key toKey(int code) {
        if (code == NONE) return InputUtil.UNKNOWN_KEY;
        return code >= MOUSE ? InputUtil.Type.MOUSE.createFromCode(code - MOUSE) : InputUtil.Type.KEYSYM.createFromCode(code);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(value);
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isNumber()) set(json.getAsInt());
    }
}
