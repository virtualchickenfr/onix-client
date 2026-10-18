package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.MouseMotion;
import gg.carbon.client.ui.R2D;
import net.minecraft.client.gui.GuiGraphics;

/** From Carbon: a pad with a dot that follows your mouse movement. */
public class MousestrokesModule extends HudModule {
    private final NumberSetting size = add(new NumberSetting("size", "Size", 40, 24, 80, 1).unit("px"));
    private final NumberSetting sensitivity = add(new NumberSetting("sensitivity", "Sensitivity", 1.0, 0.2, 3, 0.1).unit("x")
            .describe("How far the dot travels."));
    private final ColorSetting dotColor = add(new ColorSetting("dotColor", "Dot Color", 0xFFFFFFFF));
    private float dx, dy;
    private long last = System.nanoTime();

    public MousestrokesModule() {
        super("mousestrokes", "Mousestrokes", "Shows your mouse movement as a dot on a pad (from Carbon).", Category.HUD, false, Anchor.BOTTOM_LEFT, 4, -80);
        tags("mouse", "aim", "carbon");
    }

    @Override
    public int getWidth() {
        return size.getInt();
    }

    @Override
    public int getHeight() {
        return size.getInt();
    }

    @Override
    public void render(GuiGraphics ctx, boolean preview) {
        int s = size.getInt();
        drawBackground(ctx, s, s);
        R2D.rect(ctx, s / 2f - 0.25f, 4, 0.5f, s - 8, 0x22FFFFFF);
        R2D.rect(ctx, 4, s / 2f - 0.25f, s - 8, 0.5f, 0x22FFFFFF);

        long now = System.nanoTime();
        float dt = Math.min((now - last) / 1e9f, 0.1f);
        last = now;
        float k = 1 - (float) Math.exp(-18 * dt);
        float range = s / 2f - 5;
        float tx = (float) Math.max(-range, Math.min(range, MouseMotion.dx() * 0.35 * sensitivity.getFloat()));
        float ty = (float) Math.max(-range, Math.min(range, MouseMotion.dy() * 0.35 * sensitivity.getFloat()));
        dx += (tx - dx) * k;
        dy += (ty - dy) * k;

        float cx = s / 2f + dx, cy = s / 2f + dy;
        int dot = dotColor.color();
        R2D.shadow(ctx, cx - 3, cy - 3, 6, 6, 3, 3, (dot & 0x00FFFFFF) | 0x66000000);
        R2D.circle(ctx, cx, cy, 2.5f, dot);
    }
}
