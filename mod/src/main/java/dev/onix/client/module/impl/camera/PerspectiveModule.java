package dev.onix.client.module.impl.camera;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.ModeSetting;
import net.minecraft.client.option.Perspective;

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

    private Perspective previous;
    private Perspective lastSeen;
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
            previous = mc().options.getPerspective();
            mc().options.setPerspective(view.is("Behind (Back)") ? Perspective.THIRD_PERSON_BACK : Perspective.THIRD_PERSON_FRONT);
        } else if (!want && previous != null) {
            mc().options.setPerspective(previous);
            previous = null;
        }
        if (previous == null) applyCycle();
        lastSeen = mc().options.getPerspective();
    }

    /** Skips views right after vanilla F5 switched to them. */
    private void applyCycle() {
        Perspective now = mc().options.getPerspective();
        if (now == lastSeen || cycle.is("Vanilla")) return;
        if (cycle.is("First / Back") && now == Perspective.THIRD_PERSON_FRONT) mc().options.setPerspective(Perspective.FIRST_PERSON);
        if (cycle.is("First / Front") && now == Perspective.THIRD_PERSON_BACK) mc().options.setPerspective(Perspective.THIRD_PERSON_FRONT);
    }

    @Override
    protected void onDisable() {
        if (previous != null && mc().options != null) mc().options.setPerspective(previous);
        previous = null;
        toggled = false;
    }
}
