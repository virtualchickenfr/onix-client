package dev.onix.client.module.impl.combat;

import dev.onix.client.module.ItemCounterModule;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.util.InventoryUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

public class PotionCounterModule extends ItemCounterModule {
    private final ModeSetting type = add(new ModeSetting("type", "Count", "Splash Healing", "All Splash", "All Potions"));

    public PotionCounterModule() {
        super("pots", "Potion Counter", "Healing or other potions in your inventory.", Anchor.BOTTOM_RIGHT, -4, -120);
        tags("pots", "pot pvp", "healing");
    }

    @Override
    protected Item icon() {
        return type.is("All Potions") ? Items.POTION : Items.SPLASH_POTION;
    }

    private static boolean healing(ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        return contents != null && (contents.is(Potions.HEALING) || contents.is(Potions.STRONG_HEALING));
    }

    @Override
    protected int count() {
        return switch (type.get()) {
            case "All Splash" -> InventoryUtil.count(Items.SPLASH_POTION);
            case "All Potions" -> InventoryUtil.count(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);
            default -> InventoryUtil.count(stack -> stack.is(Items.SPLASH_POTION) && healing(stack));
        };
    }
}
