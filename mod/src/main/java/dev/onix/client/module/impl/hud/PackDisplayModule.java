package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.resource.ResourcePackProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PackDisplayModule extends TextHudModule {
    private static final Set<String> BUILT_IN = Set.of("vanilla", "fabric", "high_contrast", "programmer_art", "server");

    private final NumberSetting maxShown = add(new NumberSetting("max", "Max Packs", 3, 1, 10, 1));
    private final BoolSetting topFirst = add(new BoolSetting("topFirst", "Highest Priority First", true));
    private final BoolSetting stripColors = add(new BoolSetting("stripColors", "Remove Pack Colors", true));

    public PackDisplayModule() {
        super("packdisplay", "Pack Display", "Which resource packs are active.", Category.HUD, false, Anchor.BOTTOM_RIGHT, -4, -80);
        tags("resource pack", "texture pack");
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        List<String> names = new ArrayList<>();
        for (ResourcePackProfile profile : mc().getResourcePackManager().getEnabledProfiles()) {
            if (BUILT_IN.contains(profile.getId())) continue;
            String name = profile.getDisplayName().getString();
            if (stripColors.isOn()) name = name.replaceAll("§.", "");
            names.add(name.replace(".zip", ""));
        }
        if (topFirst.isOn()) java.util.Collections.reverse(names);
        if (names.isEmpty()) {
            if (preview) out.add(new Line("Pack", "No packs active"));
            return;
        }
        for (int i = 0; i < Math.min(names.size(), maxShown.getInt()); i++) out.add(new Line(i == 0 ? "Pack" : "", names.get(i)));
    }
}
