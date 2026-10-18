package dev.onix.client.module.impl.combat;

import dev.onix.client.module.ItemCounterModule;
import dev.onix.client.util.InventoryUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class PearlCounterModule extends ItemCounterModule {
    public PearlCounterModule() {
        super("pearls", "Pearl Counter", "Ender pearls in your inventory.", Anchor.BOTTOM_RIGHT, -4, -80);
        tags("ender pearl", "bedwars");
    }

    @Override
    protected Item icon() {
        return Items.ENDER_PEARL;
    }

    @Override
    protected int count() {
        return InventoryUtil.count(Items.ENDER_PEARL);
    }
}
