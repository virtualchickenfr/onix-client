package dev.onix.client.module.impl.camera;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.NumberSetting;
import org.lwjgl.glfw.GLFW;

/** Third-person camera: distance, smoothing, wall clipping and an over-the-shoulder offset. Applied by CameraMixin. */
public class CameraTweaksModule extends Module {
    private final NumberSetting distance = add(new NumberSetting("distance", "Third Person Distance", 4, 1, 16, 0.5).unit("blocks"));
    private final BoolSetting smooth = add(new BoolSetting("smooth", "Smooth Distance Changes", true));
    private final BoolSetting noClip = add(new BoolSetting("noClip", "Camera Through Walls", true)
            .describe("The third-person camera no longer zooms in when a wall is behind you."));

    {
        group("Offset");
    }

    private final NumberSetting shoulder = add(new NumberSetting("shoulder", "Shoulder Offset", 0, -2, 2, 0.1).unit("blocks")
            .describe("Moves the camera sideways for an over-the-shoulder view."));
    private final NumberSetting height = add(new NumberSetting("height", "Height Offset", 0, -1, 1, 0.05).unit("blocks"));

    {
        group("Scroll");
    }

    private final BoolSetting scrollAdjust = add(new BoolSetting("scrollAdjust", "Scroll to Change Distance", false)
            .describe("Hold the key below and scroll in third person."));
    private final KeybindSetting scrollKey = add(new KeybindSetting("scrollKey", "Hold Key", GLFW.GLFW_KEY_LEFT_CONTROL).visibleIf(scrollAdjust::isOn));

    private float current = 4f;
    private long lastNanos;

    public CameraTweaksModule() {
        super("cameratweaks", "Camera Tweaks", "Third-person distance, clipping and shoulder view.", Category.CAMERA, false);
        restricted("A third-person camera that clips through walls lets you look around corners – not allowed everywhere.");
        tags("camera distance", "clip", "f5 distance", "shoulder", "over the shoulder");
    }

    /** Multiplier for vanilla's 4-block distance; smoothed per frame. */
    public float distanceFactor() {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 1f : Math.min(0.25f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        float target = distance.getFloat();
        current = smooth.isOn() ? current + (target - current) * Math.min(1f, dt * 10f) : target;
        return current / 4f;
    }

    public boolean noClip() {
        return noClip.isOn();
    }

    public float shoulder() {
        return shoulder.getFloat();
    }

    public float height() {
        return height.getFloat();
    }

    /** @return true if the scroll was used to change the distance. */
    public boolean onScroll(double amount) {
        if (!isEnabled() || !scrollAdjust.isOn() || amount == 0 || !scrollKey.isDown()) return false;
        if (mc().options.getCameraType().isFirstPerson()) return false;
        distance.set(distance.get() - Math.signum(amount) * 0.5);
        return true;
    }
}
