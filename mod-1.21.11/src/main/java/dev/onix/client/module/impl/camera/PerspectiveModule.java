package dev.onix.client.module.impl.camera;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.ModeSetting;
import net.minecraft.client.CameraType;

/** A key for a quick look behind or at yourself, plus control over the vanilla F5 cycle. */
public class PerspectiveModule extends Module {
    private final KeybindSetting key = add(new KeybindSetting("key", "Key", KeybindSetting.NONE));
    private final ModeSetting mode = add(new ModeSetting("mode", "Activation", "Hold", "Toggle"));
    private final ModeSetting view = add(new ModeSetting("view", "View", "Behind (Back)", "In Front (Face)"));

    {
        group("F5 Cycle");
    }

    private final ModeSetting cycle = add(new ModeSetting("cycle", "F5 Cycle", "Vanilla", "First / Back", "First / Front")
            .describe("Changes which views the vanilla F5 key steps through."));

    private CameraType previous;
    private CameraType lastSeen;
    private boolean toggled;

    public PerspectiveModule() {
        super("perspective", "Perspective Key", "Quick look behind or at yourself, and a custom F5 cycle.", Category.CAMERA, false);
        tags("f5", "third person", "snaplook", "look behind");
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
        if (want && previous == null) {
            previous = mc().options.getCameraType();
            mc().options.setCameraType(view.is("Behind (Back)") ? CameraType.THIRD_PERSON_BACK : CameraType.THIRD_PERSON_FRONT);
        } else if (!want && previous != null) {
            mc().options.setCameraType(previous);
            previous = null;
        }
        if (previous == null) applyCycle();
        lastSeen = mc().options.getCameraType();
    }

    /** Skips views right after vanilla F5 switched to them. */
    private void applyCycle() {
        CameraType now = mc().options.getCameraType();
        if (now == lastSeen || cycle.is("Vanilla")) return;
        if (cycle.is("First / Back") && now == CameraType.THIRD_PERSON_FRONT) mc().options.setCameraType(CameraType.FIRST_PERSON);
        if (cycle.is("First / Front") && now == CameraType.THIRD_PERSON_BACK) mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
    }

    @Override
    protected void onDisable() {
        if (previous != null && mc().options != null) mc().options.setCameraType(previous);
        previous = null;
        toggled = false;
    }
}
