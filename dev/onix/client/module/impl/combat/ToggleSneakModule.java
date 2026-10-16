package dev.onix.client.module.impl.combat;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.TextSetting;
import java.util.List;
import net.minecraft.client.KeyMapping;

/** Tap the sneak key once to keep sneaking. */
public class ToggleSneakModule extends TextHudModule {
    private final BoolSetting showStatus = add(new BoolSetting("status", "Show Status", true));
    private final TextSetting statusText = add(new TextSetting("text", "Status Text", "Sneaking (Toggled)", 32).visibleIf(showStatus::isOn));
    private final BoolSetting releaseOnFly = add(new BoolSetting("releaseFly", "Release When Flying", true));

    private boolean toggled;
    private boolean forcing;

    public ToggleSneakModule() {
        super("togglesneak", "Toggle Sneak", "Tap sneak once instead of holding it.", Category.COMBAT, false, Anchor.BOTTOM_LEFT, 4, -22);
        tags("sneak", "crouch", "shift");
        labelStyle.withDefault("Value Only");
    }

    @Override
    public void onTick() {
        KeyMapping sneak = mc().options.keyShift;
        while (sneak.consumeClick()) toggled = !toggled;
        if (releaseOnFly.isOn() && mc().player != null && mc().player.getAbilities().flying) toggled = false;
        if (toggled) {
            sneak.setDown(true);
            forcing = true;
        } else if (forcing) {
            sneak.setDown(false);
            forcing = false;
        }
    }

    @Override
    protected void onDisable() {
        if (forcing && mc().options != null) mc().options.keyShift.setDown(false);
        forcing = false;
        toggled = false;
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if ((toggled && showStatus.isOn()) || preview) out.add(new Line("Sneak", statusText.get()));
    }
}
