package dev.onix.client.util;

import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class InventoryUtil {
    private InventoryUtil() {}

    /** Total count of matching items in main inventory, armor and offhand. */
    public static int count(Predicate<ItemStack> filter) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        Inventory inv = mc.player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && filter.test(stack)) total += stack.getCount();
        }
        return total;
    }

    public static int count(Item... items) {
        return count(stack -> {
            for (Item item : items) if (stack.is(item)) return true;
            return false;
        });
    }
}
