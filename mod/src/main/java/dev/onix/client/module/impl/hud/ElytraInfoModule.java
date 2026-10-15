package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.List;
import java.util.Locale;

public class ElytraInfoModule extends TextHudModule {
    private final BoolSetting onlyFlying = add(new BoolSetting("onlyFlying", "Only While Flying", true));
    private final BoolSetting speed = add(new BoolSetting("speed", "Speed", true));
    private final BoolSetting altitude = add(new BoolSetting("altitude", "Altitude", true));
    private final BoolSetting pitch = add(new BoolSetting("pitch", "Pitch", true));
    private final BoolSetting durability = add(new BoolSetting("durability", "Elytra Durability", true));
    private final BoolSetting rockets = add(new BoolSetting("rockets", "Rockets Left", true));

    private double lastX, lastY, lastZ, bps;

    public ElytraInfoModule() {
        super("elytra", "Elytra Info", "Speed, altitude, pitch and durability while gliding.", Category.HUD, false, Anchor.BOTTOM_RIGHT, -4, -100);
        tags("flying", "gliding", "fireworks");
    }

    @Override
    public void onTick() {
        if (mc().player == null) return;
        double x = mc().player.getX(), y = mc().player.getY(), z = mc().player.getZ();
        double d = Math.sqrt((x - lastX) * (x - lastX) + (y - lastY) * (y - lastY) + (z - lastZ) * (z - lastZ)) * 20;
        bps = bps * 0.6 + d * 0.4;
        lastX = x;
        lastY = y;
        lastZ = z;
    }

    @Override
    public boolean hasContent() {
        return mc().player != null && (!onlyFlying.isOn() || mc().player.isFallFlying());
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (mc().player == null) return;
        var p = mc().player;
        if (speed.isOn()) out.add(new Line("Speed", String.format(Locale.ROOT, "%.1f b/s", bps)));
        if (altitude.isOn()) out.add(new Line("Altitude", String.valueOf(p.getBlockY())));
        if (pitch.isOn()) out.add(new Line("Pitch", String.format(Locale.ROOT, "%.0f°", p.getPitch())));
        ItemStack chest = p.getEquippedStack(EquipmentSlot.CHEST);
        if (durability.isOn() && chest.isOf(Items.ELYTRA)) {
            int left = chest.getMaxDamage() - chest.getDamage();
            out.add(new Line("Elytra", left + " / " + chest.getMaxDamage(), left < 40 ? 0xFFFF5A5F : 0));
        }
        if (rockets.isOn()) out.add(new Line("Rockets", String.valueOf(p.getInventory().count(Items.FIREWORK_ROCKET))));
    }
}
