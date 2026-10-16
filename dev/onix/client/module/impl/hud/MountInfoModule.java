package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;

public class MountInfoModule extends TextHudModule {
    private final BoolSetting speed = add(new BoolSetting("speed", "Speed", true));
    private final BoolSetting jump = add(new BoolSetting("jump", "Jump Height", true));
    private final BoolSetting health = add(new BoolSetting("health", "Health", true));

    public MountInfoModule() {
        super("mount", "Mount Info", "Speed, jump height and health of the horse you ride.", Category.HUD, false, Anchor.BOTTOM_RIGHT, -4, -150);
        tags("horse", "donkey", "riding");
    }

    @Override
    public boolean hasContent() {
        return mc().player != null && mc().player.getVehicle() instanceof LivingEntity;
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (mc().player == null) return;
        Entity vehicle = mc().player.getVehicle();
        if (!(vehicle instanceof LivingEntity mount)) {
            if (preview) out.add(new Line("Speed", "11.2 b/s"));
            return;
        }
        if (speed.isOn()) {
            double bps = mount.getAttributeValue(Attributes.MOVEMENT_SPEED) * 42.16;
            out.add(new Line("Speed", String.format(Locale.ROOT, "%.1f b/s", bps)));
        }
        if (jump.isOn() && mount instanceof AbstractHorse) {
            double s = mount.getAttributeValue(Attributes.JUMP_STRENGTH);
            double h = -0.1817584952 * s * s * s + 3.689713992 * s * s + 2.128599134 * s - 0.343930367;
            out.add(new Line("Jump", String.format(Locale.ROOT, "%.1f blocks", h)));
        }
        if (health.isOn()) out.add(new Line("Health", String.format(Locale.ROOT, "%.0f / %.0f", mount.getHealth(), mount.getMaxHealth())));
    }
}
