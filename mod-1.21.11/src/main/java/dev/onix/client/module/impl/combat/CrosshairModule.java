package dev.onix.client.module.impl.combat;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.HitResult;

/** Replaces the vanilla crosshair. Drawn from InGameHudMixin. */
public class CrosshairModule extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Cross", "Cross + Dot", "Dot", "Circle", "Corners", "T-Shape"));
    private final NumberSetting length = add(new NumberSetting("length", "Length", 4, 1, 12, 1).unit("px"));
    private final NumberSetting gap = add(new NumberSetting("gap", "Gap", 2, 0, 10, 1).unit("px"));
    private final NumberSetting thickness = add(new NumberSetting("thickness", "Thickness", 1, 1, 4, 1).unit("px"));
    private final BoolSetting dynamic = add(new BoolSetting("dynamic", "Dynamic Gap", false)
            .describe("Opens up while you move or your attack is recharging."));

    {
        group("Colors");
    }

    private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFFFFFF));
    private final BoolSetting targetColorOn = add(new BoolSetting("targetColorOn", "Color on Target", true));
    private final ColorSetting targetColor = add(new ColorSetting("targetColor", "Target Color", 0xFFFF5A5F).visibleIf(targetColorOn::isOn));
    private final BoolSetting outline = add(new BoolSetting("outline", "Outline", true));
    private final ColorSetting outlineColor = add(new ColorSetting("outlineColor", "Outline Color", 0xA0000000).visibleIf(outline::isOn));

    {
        group("Visibility");
    }

    private final BoolSetting thirdPerson = add(new BoolSetting("thirdPerson", "Show in Third Person", false));
    private final BoolSetting hideInMenus = add(new BoolSetting("hideMenus", "Hide in Menus", true));

    private boolean outlinePass;
    private float spread;

    public CrosshairModule() {
        super("crosshair", "Crosshair", "A custom crosshair with styles, colors and dynamic gap.", Category.COMBAT, false);
        tags("reticle", "aim");
    }

    public boolean showInThirdPerson() {
        return thirdPerson.isOn();
    }

    public boolean hideInMenus() {
        return hideInMenus.isOn();
    }

    public void render(GuiGraphics ctx, int cx, int cy) {
        float target = 0;
        if (dynamic.isOn() && mc().player != null) {
            var v = mc().player.getDeltaMovement();
            target += (float) Math.min(4, Math.sqrt(v.x * v.x + v.z * v.z) * 12);
            target += (1f - mc().player.getAttackStrengthScale(0f)) * 4f;
        }
        spread += (target - spread) * 0.25f;
        if (outline.isOn()) {
            outlinePass = true;
            shapes(ctx, cx, cy);
        }
        outlinePass = false;
        shapes(ctx, cx, cy);
    }

    private int currentColor() {
        boolean onTarget = mc().hitResult != null && mc().hitResult.getType() == HitResult.Type.ENTITY;
        return targetColorOn.isOn() && onTarget ? targetColor.color() : color.color();
    }

    private void shapes(GuiGraphics ctx, int cx, int cy) {
        int c = currentColor();
        int len = length.getInt();
        int g = gap.getInt() + Math.round(spread);
        int t = thickness.getInt();
        int lo = cx - (t - 1) / 2;
        int hi = lo + t;
        int loY = cy - (t - 1) / 2;
        int hiY = loY + t;
        String s = style.get();
        if (s.equals("Cross") || s.equals("Cross + Dot") || s.equals("T-Shape")) {
            if (!s.equals("T-Shape")) bar(ctx, lo, loY - g - len, hi, loY - g, c);
            bar(ctx, lo, hiY + g, hi, hiY + g + len, c);
            bar(ctx, lo - g - len, loY, lo - g, hiY, c);
            bar(ctx, hi + g, loY, hi + g + len, hiY, c);
        }
        if (s.equals("Dot") || s.equals("Cross + Dot")) bar(ctx, lo, loY, hi, hiY, c);
        if (s.equals("Circle")) {
            int r = g + len;
            for (int i = 0; i < 32; i++) {
                double a = i * Math.PI * 2 / 32;
                int x = cx + (int) Math.round(Math.cos(a) * r);
                int y = cy + (int) Math.round(Math.sin(a) * r);
                bar(ctx, x, y, x + t, y + t, c);
            }
        }
        if (s.equals("Corners")) {
            int l = lo - g - len, r = hi + g + len, top = loY - g - len, b = hiY + g + len;
            bar(ctx, l, top, l + len, top + t, c);
            bar(ctx, l, top, l + t, top + len, c);
            bar(ctx, r - len, top, r, top + t, c);
            bar(ctx, r - t, top, r, top + len, c);
            bar(ctx, l, b - t, l + len, b, c);
            bar(ctx, l, b - len, l + t, b, c);
            bar(ctx, r - len, b - t, r, b, c);
            bar(ctx, r - t, b - len, r, b, c);
        }
    }

    private void bar(GuiGraphics ctx, int x1, int y1, int x2, int y2, int c) {
        if (outlinePass) ctx.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, outlineColor.color());
        else ctx.fill(x1, y1, x2, y2, c);
    }
}
