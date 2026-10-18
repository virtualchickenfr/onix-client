package dev.onix.client.module.impl.utility;

import dev.onix.client.OnixClient;
import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Turns every module off at once (default key: End), and can bring them back with a second press. */
public class PanicModule extends Module {
    private final BoolSetting restore = add(new BoolSetting("restore", "Second Press Restores", true)
            .describe("Pressing the key again turns the same modules back on."));
    private final BoolSetting hideHud = add(new BoolSetting("hideHud", "Also Hide the HUD (F1)", false));
    private final BoolSetting closeScreen = add(new BoolSetting("closeScreen", "Close Open Menus", true));

    {
        group("Keep On");
    }

    private final BoolSetting keepHud = add(new BoolSetting("keepHud", "HUD Modules", false));
    private final BoolSetting keepVisual = add(new BoolSetting("keepVisual", "Visual Modules", false));
    private final BoolSetting keepFavorites = add(new BoolSetting("keepFavorites", "Favorites", false));
    private final BoolSetting keepPerformance = add(new BoolSetting("keepPerformance", "Performance Modules", true));

    private final List<Module> disabled = new ArrayList<>();

    public PanicModule() {
        super("panic", "Panic", "Turn every module off instantly with one key.", Category.UTILITY, false);
        tags("disable all", "kill switch");
        toggleKey.withDefault(GLFW.GLFW_KEY_END);
        toggleNotify.withDefault(false);
    }

    private boolean keep(Module m) {
        if (keepHud.isOn() && m instanceof HudModule) return true;
        if (keepVisual.isOn() && m.getCategory() == Category.VISUAL) return true;
        if (keepPerformance.isOn() && m.getCategory() == Category.PERFORMANCE) return true;
        return keepFavorites.isOn() && OnixClient.CONFIG.favorites.contains(m.getId());
    }

    @Override
    protected void onEnable() {
        if (restore.isOn() && !disabled.isEmpty()) {
            for (Module m : disabled) m.setEnabled(true);
            Notifications.show("Panic", disabled.size() + " modules restored");
            disabled.clear();
            if (hideHud.isOn() && mc().options != null) mc().options.hideGui = false;
        } else {
            disabled.clear();
            for (Module m : OnixClient.MODULES.all()) {
                if (m == this || !m.isEnabled() || keep(m)) continue;
                m.setEnabled(false);
                disabled.add(m);
            }
            if (hideHud.isOn() && mc().options != null) mc().options.hideGui = true;
            if (closeScreen.isOn() && mc().screen != null) mc().setScreen(null);
            Notifications.show("Panic", disabled.size() + " modules turned off");
        }
        setEnabled(false);
        OnixClient.save();
    }
}
