package dev.onix.client.module.impl.camera;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import net.minecraft.client.CameraType;

/** From Carbon: switches to third person while gliding or riding. */
public class AutoPerspectiveModule extends Module {
    private final BoolSetting elytra = add(new BoolSetting("elytra", "Elytra", true).describe("Third person while gliding."));
    private final BoolSetting riding = add(new BoolSetting("riding", "Riding", false).describe("Third person while riding."));
    private CameraType previous;

    public AutoPerspectiveModule() {
        super("autoperspective", "Auto Perspective", "Third person while flying or riding (from Carbon).", Category.CAMERA, false);
        tags("elytra", "third person", "carbon");
    }

    @Override
    public void onTick() {
        if (mc().player == null) return;
        boolean want = (elytra.isOn() && mc().player.isFallFlying()) || (riding.isOn() && mc().player.isPassenger());
        if (want && previous == null) {
            previous = mc().options.getCameraType();
            mc().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!want && previous != null) {
            mc().options.setCameraType(previous);
            previous = null;
        }
    }

    @Override
    protected void onDisable() {
        if (previous != null) mc().options.setCameraType(previous);
        previous = null;
    }
}
