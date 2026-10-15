package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.entity.effect.StatusEffects;

/** Lower, smaller and more transparent fire overlay. Applied by InGameOverlayRendererMixin. */
public class LowFireModule extends Module {
    private final NumberSetting lower = add(new NumberSetting("lower", "Lower By", 30, 0, 60, 5).unit("%"));
    private final NumberSetting size = add(new NumberSetting("size", "Size", 100, 40, 100, 5).unit("%"));
    private final NumberSetting opacity = add(new NumberSetting("opacity", "Opacity", 100, 10, 100, 5).unit("%"));

    {
        group("Hide");
    }

    private final BoolSetting hideAlways = add(new BoolSetting("hideAlways", "Hide Completely", false));
    private final BoolSetting hideWithFireResistance = add(new BoolSetting("fireRes", "Hide With Fire Resistance", true)
            .describe("No fire overlay at all while fire resistance protects you.").visibleIf(() -> !hideAlways.isOn()));
    private final BoolSetting hideInCreative = add(new BoolSetting("hideCreative", "Hide in Creative", true).visibleIf(() -> !hideAlways.isOn()));

    public LowFireModule() {
        super("lowfire", "Low Fire", "Less fire covering your screen.", Category.VISUAL, false);
        tags("fire overlay", "burning");
    }

    public float offset() {
        return lower.getFloat() / 100f;
    }

    public float scale() {
        return size.getFloat() / 100f;
    }

    public float opacity() {
        return opacity.getFloat() / 100f;
    }

    public boolean hideCompletely() {
        if (hideAlways.isOn()) return true;
        if (mc().player == null) return false;
        if (hideInCreative.isOn() && mc().player.isCreative()) return true;
        return hideWithFireResistance.isOn() && mc().player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE);
    }
}
