package dev.onix.client.module.impl.camera;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.CameraType;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Look around without turning your character (perspective mod).
 * EntityMixin redirects mouse movement here, CameraMixin applies the angles.
 */
public class FreelookModule extends Module {
    private final KeybindSetting key = add(new KeybindSetting("key", "Freelook Key", GLFW.GLFW_KEY_LEFT_ALT));
    private final ModeSetting mode = add(new ModeSetting("mode", "Activation", "Hold", "Toggle"));
    private final ModeSetting view = add(new ModeSetting("view", "Camera", "Third Person", "First Person"));
    private final NumberSetting sensitivity = add(new NumberSetting("sensitivity", "Sensitivity", 100, 20, 200, 5).unit("%"));
    private final BoolSetting invertY = add(new BoolSetting("invertY", "Invert Vertical", false));
    private final BoolSetting clampPitch = add(new BoolSetting("clamp", "Limit Up/Down", true));

    private boolean active;
    private boolean toggled;
    private float yaw;
    private float pitch;
    private CameraType previous;

    public FreelookModule() {
        super("freelook", "Freelook", "Look around your character while moving straight.", Category.CAMERA, false);
        restricted("Looking around while moving straight is blocked on some servers (e.g. Hypixel) and in many tournaments.");
        tags("perspective", "360", "look around");
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
        if (want && !active) start();
        else if (!want && active) stop();
    }

    private void start() {
        if (mc().player == null) return;
        active = true;
        yaw = mc().player.getYRot();
        pitch = mc().player.getXRot();
        previous = mc().options.getCameraType();
        if (view.is("Third Person")) mc().options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private void stop() {
        active = false;
        if (previous != null && mc().options != null) mc().options.setCameraType(previous);
        previous = null;
    }

    @Override
    protected void onDisable() {
        if (active) stop();
        toggled = false;
    }

    public boolean isActive() {
        return isEnabled() && active;
    }

    /** Mouse movement while active (same scale as Entity#changeLookDirection). */
    public void look(double dx, double dy) {
        double s = sensitivity.get() / 100.0 * 0.15;
        yaw += (float) (dx * s);
        pitch += (float) ((invertY.isOn() ? -dy : dy) * s);
        if (clampPitch.isOn()) pitch = Mth.clamp(pitch, -90f, 90f);
    }

    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
}
