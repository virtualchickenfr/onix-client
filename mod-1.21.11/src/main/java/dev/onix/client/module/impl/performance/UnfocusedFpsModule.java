package dev.onix.client.module.impl.performance;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.OptionOverride;
import net.minecraft.client.Options;
import org.lwjgl.glfw.GLFW;

/** Saves power by limiting the frame rate in the background, when minimized and in menus. */
public class UnfocusedFpsModule extends Module {
    private final NumberSetting fps = add(new NumberSetting("fps", "Background FPS", 20, 10, 120, 10).unit("fps")
            .describe("While another window is focused."));
    private final NumberSetting minimizedFps = add(new NumberSetting("minimizedFps", "Minimized FPS", 10, 10, 60, 10).unit("fps"));

    {
        group("Menus");
    }

    private final BoolSetting limitMenus = add(new BoolSetting("limitMenus", "Limit in Menus", false)
            .describe("Caps the frame rate while a screen like the inventory or the Esc menu is open."));
    private final NumberSetting menuFps = add(new NumberSetting("menuFps", "Menu FPS", 60, 30, 240, 10).unit("fps").visibleIf(limitMenus::isOn));

    private final OptionOverride<Integer> option = new OptionOverride<>(Options::framerateLimit);

    public UnfocusedFpsModule() {
        super("unfocusedfps", "Unfocused FPS", "Saves power by limiting FPS when you tab out.", Category.PERFORMANCE, true);
        tags("background", "power saving", "alt tab", "battery");
    }

    private boolean minimized() {
        if (mc().getWindow() == null) return false;
        return GLFW.glfwGetWindowAttrib(mc().getWindow().handle(), GLFW.GLFW_ICONIFIED) == GLFW.GLFW_TRUE;
    }

    @Override
    public void onTick() {
        Integer cap = null;
        if (minimized()) cap = minimizedFps.getInt();
        else if (!mc().isWindowActive()) cap = fps.getInt();
        else if (limitMenus.isOn() && mc().screen != null) cap = menuFps.getInt();

        if (cap == null) {
            option.restore();
            return;
        }
        Integer own = option.original();
        // never raise the limit above what the player chose
        int value = own != null ? Math.min(own, cap) : cap;
        option.apply(Math.max(10, value / 10 * 10));
    }

    @Override
    protected void onDisable() {
        option.restore();
    }

    @Override
    public void onShutdown() {
        option.restore();
    }
}
