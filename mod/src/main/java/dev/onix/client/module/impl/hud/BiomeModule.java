package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.ModeSetting;
import net.minecraft.text.Text;

import java.util.List;

public class BiomeModule extends TextHudModule {
    private final ModeSetting format = add(new ModeSetting("format", "Format", "Name", "ID"));

    public BiomeModule() {
        super("biome", "Biome", "The biome you are standing in.", Category.HUD, false, Anchor.TOP_LEFT, 4, 112);
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (mc().world == null || mc().player == null) {
            out.add(new Line("Biome", "Plains"));
            return;
        }
        var key = mc().world.getBiome(mc().player.getBlockPos()).getKey();
        if (key.isEmpty()) return;
        var id = key.get().getValue();
        String value = format.is("ID") ? id.toString()
                : Text.translatable("biome." + id.getNamespace() + "." + id.getPath()).getString();
        out.add(new Line("Biome", value));
    }
}
