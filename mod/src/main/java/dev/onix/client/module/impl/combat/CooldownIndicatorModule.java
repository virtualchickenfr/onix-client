package dev.onix.client.module.impl.combat;

import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/** Attack cooldown as a bar or percentage – hit at 100% for full damage. */
public class CooldownIndicatorModule extends HudModule {
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Bar", "Percent", "Bar + Percent"));
    private final NumberSetting barWidth = add(new NumberSetting("barWidth", "Bar Width", 30, 10, 120, 2).unit("px"));
    private final NumberSetting barHeight = add(new NumberSetting("barHeight", "Bar Height", 2, 1, 6, 1).unit("px"));
    private final BoolSetting hideWhenReady = add(new BoolSetting("hideReady", "Hide When Ready", true));
    private final ColorSetting chargingColor = add(new ColorSetting("charging", "Charging Color", 0xFFA1A1AA));
    private final ColorSetting readyColor = add(ColorSetting.accent("ready", "Ready Color"));
    private final ColorSetting trackColor = add(new ColorSetting("track", "Track Color", 0x40FFFFFF));

    public CooldownIndicatorModule() {
        super("cooldown", "Cooldown Indicator", "Shows your attack cooldown under the crosshair.", Category.COMBAT, false, Anchor.CENTER, 0, 12);
        tags("attack indicator", "1.9 pvp", "charge");
        background.withDefault(false);
        padding.withDefault(0);
    }

    private float progress() {
        return mc().player == null ? 1 : mc().player.getAttackCooldownProgress(0f);
    }

    @Override
    public boolean hasContent() {
        return !hideWhenReady.isOn() || progress() < 1f;
    }

    private boolean bar() {
        return !style.is("Percent");
    }

    private boolean text() {
        return !style.is("Bar");
    }

    @Override
    public int getWidth() {
        return pad() * 2 + Math.max(bar() ? barWidth.getInt() : 0, text() ? textWidth("100%") : 0);
    }

    @Override
    public int getHeight() {
        return pad() * 2 + (bar() ? barHeight.getInt() : 0) + (text() ? 10 : 0) + (bar() && text() ? 2 : 0);
    }

    @Override
    public void render(DrawContext ctx, boolean preview) {
        float p = preview ? 0.6f : progress();
        int w = getWidth();
        drawBackground(ctx, w, getHeight());
        int color = p >= 1f ? readyColor.color() : chargingColor.color();
        int y = pad();
        if (bar()) {
            int bw = barWidth.getInt();
            int x = (w - bw) / 2;
            Theme.round(ctx, x, y, x + bw, y + barHeight.getInt(), 1, trackColor.color());
            Theme.round(ctx, x, y, x + Math.max(1, Math.round(bw * p)), y + barHeight.getInt(), 1, color);
            y += barHeight.getInt() + 2;
        }
        if (text()) {
            String s = String.format(Locale.ROOT, "%d%%", Math.round(p * 100));
            drawText(ctx, s, (w - textWidth(s)) / 2, y, color);
        }
    }
}
