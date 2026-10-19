package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/** Press a key to show off the item in your hand: it is lifted, turned and put back. */
public class WeaponInspectModule extends Module {
    private final KeybindSetting key = add(new KeybindSetting("key", "Inspect Key", GLFW.GLFW_KEY_Y));
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Turn", "Flip", "Spin"));
    private final NumberSetting duration = add(new NumberSetting("duration", "Duration", 1.8, 0.8, 4, 0.1).unit("s"));

    private long start = -1;

    public WeaponInspectModule() {
        super("weaponinspect", "Weapon Inspect", "Press a key to inspect the item in your hand.", Category.VISUAL, false);
        tags("inspect", "animation", "hand", "cs");
    }

    @Override
    public void onTick() {
        if (key.poll()) start = System.currentTimeMillis();
    }

    /** The animation's progress 0..1, or -1 while not inspecting. */
    private float progress() {
        if (start < 0) return -1;
        float p = (System.currentTimeMillis() - start) / (float) (duration.get() * 1000);
        if (p >= 1) {
            start = -1;
            return -1;
        }
        return p;
    }

    public boolean active() {
        return isEnabled() && start >= 0;
    }

    /** Applied to the main hand before vanilla's own transforms. */
    public void apply(PoseStack m, int side) {
        float p = progress();
        if (p < 0) return;
        // ease in to the pose, hold, ease out
        float in = Mth.clamp(p / 0.2f, 0, 1);
        float out = Mth.clamp((1 - p) / 0.2f, 0, 1);
        float w = smooth(Math.min(in, out));
        float turn = smooth(Mth.clamp((p - 0.15f) / 0.7f, 0, 1));
        m.translate(side * -0.22f * w, 0.12f * w, 0.05f * w);
        switch (style.get()) {
            case "Flip" -> m.mulPose(Axis.XP.rotationDegrees(360f * turn));
            case "Spin" -> m.mulPose(Axis.ZP.rotationDegrees(side * 360f * turn));
            default -> {
                m.mulPose(Axis.YP.rotationDegrees(side * (-35f * w + 180f * turn)));
                m.mulPose(Axis.ZP.rotationDegrees(side * 20f * w));
            }
        }
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }
}
