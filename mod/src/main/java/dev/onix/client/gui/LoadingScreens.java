package dev.onix.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.server.WorldGenerationProgressTracker;
import net.minecraft.client.gui.screen.MessageScreen;
import net.minecraft.client.gui.screen.ProgressScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.resource.ResourceReload;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    public static void splash(Object overlay, DrawContext ctx) {
        if (!LoadingAnimation.enabled()) return;
        ResourceReload reload = fieldOfType(overlay, ResourceReload.class);
        if (reload != currentReload) {
            currentReload = reload;
            completeAt = -1;
        }
        float progress = reload == null ? -1f : reload.getProgress();
        long now = System.currentTimeMillis();
        if (reload != null && reload.isComplete() && completeAt < 0) completeAt = now;
        // vanilla keeps the overlay for one second after loading and fades out during the next
        float alpha = completeAt < 0 ? 1f : 1f - Math.max(0f, Math.min(1f, (now - completeAt) / 1000f - 1f));
        if (alpha <= 0.01f) return;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 500);
        LoadingAnimation.render(ctx, null, null, completeAt >= 0 ? 1f : progress, alpha, false);
        ctx.getMatrices().pop();
    }

    // ------------------------------------------------------------------ world loading

    public static void levelLoading(Screen screen, DrawContext ctx) {
        WorldGenerationProgressTracker tracker = fieldOfType(screen, WorldGenerationProgressTracker.class);
        float progress = tracker == null ? -1f : tracker.getProgressPercentage() / 100f;
        String detail = tracker == null ? "Preparing spawn area" : "Preparing spawn area · " + Math.round(progress * 100) + "%";
        LoadingAnimation.render(ctx, "Loading world", detail, progress, 1f, true);
    }

    public static void terrain(Screen screen, DrawContext ctx) {
        LoadingAnimation.render(ctx, "Joining world", "Loading terrain", -1f, 1f, true);
    }

    public static void connecting(Screen screen, DrawContext ctx, int mouseX, int mouseY, float delta) {
        Text status = fieldOfType(screen, Text.class, true);
        String detail = status == null ? "Connecting to the server" : status.getString();
        LoadingAnimation.render(ctx, "Connecting", detail, -1f, 1f, true);
        renderWidgets(screen, ctx, mouseX, mouseY, delta);
    }

    /** Screens whose own text stays: only the background is replaced. */
    public static boolean replacesBackground(Screen screen) {
        return LoadingAnimation.enabled() && (screen instanceof ProgressScreen || screen instanceof MessageScreen);
    }

    public static void background(Screen screen, DrawContext ctx) {
        LoadingAnimation.backgroundOnly(ctx, -1f, 1f);
    }

    /** Buttons such as "Cancel" on the connect screen. */
    private static void renderWidgets(Screen screen, DrawContext ctx, int mouseX, int mouseY, float delta) {
        for (Element e : screen.children()) {
            if (e instanceof Drawable d) d.render(ctx, mouseX, mouseY, delta);
        }
    }
}
