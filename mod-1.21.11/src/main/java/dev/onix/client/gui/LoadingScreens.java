package dev.onix.client.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.server.packs.resources.ReloadInstance;

/**
 * Glue between vanilla loading screens and {@link LoadingAnimation}. Private vanilla fields
 * are found by their type (not their name), so this keeps working with obfuscated names.
 */
public final class LoadingScreens {
    private static final Map<String, Field> FIELDS = new ConcurrentHashMap<>();

    // resource-loading overlay state
    private static Object currentReload;
    private static long completeAt = -1;

    private LoadingScreens() {}

    private static <T> T fieldOfType(Object owner, Class<T> type) {
        return fieldOfType(owner, type, false);
    }

    /** @param mutableOnly skip final fields (e.g. the connect screen's status text is the only non-final Text) */
    @SuppressWarnings("unchecked")
    private static <T> T fieldOfType(Object owner, Class<T> type, boolean mutableOnly) {
        if (owner == null) return null;
        String key = owner.getClass().getName() + "#" + type.getName() + "#" + mutableOnly;
        Field f = FIELDS.computeIfAbsent(key, k -> {
            for (Class<?> c = owner.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !type.isAssignableFrom(field.getType())) continue;
                    if (mutableOnly && Modifier.isFinal(field.getModifiers())) continue;
                    try {
                        field.setAccessible(true);
                        return field;
                    } catch (RuntimeException ignored) {
                        return null;
                    }
                }
            }
            return null;
        });
        if (f == null) return null;
        try {
            return (T) f.get(owner);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ resource loading (SplashOverlay)

    /** Draws the Onix overlay on top of the vanilla splash. */
    public static void splash(Object overlay, GuiGraphics ctx) {
        if (!LoadingAnimation.enabled()) return;
        ReloadInstance reload = fieldOfType(overlay, ReloadInstance.class);
        if (reload != currentReload) {
            currentReload = reload;
            completeAt = -1;
        }
        float progress = reload == null ? -1f : reload.getActualProgress();
        long now = System.currentTimeMillis();
        if (reload != null && reload.isDone() && completeAt < 0) completeAt = now;
        // vanilla keeps the overlay for one second after loading and fades out during the next
        float alpha = completeAt < 0 ? 1f : 1f - Math.max(0f, Math.min(1f, (now - completeAt) / 1000f - 1f));
        if (alpha <= 0.01f) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(0, 0);
        LoadingAnimation.render(ctx, null, null, completeAt >= 0 ? 1f : progress, alpha, false);
        ctx.pose().popMatrix();
    }

    // ------------------------------------------------------------------ world loading

    public static void levelLoading(Screen screen, GuiGraphics ctx) {
        LevelLoadTracker tracker = fieldOfType(screen, LevelLoadTracker.class);
        float progress = tracker == null ? -1f : tracker.serverProgress();
        String detail = tracker == null ? "Preparing spawn area" : "Preparing spawn area · " + Math.round(progress * 100) + "%";
        LoadingAnimation.render(ctx, "Loading world", detail, progress, 1f, true);
    }

    public static void terrain(Screen screen, GuiGraphics ctx) {
        LoadingAnimation.render(ctx, "Joining world", "Loading terrain", -1f, 1f, true);
    }

    public static void connecting(Screen screen, GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        Component status = fieldOfType(screen, Component.class, true);
        String detail = status == null ? "Connecting to the server" : status.getString();
        LoadingAnimation.render(ctx, "Connecting", detail, -1f, 1f, true);
        renderWidgets(screen, ctx, mouseX, mouseY, delta);
    }

    /** Screens whose own text stays: only the background is replaced. */
    public static boolean replacesBackground(Screen screen) {
        return LoadingAnimation.enabled() && (screen instanceof ProgressScreen || screen instanceof GenericMessageScreen);
    }

    public static void background(Screen screen, GuiGraphics ctx) {
        LoadingAnimation.backgroundOnly(ctx, -1f, 1f);
    }

    /** Buttons such as "Cancel" on the connect screen. */
    private static void renderWidgets(Screen screen, GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        for (GuiEventListener e : screen.children()) {
            if (e instanceof Renderable d) d.render(ctx, mouseX, mouseY, delta);
        }
    }
}
