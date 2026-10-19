package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.render.FxDraw;
import dev.onix.client.render.FxSubmit;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.joml.Matrix4f;

/** A soft glow with orbiting sparks around the item in your hand (first person). */
public class HandAuraModule extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Glow", "Sparks", "Glow + Sparks", "Rings"));
    private final ColorSetting color = add(ColorSetting.accent("color", "Color"));
    private final BoolSetting rainbow = add(new BoolSetting("rainbow", "Rainbow", false));
    private final NumberSetting size = add(new NumberSetting("size", "Size", 1.0, 0.4, 2.0, 0.05).unit("x"));
    private final NumberSetting intensity = add(new NumberSetting("intensity", "Intensity", 0.7, 0.1, 1.0, 0.05));
    private final BoolSetting offhand = add(new BoolSetting("offhand", "Off Hand Too", false));
    private final BoolSetting emptyHand = add(new BoolSetting("empty", "Empty Hand", false));

    public HandAuraModule() {
        super("handaura", "Hand Aura", "A glow and sparks around the item in your hand.", Category.VISUAL, false);
        tags("aura", "glow", "hand", "effect", "cosmetic");
    }

    public boolean shows(boolean mainHand, boolean empty) {
        return isEnabled() && (mainHand || offhand.isOn()) && (!empty || emptyHand.isOn());
    }

    /** Called with the pose at the held item (before it is drawn). */
    public void render(PoseStack matrices, SubmitNodeCollector collector) {
        float t = (System.nanoTime() / 1_000_000_000f);
        float s = size.getFloat();
        float k = intensity.getFloat();
        int base = rainbow.isOn() ? FxDraw.rainbow(t * 0.15f) : color.color();
        String st = style.get();
        matrices.pushPose();
        matrices.translate(0, 0.08f, 0);
        if (st.equals("Glow") || st.equals("Glow + Sparks")) {
            FxSubmit.submit(collector, matrices, RenderTypes.lightning(), (m, vc) -> {
                float pulse = 0.85f + 0.15f * (float) Math.sin(t * 3);
                // three crossed glow sheets read as a soft ball from any angle
                for (int i = 0; i < 3; i++) {
                    Matrix4f r = new Matrix4f(m).rotateY((float) (Math.PI / 3 * i + t * 0.6));
                    FxDraw.glow(r, vc, 0, 0, 0, 0.75f * s * pulse, FxDraw.withAlpha(base, 0.55f * k), FxDraw.withAlpha(base, 0f));
                }
            });
        }
        if (st.equals("Sparks") || st.equals("Glow + Sparks")) {
            FxSubmit.submit(collector, matrices, RenderTypes.lightning(), (m, vc) -> {
                for (int i = 0; i < 8; i++) {
                    float a = t * (1.2f + i * 0.13f) + i * 0.785f;
                    float r = 0.28f * s;
                    float y = 0.18f * s * (float) Math.sin(t * 1.7f + i);
                    Matrix4f p = new Matrix4f(m).translate((float) Math.cos(a) * r, y, (float) Math.sin(a) * r).rotateY(-a);
                    FxDraw.glow(p, vc, 0, 0, 0, 0.09f * s, FxDraw.withAlpha(0xFFFFFFFF, 0.9f * k), FxDraw.withAlpha(base, 0f));
                }
            });
        }
        if (st.equals("Rings")) {
            FxSubmit.submit(collector, matrices, RenderTypes.lightning(), (m, vc) -> {
                for (int i = 0; i < 3; i++) {
                    float phase = (t * 0.6f + i / 3f) % 1f;
                    float r = 0.1f * s + 0.35f * s * phase;
                    Matrix4f p = new Matrix4f(m).rotateX((float) Math.toRadians(70 + 20 * i)).rotateZ(t * (0.5f + i * 0.2f));
                    FxDraw.ring(p, vc, 0, 0, 0, r - 0.025f * s, r, FxDraw.withAlpha(base, 0f), FxDraw.withAlpha(base, (1 - phase) * 0.8f * k), 40);
                }
            });
        }
        matrices.popPose();
    }
}
