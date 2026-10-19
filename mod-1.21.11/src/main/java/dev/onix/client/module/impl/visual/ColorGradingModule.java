package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

/** A colour look over the world: tint and vignette, from presets or your own colour. Drawn under the HUD. */
public class ColorGradingModule extends Module {
    private final ModeSetting preset = add(new ModeSetting("preset", "Look", "Cinematic", "Warm", "Cold", "Dreamy", "Night", "Sepia", "Custom"));
    private final ColorSetting tint = add(new ColorSetting("tint", "Tint", 0xFFFF9A4D).describe("Used by the Custom look."));
    private final NumberSetting strength = add(new NumberSetting("strength", "Strength", 0.5, 0, 1, 0.05));
    private final NumberSetting vignette = add(new NumberSetting("vignette", "Vignette", 0.45, 0, 1, 0.05));

    public ColorGradingModule() {
        super("colorgrading", "Color Grading", "Give the world a cinematic colour look with tint and vignette.", Category.VISUAL, false);
        tags("color", "filter", "vignette", "cinematic", "shader");
    }

    private int tintColor() {
        return switch (preset.get()) {
            case "Warm" -> 0xFFFF9A4D;
            case "Cold" -> 0xFF4DA6FF;
            case "Dreamy" -> 0xFFE58CFF;
            case "Night" -> 0xFF1B2A6B;
            case "Sepia" -> 0xFFB58A52;
            case "Cinematic" -> 0xFF2EC4B6;
            default -> tint.color();
        };
    }

    /** Called before the HUD is drawn. */
    public void render(GuiGraphics g, int w, int h) {
        float k = strength.getFloat();
        int c = tintColor() & 0xFFFFFF;
        int a = Math.round(k * (preset.is("Night") ? 0.55f : preset.is("Sepia") ? 0.42f : 0.26f) * 255);
        if (a > 0) g.fill(0, 0, w, h, a << 24 | c);
        if (preset.is("Cinematic")) {
            // teal shadows, warm highlights: a warm glow in the middle on top of the teal tint
            int warm = Math.round(k * 0.16f * 255);
            g.fillGradient(0, h / 4, w, h / 2, 0x00FFB070, warm << 24 | 0xFFB070);
            g.fillGradient(0, h / 2, w, h * 3 / 4, warm << 24 | 0xFFB070, 0x00FFB070);
            int bar = Math.round(h * 0.06f * k);
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
        }
        float v = vignette.getFloat();
        if (v > 0) {
            int va = Math.round(v * 0.85f * 255);
            int edgeH = Math.max(1, h / 3);
            int edgeW = Math.max(1, w / 4);
            int dark = va << 24;
            g.fillGradient(0, 0, w, edgeH, dark, 0);
            g.fillGradient(0, h - edgeH, w, h, 0, dark);
            // left and right edges: horizontal gradients drawn as thin strips
            int steps = 24;
            for (int i = 0; i < steps; i++) {
                float t = 1f - i / (float) steps;
                int alpha = Math.round(va * t * t);
                int x0 = edgeW * i / steps, x1 = edgeW * (i + 1) / steps;
                g.fill(x0, 0, x1, h, alpha << 24);
                g.fill(w - x1, 0, w - x0, h, alpha << 24);
            }
        }
    }
}
