package dev.onix.client.module.impl.camera;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

/** OptiFine-style zoom. The FOV change is applied by GameRendererMixin. */
public class ZoomModule extends Module {
    private final KeybindSetting key = add(new KeybindSetting("key", "Zoom Key", GLFW.GLFW_KEY_C));
    private final ModeSetting mode = add(new ModeSetting("mode", "Activation", "Hold", "Toggle"));
    private final NumberSetting strength = add(new NumberSetting("strength", "Zoom Level", 4, 1.5, 15, 0.5).unit("x"));
    private final BoolSetting scroll = add(new BoolSetting("scroll", "Scroll to Adjust", true));
    private final NumberSetting maxZoom = add(new NumberSetting("max", "Max Scroll Zoom", 50, 5, 100, 5).unit("x").visibleIf(scroll::isOn));
    private final BoolSetting resetOnRelease = add(new BoolSetting("reset", "Reset Scroll on Release", true).visibleIf(scroll::isOn));

    {
        group("Feel");
    }

    private final BoolSetting smoothCamera = add(new BoolSetting("smooth", "Cinematic Camera", true));
    private final BoolSetting lowerSensitivity = add(new BoolSetting("lowerSens", "Lower Sensitivity", true)
            .describe("Reduces mouse sensitivity while zoomed, so aiming stays precise."));
    private final BoolSetting animate = add(new BoolSetting("animate", "Animation", true));
    private final NumberSetting speed = add(new NumberSetting("speed", "Animation Speed", 14, 4, 40, 1).visibleIf(animate::isOn));

    private boolean zooming;
    private boolean toggled;
    private boolean previousSmooth;
    private Double previousSensitivity;
    private double level;
    private double current = 1.0;
    private long lastFrame = System.nanoTime();

    public ZoomModule() {
        super("zoom", "Zoom", "Zoom in with a key; scroll to adjust.", Category.CAMERA, true);
        tags("optifine zoom", "spyglass", "magnify");
    }

    @Override
    public void onTick() {
        boolean pressed = key.poll();
        boolean want;
        if (mode.is("Toggle")) {
            if (pressed) toggled = !toggled;
            want = toggled;
        } else {
            want = key.isDown();
        }
        if (want && !zooming) start();
        else if (!want && zooming) stop();
        if (zooming && lowerSensitivity.isOn() && previousSensitivity != null) {
            double target = previousSensitivity * Math.max(0.15, 1.0 / Math.sqrt(level));
            mc().options.sensitivity().set(target);
        }
    }

    private void start() {
        zooming = true;
        if (level <= 0 || resetOnRelease.isOn()) level = strength.get();
        if (smoothCamera.isOn()) {
            previousSmooth = mc().options.smoothCamera;
            mc().options.smoothCamera = true;
        }
        if (lowerSensitivity.isOn()) previousSensitivity = mc().options.sensitivity().get();
    }

    private void stop() {
        zooming = false;
        if (smoothCamera.isOn() && mc().options != null) mc().options.smoothCamera = previousSmooth;
        if (previousSensitivity != null && mc().options != null) mc().options.sensitivity().set(previousSensitivity);
        previousSensitivity = null;
    }

    @Override
    protected void onDisable() {
        if (zooming) stop();
        toggled = false;
    }

    public boolean isZooming() {
        return isEnabled() && zooming;
    }

    /** From MouseMixin. Returns true if the scroll was used. */
    public boolean onScroll(double amount) {
        if (!isZooming() || !scroll.isOn() || amount == 0) return false;
        level = Math.max(1.2, Math.min(maxZoom.get(), amount > 0 ? level * 1.2 : level / 1.2));
        return true;
    }

    /** Divisor for the field of view. */
    public double fovDivisor() {
        long now = System.nanoTime();
        double seconds = Math.min(0.1, (now - lastFrame) / 1_000_000_000.0);
        lastFrame = now;
        double target = isZooming() ? level : 1.0;
        if (!animate.isOn()) {
            current = target;
        } else {
            current += (target - current) * (1.0 - Math.exp(-seconds * speed.get()));
            if (Math.abs(current - target) < 0.001) current = target;
        }
        return current;
    }
}
