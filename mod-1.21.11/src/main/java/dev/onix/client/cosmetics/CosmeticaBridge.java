package dev.onix.client.cosmetics;

import dev.onix.client.OnixClient;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Opens the Cosmetica mod's own menu (where you pick its free capes, hats and shoulder buddies) without a compile-time
 * dependency: Cosmetica registers its settings screen with Mod Menu, and that entry point is called by reflection.
 */
public final class CosmeticaBridge {
    private CosmeticaBridge() {}

    /** @return true if Cosmetica's menu was opened. */
    public static boolean openMenu(Screen parent) {
        try {
            for (EntrypointContainer<Object> c : FabricLoader.getInstance().getEntrypointContainers("modmenu", Object.class)) {
                if (!c.getProvider().getMetadata().getId().startsWith("cosmetica")) continue;
                Object api = c.getEntrypoint();
                Method get = api.getClass().getMethod("getModConfigScreenFactory");
                get.setAccessible(true);
                Object factory = get.invoke(api);
                if (factory == null) continue;
                Method create = null;
                for (Method m : factory.getClass().getMethods()) {
                    if (m.getName().equals("create") && m.getParameterCount() == 1) create = m;
                }
                if (create == null) continue;
                create.setAccessible(true);
                Object screen = create.invoke(factory, parent);
                if (screen instanceof Screen sc) {
                    Minecraft.getInstance().setScreen(sc);
                    return true;
                }
            }
        } catch (Throwable t) {
            OnixClient.LOGGER.warn("Could not open the Cosmetica menu", t);
        }
        return false;
    }
}
