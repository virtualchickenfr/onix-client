package dev.onix.client.module.impl.hud;

import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/** A horizontal compass strip like in many shooters. */
public class CompassModule extends HudModule {
    private static final String[] NAMES = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    private final NumberSetting stripWidth = add(new NumberSetting("width", "Width", 160, 80, 320, 10).unit("px"));
    private final NumberSetting zoom = add(new NumberSetting("zoom", "Zoom", 1.4, 0.6, 3, 0.1).unit("x")
            .describe("Pixels per degree – higher shows fewer directions."));
    private final BoolSetting intercardinal = add(new BoolSetting("inter", "Show NE/SE/SW/NW", true));
    private final BoolSetting degrees = add(new BoolSetting("degrees", "Show Heading", false));
    private final BoolSetting fadeEdges = add(new BoolSetting("fadeEdges", "Fade Edges", true));
    private final ColorSetting textColor = add(new ColorSetting("textColor", "Text Color", 0xFFFFFFFF));
    private final ColorSetting northColor = add(new ColorSetting("northColor", "North Color", 0xFFFF5A5F));
    private final ColorSetting markerColor = add(ColorSetting.accent("markerColor", "Center Marker"));

    public CompassModule() {
        super("compass", "Compass", "A horizontal compass strip.", Category.HUD, false, Anchor.TOP_CENTER, 0, 4);
        tags("heading", "directions");
    }

    @Override
    public int getWidth() {
        return stripWidth.getInt();
    }

    @Override
    public int getHeight() {
        return 18 + (degrees.isOn() ? 10 : 0);
    }

    @Override
    public void render(DrawContext ctx, boolean preview) {
        int w = getWidth();
        drawBackground(ctx, w, getHeight());
        float yaw = mc().player == null ? 180f : MathHelper.wrapDegrees(mc().player.getYaw());
        float ppd = zoom.getFloat();
        int center = w / 2;
        for (int deg = -180; deg < 180; deg += 15) {
            float diff = MathHelper.wrapDegrees(deg - yaw);
            int x = Math.round(center + diff * ppd);
            if (x < 4 || x > w - 4) continue;
            float edge = fadeEdges.isOn() ? 1f - Math.abs(diff * ppd) / (w / 2f) : 1f;
            int alpha = (int) (255 * Math.max(0, Math.min(1, edge * 1.6f)));
            if (deg % 45 == 0) {
                String name = NAMES[Math.floorMod(deg / 45, 8)];
                if (name.length() == 2 && !intercardinal.isOn()) continue;
                int c = name.equals("N") ? northColor.color() : name.length() == 2 ? Theme.fade(textColor.color(), 0.6f) : textColor.color();
                drawText(ctx, name, x - textWidth(name) / 2 + 1, 5, Theme.withAlpha(c, Math.min(alpha, (c >>> 24) & 0xFF)));
            } else {
                ctx.fill(x, 8, x + 1, 11, Theme.withAlpha(textColor.color(), alpha / 3));
            }
        }
        ctx.fill(center, 0, center + 1, 3, markerColor.color());
        ctx.fill(center, 15, center + 1, 18, markerColor.color());
        if (degrees.isOn()) {
            String h = Math.round(Math.floorMod((int) yaw + 180, 360)) + "°";
            drawText(ctx, h, center - textWidth(h) / 2, 19, textColor.color());
        }
    }
}
