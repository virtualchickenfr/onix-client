package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.util.InventoryUtil;
import net.minecraft.item.ItemStack;

import java.util.List;

public class HeldItemModule extends TextHudModule {
    private final BoolSetting durability = add(new BoolSetting("durability", "Durability", true));
    private final BoolSetting total = add(new BoolSetting("total", "Total in Inventory", true));
    private final BoolSetting offhand = add(new BoolSetting("offhand", "Off Hand Line", false));
    private final BoolSetting hideEmpty = add(new BoolSetting("hideEmpty", "Hide With Empty Hand", true));

    public HeldItemModule() {
        super("helditem", "Held Item", "Name, durability and total count of the item in your hand.", Category.HUD, false, Anchor.BOTTOM_CENTER, 0, -60);
        tags("hand", "item name");
    }

    private void describe(List<Line> out, String label, ItemStack stack) {
        if (stack.isEmpty()) return;
        StringBuilder value = new StringBuilder(stack.getName().getString());
        if (durability.isOn() && stack.isDamageable()) {
            value.append(" · ").append(stack.getMaxDamage() - stack.getDamage()).append('/').append(stack.getMaxDamage());
        }
        if (total.isOn() && stack.isStackable()) value.append(" · ").append(InventoryUtil.count(stack.getItem()));
        out.add(new Line(label, value.toString()));
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (mc().player == null) return;
        describe(out, "Main", mc().player.getMainHandStack());
        if (offhand.isOn()) describe(out, "Off", mc().player.getOffHandStack());
        if (out.isEmpty() && (preview || !hideEmpty.isOn())) out.add(new Line("Main", "Empty"));
    }
}
