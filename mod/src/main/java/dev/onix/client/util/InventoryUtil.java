package dev.onix.client.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.function.Predicate;

public final class InventoryUtil {
    private InventoryUtil() {}

    /** Total count of matching items in main inventory, armor and offhand. */
    public static int count(Predicate<ItemStack> filter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return 0;
        PlayerInventory inv = mc.player.getInventory();
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && filter.test(stack)) total += stack.getCount();
        }
        return total;
    }

    public static int count(Item... items) {
        return count(stack -> {
            for (Item item : items) if (stack.isOf(item)) return true;
            return false;
        });
    }
}
