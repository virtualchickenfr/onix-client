package dev.onix.client.module.impl.utility;

import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.module.Module;
import dev.onix.client.module.OverlayRenderer;
import dev.onix.client.setting.BoolSetting;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;

/** Hides what viewers should not see on stream: your coordinates, waypoints and the server address. */
public class StreamerModeModule extends Module implements OverlayRenderer {
    private static final Set<String> LOCATION = Set.of("coords", "deathcoords", "waypoints", "chunkinfo", "biome", "direction");
    private static final Set<String> SERVER = Set.of("server", "playercount");

    private final BoolSetting hideLocation = add(new BoolSetting("hideLocation", "Hide Location HUD", true)
            .describe("Coordinates, death coordinates, waypoints, chunk info, biome and direction."));
    private final BoolSetting hideServer = add(new BoolSetting("hideServer", "Hide Server HUD", true)
            .describe("Server address and player count."));
    private final BoolSetting hideOnF3 = add(new BoolSetting("hideF3", "Block the F3 Screen", true)
            .describe("F3 shows your position too; this closes it while streamer mode is on."));
    private final BoolSetting label = add(new BoolSetting("label", "Show \"Streamer Mode\" Label", true)
            .describe("A small reminder in the top-left corner – not visible in F1."));

    public StreamerModeModule() {
        super("streamer", "Streamer Mode", "Hides coordinates, waypoints and the server address while you stream or record.",
                Category.UTILITY, false);
        tags("stream", "obs", "twitch", "youtube", "hide coordinates", "privacy");
    }

    /** Whether a HUD module should stay hidden right now. */
    public boolean hides(HudModule hud) {
        if (!isEnabled()) return false;
        return (hideLocation.isOn() && LOCATION.contains(hud.getId())) || (hideServer.isOn() && SERVER.contains(hud.getId()));
    }

    @Override
    public void onTick() {
        if (hideOnF3.isOn() && mc().debugEntries.isOverlayVisible()) mc().debugEntries.setOverlayVisible(false);
    }

    @Override
    public void renderOverlay(GuiGraphics ctx) {
        if (!label.isOn()) return;
        String text = "Streamer Mode";
        int w = Theme.smallWidth(text) + 16;
        Theme.round(ctx, 4, 4, 4 + w, 15, 4, Theme.withAlpha(0xFF000000, 120));
        Theme.round(ctx, 8, 8, 11, 11, 1, Theme.RED);
        Theme.small(ctx, text, 14, 7, 0xFFFFFFFF);
    }
}
