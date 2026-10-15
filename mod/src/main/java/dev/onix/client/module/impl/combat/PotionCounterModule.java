package dev.onix.client.module.impl.combat;

import dev.onix.client.module.ItemCounterModule;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.util.InventoryUtil;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;

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
        PotionContentsComponent contents = stack.get(DataComponentTypes.POTION_CONTENTS);
        return contents != null && (contents.matches(Potions.HEALING) || contents.matches(Potions.STRONG_HEALING));
    }

    @Override
    protected int count() {
        return switch (type.get()) {
            case "All Splash" -> InventoryUtil.count(Items.SPLASH_POTION);
            case "All Potions" -> InventoryUtil.count(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);
            default -> InventoryUtil.count(stack -> stack.isOf(Items.SPLASH_POTION) && healing(stack));
        };
    }
}
