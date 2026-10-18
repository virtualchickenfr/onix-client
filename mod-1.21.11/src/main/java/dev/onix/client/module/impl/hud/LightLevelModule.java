package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

public class LightLevelModule extends TextHudModule {
    private final ModeSetting show = add(new ModeSetting("show", "Show", "Block Light", "Sky Light", "Both"));
    private final BoolSetting warn = add(new BoolSetting("warn", "Spawn Warning", true)
            .describe("Colors the value when hostile mobs can spawn here (block light at or below the threshold)."));
    private final NumberSetting threshold = add(new NumberSetting("threshold", "Spawn Threshold", 0, 0, 15, 1).visibleIf(warn::isOn));
    private final ColorSetting warnColor = add(new ColorSetting("warnColor", "Warning Color", 0xFFFF5A5F).visibleIf(warn::isOn));

    public LightLevelModule() {
        super("lightlevel", "Light Level", "Block and sky light where you stand.", Category.HUD, false, Anchor.TOP_LEFT, 4, 148);
        tags("mob spawning", "torch");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        int block = 15;
        int sky = 15;
        if (mc().level != null && mc().player != null) {
            BlockPos pos = mc().player.blockPosition();
            block = mc().level.getBrightness(LightLayer.BLOCK, pos);
            sky = mc().level.getBrightness(LightLayer.SKY, pos);
        }
        int color = warn.isOn() && block <= threshold.getInt() ? warnColor.color() : 0;
        if (!show.is("Sky Light")) out.add(new Line("Block Light", String.valueOf(block), color));
        if (!show.is("Block Light")) out.add(new Line("Sky Light", String.valueOf(sky)));
    }

    @Override
    protected String sampleValue() {
        return "15";
    }
}
