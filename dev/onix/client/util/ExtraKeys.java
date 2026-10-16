package dev.onix.client.util;

import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.utility.ExtraKeybindsModule;

/** Routes raw key and mouse events to the Extra Keybinds module. */
public final class ExtraKeys {
    private ExtraKeys() {}

    /** @param code a GLFW key code, or {@link dev.onix.client.setting.KeybindSetting#mouse} for mouse buttons */
    public static void onInput(int code, int action) {
        if (OnixClient.MODULES == null) return;
        ExtraKeybindsModule module = OnixClient.MODULES.get(ExtraKeybindsModule.class);
        if (module != null && module.isEnabled()) module.onInput(code, action);
    }
}
