package dev.onix.client.module.impl.combat;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.util.CombatTracker;

import java.util.List;
import java.util.Locale;

public class SessionStatsModule extends TextHudModule {
    private final BoolSetting kills = add(new BoolSetting("kills", "Kills", true));
    private final BoolSetting deaths = add(new BoolSetting("deaths", "Deaths", true));
    private final BoolSetting kdr = add(new BoolSetting("kdr", "K/D Ratio", true));
    private final BoolSetting hits = add(new BoolSetting("hits", "Hits", false));
    private final BoolSetting bestCombo = add(new BoolSetting("bestCombo", "Best Combo", false));
    private final KeybindSetting resetKey = add(new KeybindSetting("resetKey", "Reset Key", KeybindSetting.NONE));

    public SessionStatsModule() {
        super("sessionstats", "Session Stats", "Kills, deaths and K/D since you started playing.", Category.COMBAT, false, Anchor.TOP_LEFT, 4, 238);
        tags("kd", "kdr", "statistics");
    }

    @Override
    public void onTick() {
        if (resetKey.poll()) CombatTracker.resetStats();
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        int k = CombatTracker.kills();
        int d = CombatTracker.deaths();
        if (kills.isOn()) out.add(new Line("Kills", String.valueOf(k)));
        if (deaths.isOn()) out.add(new Line("Deaths", String.valueOf(d)));
        if (kdr.isOn()) out.add(new Line("K/D", String.format(Locale.ROOT, "%.2f", d == 0 ? k : k / (double) d)));
        if (hits.isOn()) out.add(new Line("Hits", String.valueOf(CombatTracker.hits())));
        if (bestCombo.isOn()) out.add(new Line("Best Combo", String.valueOf(CombatTracker.bestCombo())));
    }
}
