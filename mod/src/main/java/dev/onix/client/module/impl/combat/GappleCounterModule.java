package dev.onix.client.module.impl.combat;

import dev.onix.client.module.ItemCounterModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.util.InventoryUtil;
import net.minecraft.item.Item;
import net.minecraft.item.Items;

public class GappleCounterModule extends ItemCounterModule {
    private final BoolSetting enchanted = add(new BoolSetting("enchanted", "Include Enchanted", true));

    public GappleCounterModule() {
        super("gapples", "Golden Apple Counter", "Golden apples in your inventory.", Anchor.BOTTOM_RIGHT, -4, -100);
        tags("gapple", "golden apple", "uhc");
    }

    @Override
    protected Item icon() {
        return Items.GOLDEN_APPLE;
    }

    @Override
    protected int count() {
        return enchanted.isOn() ? InventoryUtil.count(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE) : InventoryUtil.count(Items.GOLDEN_APPLE);
    }
}
