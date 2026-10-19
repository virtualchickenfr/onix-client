package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.util.Mth;

/** How your first-person hand swings: style and speed. Only the animation changes, never the attack timing. */
public class SwingAnimationModule extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Smooth", "Vanilla", "Slash", "Stab", "Spin", "Swipe"));
    private final NumberSetting speed = add(new NumberSetting("speed", "Speed", 1.0, 0.3, 2.5, 0.05).unit("x").describe("Below 1 swings slower, above 1 faster."));
    private final NumberSetting strength = add(new NumberSetting("strength", "Strength", 1.0, 0.3, 2.0, 0.05).unit("x"));

    public SwingAnimationModule() {
        super("swinganimation", "Swing Animation", "Change how your hand swings in first person.", Category.VISUAL, false);
        tags("swing", "animation", "hand", "1.7", "blockhit");
    }

    /** Swing duration in ticks for the local player (vanilla 6). */
    public int duration(int vanilla) {
        return Math.max(1, Math.round(vanilla / speed.getFloat()));
    }

    /** Replaces vanilla's swing transform; returns false to keep vanilla. f = swing progress 0..1, side = 1 right / -1 left. */
    /** True when a custom swing replaces the vanilla one. */
    public boolean active() {
        return isEnabled() && !style.get().equals("Vanilla");
    }

    public boolean apply(PoseStack m, float f, int side) {
        String s = style.get();
        if (s.equals("Vanilla")) return false;
        float k = strength.getFloat();
        float pi = (float) Math.PI;
        switch (s) {
            case "Smooth" -> {
                float w = Mth.sin(f * pi);
                m.translate(side * -0.3f * w * k, 0.12f * Mth.sin(f * pi * 2) * k, -0.18f * w * k);
                m.mulPose(Axis.YP.rotationDegrees(side * 45f));
                m.mulPose(Axis.ZP.rotationDegrees(side * w * -14f * k));
                m.mulPose(Axis.XP.rotationDegrees(w * -60f * k));
                m.mulPose(Axis.YP.rotationDegrees(side * -45f));
            }
            case "Slash" -> {
                float w = Mth.sin(Mth.sqrt(f) * pi);
                m.translate(side * (0.1f - 0.35f * f) * k, 0.1f * w * k, -0.15f * w * k);
                m.mulPose(Axis.ZP.rotationDegrees(side * (50f - 110f * f) * k * w));
                m.mulPose(Axis.XP.rotationDegrees(w * -35f * k));
            }
            case "Stab" -> {
                float w = Mth.sin(f * pi);
                m.translate(side * -0.08f * w, 0.05f * w, -0.45f * w * k);
                m.mulPose(Axis.XP.rotationDegrees(w * -12f * k));
            }
            case "Spin" -> {
                float w = Mth.sin(f * pi);
                m.translate(0, 0.05f * w * k, -0.1f * w * k);
                m.mulPose(Axis.YP.rotationDegrees(side * 360f * f));
            }
            case "Swipe" -> {
                float w = Mth.sin(Mth.sqrt(f) * pi);
                m.translate(side * -0.5f * w * k, -0.1f * w * k, -0.1f * w * k);
                m.mulPose(Axis.YP.rotationDegrees(side * w * 70f * k));
                m.mulPose(Axis.ZP.rotationDegrees(side * w * -25f * k));
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
