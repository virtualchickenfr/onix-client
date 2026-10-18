package dev.onix.client.module.impl.combat;

import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.module.OverlayRenderer;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;

/** Flashes the screen when you take damage, so you always notice hits. */
public class DamageFlashModule extends Module implements OverlayRenderer {
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Edges", "Full Screen"));
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFF3B3B));
    private final NumberSetting intensity = add(new NumberSetting("intensity", "Intensity", 40, 5, 100, 5).unit("%"));
    private final NumberSetting duration = add(new NumberSetting("duration", "Duration", 300, 100, 1000, 50).unit("ms"));

    private long hitTime;
    private int lastHurt;

    public DamageFlashModule() {
        super("damageflash", "Damage Flash", "Screen flash when you take damage.", Category.COMBAT, false);
        tags("hit indicator", "hurt");
    }

    @Override
    public void onTick() {
        if (mc().player == null) return;
        int hurt = mc().player.hurtTime;
        if (hurt > lastHurt) hitTime = System.currentTimeMillis();
        lastHurt = hurt;
    }

    @Override
    public void renderOverlay(GuiGraphics ctx) {
        float age = (System.currentTimeMillis() - hitTime) / duration.getFloat();
        if (age >= 1) return;
        float a = (1 - age) * intensity.getFloat() / 100f;
        if (style.is("Full Screen")) {
            ctx.fill(0, 0, ctx.guiWidth(), ctx.guiHeight(), Theme.fade(color.color(), a));
        } else {
            Theme.vignette(ctx, color.color(), a, 0.25f);
        }
    }
}
