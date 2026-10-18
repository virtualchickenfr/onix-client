package dev.onix.client.module.impl.performance;

import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.NumberSetting;

/** Switches between two render (and optionally simulation) distances with one key. */
public class RenderDistanceToggleModule extends Module {
    private final KeybindSetting key = add(new KeybindSetting("key", "Switch Key", KeybindSetting.NONE));
    private final NumberSetting first = add(new NumberSetting("first", "Distance A", 8, 2, 32, 1).unit("chunks"));
    private final NumberSetting second = add(new NumberSetting("second", "Distance B", 16, 2, 32, 1).unit("chunks"));
    private final BoolSetting notify = add(new BoolSetting("notify", "Show Notification", true));

    {
        group("Simulation Distance");
    }

    private final BoolSetting simulation = add(new BoolSetting("simulation", "Switch Simulation Distance Too", false)
            .describe("Only matters in singleplayer and LAN worlds."));
    private final NumberSetting simFirst = add(new NumberSetting("simFirst", "Simulation A", 6, 5, 32, 1).unit("chunks").visibleIf(simulation::isOn));
    private final NumberSetting simSecond = add(new NumberSetting("simSecond", "Simulation B", 12, 5, 32, 1).unit("chunks").visibleIf(simulation::isOn));

    private boolean useSecond;

    public RenderDistanceToggleModule() {
        super("renderdistance", "Render Distance Switch", "Toggle between two render distances.", Category.PERFORMANCE, false);
        tags("chunks", "view distance", "simulation distance");
    }

    @Override
    public void onTick() {
        if (!key.poll()) return;
        useSecond = !useSecond;
        int target = (useSecond ? second : first).getInt();
        mc().options.renderDistance().set(target);
        String text = target + " chunks";
        if (simulation.isOn()) {
            int sim = (useSecond ? simSecond : simFirst).getInt();
            mc().options.simulationDistance().set(sim);
            text += " · simulation " + sim;
        }
        if (notify.isOn()) Notifications.show("Render distance", text);
    }
}
