package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.network.PlayerListEntry;

import java.util.List;

public class PingModule extends TextHudModule {
    private final BoolSetting unit = add(new BoolSetting("unit", "Show \"ms\"", true));
    private final BoolSetting hideSingleplayer = add(new BoolSetting("hideSp", "Hide in Singleplayer", true));
    private final BoolSetting colorize = add(new BoolSetting("colorize", "Color by Latency", true));
    private final NumberSetting good = add(new NumberSetting("good", "Good Below", 60, 10, 300, 5).unit("ms").visibleIf(colorize::isOn));
    private final NumberSetting bad = add(new NumberSetting("bad", "Bad From", 150, 50, 1000, 10).unit("ms").visibleIf(colorize::isOn));
    private final ColorSetting goodColor = add(new ColorSetting("goodColor", "Good Color", 0xFF34D399).visibleIf(colorize::isOn));
    private final ColorSetting okColor = add(new ColorSetting("okColor", "Medium Color", 0xFFFACC15).visibleIf(colorize::isOn));
    private final ColorSetting badColor = add(new ColorSetting("badColor", "Bad Color", 0xFFFF5A5F).visibleIf(colorize::isOn));

    public PingModule() {
        super("ping", "Ping", "Your latency to the server.", Category.HUD, true, Anchor.TOP_LEFT, 4, 40);
        tags("latency", "ms", "connection");
    }

    private int latency() {
        if (mc().player == null || mc().getNetworkHandler() == null) return 0;
        PlayerListEntry entry = mc().getNetworkHandler().getPlayerListEntry(mc().player.getUuid());
        return entry == null ? 0 : entry.getLatency();
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (!preview && hideSingleplayer.isOn() && mc().isInSingleplayer()) return;
        int ms = preview && latency() == 0 ? 42 : latency();
        int color = 0;
        if (colorize.isOn()) color = ms < good.get() ? goodColor.color() : ms >= bad.get() ? badColor.color() : okColor.color();
        out.add(new Line("Ping", ms + (unit.isOn() ? " ms" : ""), color));
    }

    @Override
    protected String sampleValue() {
        return unit.isOn() ? "000 ms" : "000";
    }
}
