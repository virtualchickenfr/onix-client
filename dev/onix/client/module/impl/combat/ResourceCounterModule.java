package dev.onix.client.module.impl.combat;

import dev.onix.client.module.ItemCounterModule;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.util.InventoryUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class ResourceCounterModule extends ItemCounterModule {
    private final ModeSetting resource = add(new ModeSetting("resource", "Resource", "Iron", "Gold", "Diamond", "Emerald", "Netherite", "Lapis"));

    public ResourceCounterModule() {
        super("resources", "Resource Counter", "Iron, gold, diamonds or emeralds you carry – for Bedwars and more.", Anchor.BOTTOM_RIGHT, -4, -140);
        tags("bedwars", "iron", "gold", "emerald", "diamond");
    }

    @Override
    protected Item icon() {
        return switch (resource.get()) {
            case "Gold" -> Items.GOLD_INGOT;
            case "Diamond" -> Items.DIAMOND;
            case "Emerald" -> Items.EMERALD;
            case "Netherite" -> Items.NETHERITE_INGOT;
            case "Lapis" -> Items.LAPIS_LAZULI;
            default -> Items.IRON_INGOT;
        };
    }

    @Override
    protected String label() {
        return resource.get();
    }

    @Override
    protected int count() {
        return InventoryUtil.count(icon());
    }
}
