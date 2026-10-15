package dev.onix.client.module.impl.combat;

import dev.onix.client.module.ItemCounterModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.util.InventoryUtil;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class ArrowCounterModule extends ItemCounterModule {
    private final BoolSetting onlyWithBow = add(new BoolSetting("onlyBow", "Only With Bow in Hand", true));
    private final BoolSetting special = add(new BoolSetting("special", "Count Tipped & Spectral", true));

    public ArrowCounterModule() {
        super("arrows", "Arrow Counter", "How many arrows you carry.", Anchor.BOTTOM_RIGHT, -4, -40);
        tags("bow", "ammo");
    }

    @Override
    protected Item icon() {
        return Items.ARROW;
    }

    @Override
    protected int count() {
        return special.isOn() ? InventoryUtil.count(Items.ARROW, Items.SPECTRAL_ARROW, Items.TIPPED_ARROW) : InventoryUtil.count(Items.ARROW);
    }

    @Override
    public boolean hasContent() {
        if (onlyWithBow.isOn() && mc().player != null) {
            Item main = mc().player.getMainHandStack().getItem();
            Item off = mc().player.getOffHandStack().getItem();
            boolean bow = main instanceof BowItem || main instanceof CrossbowItem || off instanceof BowItem || off instanceof CrossbowItem;
            if (!bow) return false;
        }
        return super.hasContent();
    }
}
