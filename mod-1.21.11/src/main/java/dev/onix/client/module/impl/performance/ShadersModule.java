package dev.onix.client.module.impl.performance;

import dev.onix.client.OnixClient;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * The switch for shaders. Off by default (more FPS): shaders run only when you turn them on – here, in Iris or with
 * the launcher's one-click shaders. Iris's own setting is what counts; this module shows it and switching it changes
 * it. Iris is called through its public API by reflection, so Onix runs the same with or without Iris.
 */
public class ShadersModule extends Module {
    /** What was last applied to Iris (null: not yet in this session). */
    private Boolean applied;
    private boolean wasInWorld;

    public ShadersModule() {
        super("shaders", "Shaders", "Turns the shader pack on and off (needs Iris; the launcher's one-click shaders add it).",
                Category.PERFORMANCE, false);
        tags("shaders", "iris", "graphics", "fps", "reflections");
    }

    public static boolean irisPresent() {
        return FabricLoader.getInstance().isModLoaded("iris") && FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT;
    }

    @Override
    public void init() {
        // applied on joining a world and whenever the switch changes (not during start-up, when Iris is not ready)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            boolean inWorld = client.level != null;
            if (inWorld && !wasInWorld) {
                if (!OnixClient.CONFIG.shadersOffSet.isOn()) {
                    // 1.1.22: shaders are no longer on automatically – switched off once for everyone
                    OnixClient.CONFIG.shadersOffSet.set(true);
                    OnixClient.CONFIG.save();
                    setEnabled(false);
                    apply(false);
                } else {
                    // joining a world: show what Iris runs (the launcher's one-click shaders turn them on there)
                    Boolean now = irisEnabled();
                    applied = now == null ? isEnabled() : now;
                    setEnabled(applied);
                }
            } else if (inWorld && applied != null && applied != isEnabled()) {
                apply(isEnabled());   // the switch was changed
            }
            wasInWorld = inWorld;
        });
    }

    /** Whether Iris runs shaders right now, or null without Iris. */
    private static Boolean irisEnabled() {
        if (!irisPresent()) return null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object config = api.getMethod("getConfig").invoke(instance);
            return (boolean) config.getClass().getMethod("areShadersEnabled").invoke(config);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return null;
        }
    }

    private void apply(boolean on) {
        applied = on;
        if (!irisPresent()) return;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = api.getMethod("getInstance").invoke(null);
            Object config = api.getMethod("getConfig").invoke(instance);
            boolean now = (boolean) config.getClass().getMethod("areShadersEnabled").invoke(config);
            if (now != on) config.getClass().getMethod("setShadersEnabledAndApply", boolean.class).invoke(config, on);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            OnixClient.LOGGER.warn("Could not switch shaders {}: {}", on ? "on" : "off", e.toString());
        }
    }
}
