package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.ModeSetting;
import java.util.List;
import net.minecraft.network.chat.Component;

public class BiomeModule extends TextHudModule {
    private final ModeSetting format = add(new ModeSetting("format", "Format", "Name", "ID"));

    public BiomeModule() {
        super("biome", "Biome", "The biome you are standing in.", Category.HUD, false, Anchor.TOP_LEFT, 4, 112);
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (mc().level == null || mc().player == null) {
            out.add(new Line("Biome", "Plains"));
            return;
        }
        var key = mc().level.getBiome(mc().player.blockPosition()).unwrapKey();
        if (key.isEmpty()) return;
        var id = key.get().identifier();
        String value = format.is("ID") ? id.toString()
                : Component.translatable("biome." + id.getNamespace() + "." + id.getPath()).getString();
        out.add(new Line("Biome", value));
    }
}
