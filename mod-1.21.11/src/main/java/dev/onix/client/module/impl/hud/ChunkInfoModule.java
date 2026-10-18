package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;

import java.util.List;

public class ChunkInfoModule extends TextHudModule {
    private final BoolSetting chunk = add(new BoolSetting("chunk", "Chunk Coordinates", true));
    private final BoolSetting inChunk = add(new BoolSetting("inChunk", "Position in Chunk", true));
    private final BoolSetting region = add(new BoolSetting("region", "Region File", false));
    private final BoolSetting loaded = add(new BoolSetting("loaded", "Loaded Chunks", false));

    public ChunkInfoModule() {
        super("chunkinfo", "Chunk Info", "Chunk and region coordinates, handy for farms and builds.", Category.HUD, false, Anchor.TOP_LEFT, 4, 130);
        tags("region", "chunk coordinates");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        int x = 0, z = 0;
        if (mc().player != null) {
            x = mc().player.getBlockX();
            z = mc().player.getBlockZ();
        }
        if (chunk.isOn()) out.add(new Line("Chunk", (x >> 4) + ", " + (z >> 4)));
        if (inChunk.isOn()) out.add(new Line("In Chunk", (x & 15) + ", " + (z & 15)));
        if (region.isOn()) out.add(new Line("Region", "r." + (x >> 9) + "." + (z >> 9) + ".mca"));
        if (loaded.isOn() && mc().level != null) {
            out.add(new Line("Loaded", String.valueOf(mc().level.getChunkSource().getLoadedChunksCount())));
        }
    }
}
