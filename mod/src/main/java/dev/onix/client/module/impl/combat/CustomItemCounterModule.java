package dev.onix.client.module.impl.combat;

import dev.onix.client.module.ItemCounterModule;
import dev.onix.client.setting.TextSetting;
import dev.onix.client.util.InventoryUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class CustomItemCounterModule extends ItemCounterModule {
    private final TextSetting itemId = add(new TextSetting("item", "Item ID", "minecraft:cobweb", 64)
            .describe("Any item, e.g. minecraft:cobweb, minecraft:tnt or minecraft:experience_bottle."));

    public CustomItemCounterModule() {
        super("customcounter", "Custom Item Counter", "Count any item you choose.", Anchor.BOTTOM_RIGHT, -4, -160);
        tags("item counter", "count");
    }

    @Override
    protected Item icon() {
        Identifier id = Identifier.tryParse(itemId.get().trim().toLowerCase(java.util.Locale.ROOT));
        if (id == null) return Items.BARRIER;
        Item item = Registries.ITEM.get(id);
        return item == Items.AIR ? Items.BARRIER : item;
    }

    @Override
    protected String label() {
        return new ItemStack(icon()).getName().getString();
    }

    @Override
    protected int count() {
        Item item = icon();
        return item == Items.BARRIER ? 0 : InventoryUtil.count(item);
    }
}
