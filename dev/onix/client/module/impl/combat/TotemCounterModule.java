package dev.onix.client.module.impl.combat;

import dev.onix.client.module.ItemCounterModule;
import dev.onix.client.util.InventoryUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class TotemCounterModule extends ItemCounterModule {
    public TotemCounterModule() {
        super("totems", "Totem Counter", "Totems of Undying in your inventory.", Anchor.BOTTOM_RIGHT, -4, -60);
        tags("totem", "crystal pvp");
    }

    @Override
    protected Item icon() {
        return Items.TOTEM_OF_UNDYING;
    }

    @Override
    protected int count() {
        return InventoryUtil.count(Items.TOTEM_OF_UNDYING);
    }
}
