package dev.onix.client.module.impl.performance;

import dev.onix.client.OnixClient;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * The master switch for shaders: Iris only runs its shader pack while this module is on. The launcher installs Iris
 * and a pack only on PCs with a strong graphics card, so on weak PCs and phones this switch does nothing.
 * Iris is called through its public API by reflection, so Onix runs the same with or without Iris.
 */
public class ShadersModule extends Module {
    /** What was last applied to Iris (null: not yet in this session). */
    private Boolean applied;
    private boolean wasInWorld;

    public ShadersModule() {
        super("shaders", "Shaders", "Turns the shader pack on and off (needs Iris, which the launcher adds on strong PCs).",
                Category.PERFORMANCE, true);
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
            if (inWorld && (!wasInWorld || applied == null || applied != isEnabled())) apply(isEnabled());
            wasInWorld = inWorld;
        });
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
