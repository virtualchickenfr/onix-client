package dev.onix.client.module.impl.visual;

import dev.onix.client.OnixClient;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.ModeSetting;
import net.minecraft.resources.Identifier;

/**
 * Motion blur: every frame is blended with the frames before it, so fast turns smear smoothly. A post effect
 * (assets/onix/post_effect/motion_blur_*.json) run by MotionBlurMixin right after the world is drawn, before the HUD.
 */
public class MotionBlurModule extends Module {
    private final ModeSetting strength = add(new ModeSetting("strength", "Strength", "Medium", "Low", "Medium", "High"));

    public MotionBlurModule() {
        super("motionblur", "Motion Blur", "Smooth blur when you turn, like in PvP montages.", Category.VISUAL, false);
        tags("blur", "smooth", "montage", "cinematic");
    }

    /** The post effect to run this frame, or null. */
    public static Identifier effect() {
        if (OnixClient.MODULES == null) return null;
        MotionBlurModule m = OnixClient.MODULES.get(MotionBlurModule.class);
        if (m == null || !m.isEnabled()) return null;
        return Identifier.fromNamespaceAndPath("onix", "motion_blur_" + m.strength.get().toLowerCase(java.util.Locale.ROOT));
    }
}
