package dev.onix.client.module.impl.utility;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

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
            if (onlyShift.isOn() && !net.minecraft.client.gui.screen.Screen.hasShiftDown()) return;
            if (durability.isOn() && stack.isDamageable()) {
                int left = stack.getMaxDamage() - stack.getDamage();
                lines.add(Text.literal("Durability: " + left + " / " + stack.getMaxDamage()).formatted(Formatting.GRAY));
            }
            FoodComponent foodComponent = stack.get(DataComponentTypes.FOOD);
            if (food.isOn() && foodComponent != null) {
                lines.add(Text.literal(String.format(Locale.ROOT, "Food: %d · Saturation: %.1f",
                        foodComponent.nutrition(), foodComponent.saturation())).formatted(Formatting.GRAY));
            }
            if (stackSize.isOn()) lines.add(Text.literal("Stacks to " + stack.getMaxCount()).formatted(Formatting.DARK_GRAY));
            if (itemId.isOn()) lines.add(Text.literal(Registries.ITEM.getId(stack.getItem()).toString()).formatted(Formatting.DARK_GRAY));
        });
    }
}
