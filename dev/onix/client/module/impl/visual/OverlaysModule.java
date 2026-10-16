package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;

/** Hides screen overlays that block your view. Applied by InGameHudMixin. */
public class OverlaysModule extends Module {
    public final BoolSetting pumpkin = add(new BoolSetting("pumpkin", "Hide Pumpkin Blur", true));
    public final BoolSetting powderSnow = add(new BoolSetting("powderSnow", "Hide Powder Snow Frost", true));
    public final BoolSetting vignette = add(new BoolSetting("vignette", "Hide Vignette", false)
            .describe("The dark screen edges vanilla draws in Fancy graphics."));
    public final BoolSetting portal = add(new BoolSetting("portal", "Hide Nether Portal Swirl", false));
    public final BoolSetting spyglass = add(new BoolSetting("spyglass", "Hide Spyglass Frame", false));

    public OverlaysModule() {
        super("overlays", "Overlays", "Remove pumpkin blur, portal swirl and other overlays.", Category.VISUAL, false);
        restricted("Removing the pumpkin overlay is an advantage in some minigames.");
        tags("pumpkin", "vignette", "portal", "clear view");
    }
}
