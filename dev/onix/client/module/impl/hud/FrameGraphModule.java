package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.FrameStats;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;

/** A small live graph of frame times – spikes show stutters at a glance. */
public class FrameGraphModule extends HudModule {
    private final NumberSetting graphWidth = add(new NumberSetting("width", "Width", 100, 40, 240, 4).unit("px"));
    private final NumberSetting graphHeight = add(new NumberSetting("height", "Height", 30, 12, 80, 2).unit("px"));
    private final NumberSetting maxMs = add(new NumberSetting("maxMs", "Scale Top", 50, 10, 200, 5).unit("ms")
            .describe("Frame time at the top edge of the graph."));
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Bars", "Line"));
    private final ColorSetting graphColor = add(ColorSetting.accent("graphColor", "Graph Color"));
    private final BoolSetting highlightSpikes = add(new BoolSetting("spikes", "Highlight Spikes", true));
    private final ColorSetting spikeColor = add(new ColorSetting("spikeColor", "Spike Color", 0xFFFF5A5F).visibleIf(highlightSpikes::isOn));
    private final NumberSetting spikeMs = add(new NumberSetting("spikeMs", "Spike From", 33, 10, 100, 1).unit("ms").visibleIf(highlightSpikes::isOn));
    private final BoolSetting showText = add(new BoolSetting("text", "Show Frame Time", true));
    private final BoolSetting targetLine = add(new BoolSetting("target", "60 FPS Line", true));

    public FrameGraphModule() {
        super("framegraph", "Frame Graph", "Live graph of frame times to spot stutters.", Category.HUD, false, Anchor.TOP_LEFT, 4, 60);
        tags("lag", "stutter", "frametime", "performance graph");
    }

    @Override
    public int getWidth() {
        return graphWidth.getInt() + pad() * 2;
    }

    @Override
    public int getHeight() {
        return graphHeight.getInt() + pad() * 2 + (showText.isOn() ? 11 : 0);
    }

    @Override
    public void render(GuiGraphics ctx, boolean preview) {
        int w = getWidth();
        int h = getHeight();
        drawBackground(ctx, w, h);
        int gw = graphWidth.getInt();
        int gh = graphHeight.getInt();
        int x0 = pad();
        int y0 = pad() + (showText.isOn() ? 11 : 0);
        float[] ms = FrameStats.recent(gw);
        float top = maxMs.getFloat();
        if (targetLine.isOn() && 16.7f < top) {
            int ty = y0 + gh - Math.round(gh * 16.7f / top);
            for (int x = x0; x < x0 + gw; x += 3) ctx.fill(x, ty, x + 1, ty + 1, 0x60FFFFFF);
        }
        int offset = gw - ms.length;
        int prevY = -1;
        for (int i = 0; i < ms.length; i++) {
            int bh = Math.max(1, Math.min(gh, Math.round(gh * ms[i] / top)));
            int x = x0 + offset + i;
            int color = highlightSpikes.isOn() && ms[i] >= spikeMs.get() ? spikeColor.color() : graphColor.color(i / (float) gw);
            if (style.is("Bars")) {
                ctx.fill(x, y0 + gh - bh, x + 1, y0 + gh, color);
            } else {
                int y = y0 + gh - bh;
                if (prevY >= 0) ctx.fill(x, Math.min(prevY, y), x + 1, Math.max(prevY, y) + 1, color);
                prevY = y;
            }
        }
        if (showText.isOn()) {
            float last = ms.length > 0 ? ms[ms.length - 1] : 0;
            drawText(ctx, String.format(Locale.ROOT, "%.1f ms", last), x0, pad(), 0xFFFFFFFF);
        }
    }
}
