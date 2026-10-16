package dev.onix.client.module.impl.utility;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import java.util.Locale;

/** Extra lines in item tooltips: durability, food values, stack size and item ID. */
public class ItemTooltipsModule extends Module {
    private final BoolSetting durability = add(new BoolSetting("durability", "Durability", true));
    private final BoolSetting food = add(new BoolSetting("food", "Food & Saturation", true));
    private final BoolSetting stackSize = add(new BoolSetting("stack", "Max Stack Size", false));
    private final BoolSetting itemId = add(new BoolSetting("id", "Item ID", false));
    private final BoolSetting onlyShift = add(new BoolSetting("shift", "Only While Holding Shift", false));

    public ItemTooltipsModule() {
        super("tooltips", "Item Tooltips", "More information in item tooltips.", Category.UTILITY, false);
        tags("tooltip", "durability", "advanced tooltips");
    }

    @Override
    public void init() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!isEnabled()) return;
            if (onlyShift.isOn() && !net.minecraft.client.gui.screens.Screen.hasShiftDown()) return;
            if (durability.isOn() && stack.isDamageableItem()) {
                int left = stack.getMaxDamage() - stack.getDamageValue();
                lines.add(Component.literal("Durability: " + left + " / " + stack.getMaxDamage()).withStyle(ChatFormatting.GRAY));
            }
            FoodProperties foodComponent = stack.get(DataComponents.FOOD);
            if (food.isOn() && foodComponent != null) {
                lines.add(Component.literal(String.format(Locale.ROOT, "Food: %d · Saturation: %.1f",
                        foodComponent.nutrition(), foodComponent.saturation())).withStyle(ChatFormatting.GRAY));
            }
            if (stackSize.isOn()) lines.add(Component.literal("Stacks to " + stack.getMaxStackSize()).withStyle(ChatFormatting.DARK_GRAY));
            if (itemId.isOn()) lines.add(Component.literal(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()).withStyle(ChatFormatting.DARK_GRAY));
        });
    }
}
