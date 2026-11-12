package dev.onix.client.gui;

import dev.onix.client.OnixClient;
import java.lang.reflect.Constructor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * The Essential mod's own menus (friends, wardrobe, pictures), for Onix's title screen: Essential only adds its buttons
 * to the vanilla title screen, so with Onix's menu they would be missing. Opened by reflection – Essential is optional
 * and nothing here is needed without it.
 */
public final class EssentialLink {
    private EssentialLink() {}

    private static Boolean present;

    /** Essential is installed (checked once; its classes come from its own loader, so the class is what counts). */
    public static boolean present() {
        if (present == null) {
            try {
                Class.forName("gg.essential.api.EssentialAPI", false, EssentialLink.class.getClassLoader());
                present = true;
            } catch (ClassNotFoundException | LinkageError e) {
                present = false;
            }
        }
        return present;
    }

    public static void friends() {
        open("gg.essential.gui.friends.SocialMenu", "Friends");
    }

    public static void wardrobe() {
        open("gg.essential.gui.wardrobe.Wardrobe", "Wardrobe");
    }

    public static void pictures() {
        open("gg.essential.gui.screenshot.components.ScreenshotBrowser", "Pictures");
    }

    /**
     * Opens one of Essential's screens. They are Kotlin classes: a constructor without arguments, or Kotlin's
     * "all defaults" constructor (the real arguments, then an int bit mask and a marker) with every bit set.
     */
    private static void open(String className, String what) {
        try {
            Class<?> type = Class.forName(className, true, EssentialLink.class.getClassLoader());
            Object screen = null;
            for (Constructor<?> c : type.getDeclaredConstructors()) {
                Class<?>[] p = c.getParameterTypes();
                if (p.length == 0) {
                    c.setAccessible(true);
                    screen = c.newInstance();
                    break;
                }
            }
            if (screen == null) {
                for (Constructor<?> c : type.getDeclaredConstructors()) {
                    Class<?>[] p = c.getParameterTypes();
                    if (p.length >= 2 && p[p.length - 2] == int.class && p[p.length - 1].getName().equals("kotlin.jvm.internal.DefaultConstructorMarker")) {
                        Object[] args = new Object[p.length];
                        for (int i = 0; i < p.length - 2; i++) args[i] = defaultValue(p[i]);
                        args[p.length - 2] = -1;   // every argument uses its default
                        c.setAccessible(true);
                        screen = c.newInstance(args);
                        break;
                    }
                }
            }
            if (screen instanceof Screen s) Minecraft.getInstance().setScreen(s);
            else Notifications.show("Essential", what + " could not be opened");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            OnixClient.LOGGER.warn("Could not open Essential's {}", what, e);
            Notifications.show("Essential", what + " could not be opened");
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return 0;
    }
}
