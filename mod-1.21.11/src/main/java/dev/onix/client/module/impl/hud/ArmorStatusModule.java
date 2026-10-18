package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.InventoryUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ArmorStatusModule extends HudModule {
    private final ModeSetting orientation = add(new ModeSetting("orientation", "Orientation", "Vertical", "Horizontal"));
    private final ModeSetting durability = add(new ModeSetting("durability", "Durability", "Number", "Percent", "Bar", "Hidden"));
    private final BoolSetting mainHand = add(new BoolSetting("mainHand", "Main Hand Item", true));
    private final BoolSetting offHand = add(new BoolSetting("offHand", "Off Hand Item", false));
    private final BoolSetting itemCount = add(new BoolSetting("itemCount", "Total Count of Held Item", true)
            .describe("For stackable items: how many you carry in total, e.g. arrows or blocks."));
    private final BoolSetting reverse = add(new BoolSetting("reverse", "Boots First", false));

    {
        group("Colors");
    }

    private final ColorSetting goodColor = add(new ColorSetting("goodColor", "High Durability", 0xFF34D399));
    private final ColorSetting midColor = add(new ColorSetting("midColor", "Medium Durability", 0xFFFACC15));
    private final ColorSetting lowColor = add(new ColorSetting("lowColor", "Low Durability", 0xFFFF5A5F));
    private final NumberSetting midBelow = add(new NumberSetting("midBelow", "Medium Below", 50, 5, 95, 5).unit("%"));
    private final NumberSetting lowBelow = add(new NumberSetting("lowBelow", "Low Below", 20, 1, 90, 1).unit("%"));
    private final ColorSetting countColor = add(new ColorSetting("countColor", "Count Color", 0xFFFFFFFF));

    public ArmorStatusModule() {
        super("armor", "Armor Status", "Your armor and held items with durability.", Category.HUD, true, Anchor.MIDDLE_RIGHT, -4, 0);
        tags("durability", "armour", "items");
    }

    private List<ItemStack> stacks(boolean preview) {
        List<ItemStack> list = new ArrayList<>();
        if (mc().player != null) {
            for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                    net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                ItemStack piece = mc().player.getItemBySlot(slot);
                if (!piece.isEmpty()) list.add(piece);
            }
            if (mainHand.isOn() && !mc().player.getMainHandItem().isEmpty()) list.add(mc().player.getMainHandItem());
            if (offHand.isOn() && !mc().player.getOffhandItem().isEmpty()) list.add(mc().player.getOffhandItem());
        }
        if (list.isEmpty() && preview) {
            list.add(new ItemStack(Items.DIAMOND_HELMET));
            list.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
            list.add(new ItemStack(Items.DIAMOND_LEGGINGS));
            list.add(new ItemStack(Items.DIAMOND_BOOTS));
            if (mainHand.isOn()) list.add(new ItemStack(Items.DIAMOND_SWORD));
        }
        if (reverse.isOn()) java.util.Collections.reverse(list);
        return list;
    }

    private String label(ItemStack stack) {
        if (stack.isDamageableItem()) {
            int left = stack.getMaxDamage() - stack.getDamageValue();
            return switch (durability.get()) {
                case "Percent" -> (left * 100 / stack.getMaxDamage()) + "%";
                case "Number" -> String.valueOf(left);
                default -> "";
            };
        }
        if (!itemCount.isOn()) return stack.getCount() > 1 ? String.valueOf(stack.getCount()) : "";
        int total = InventoryUtil.count(stack.getItem());
        return total > 1 ? String.valueOf(total) : "";
    }

    private int color(ItemStack stack) {
        if (!stack.isDamageableItem()) return countColor.color();
        float pct = 100f * (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage();
        return pct < lowBelow.get() ? lowColor.color() : pct < midBelow.get() ? midColor.color() : goodColor.color();
    }

    private int cell() {
        return durability.is("Hidden") || durability.is("Bar") ? 18 : 18 + textWidth("0000") + 2;
    }

    @Override
    public boolean hasContent() {
        return !stacks(false).isEmpty();
    }

    @Override
    public int getWidth() {
        int n = Math.max(1, stacks(true).size());
        return pad() * 2 + (orientation.is("Horizontal") ? n * cell() : cell());
    }

    @Override
    public int getHeight() {
        int n = Math.max(1, stacks(true).size());
        return pad() * 2 + (orientation.is("Horizontal") ? 17 : n * 17);
    }

    @Override
    public void render(GuiGraphics ctx, boolean preview) {
        List<ItemStack> list = stacks(preview);
        drawBackground(ctx, getWidth(), getHeight());
        int x = pad();
        int y = pad();
        for (ItemStack stack : list) {
            ctx.renderItem(stack, x, y);
            if (durability.is("Bar") && stack.isDamageableItem()) {
                float pct = (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage();
                ctx.fill(x + 2, y + 15, x + 14, y + 16, 0xFF000000);
                ctx.fill(x + 2, y + 15, x + 2 + Math.round(12 * pct), y + 16, color(stack));
            } else {
                String text = label(stack);
                if (!text.isEmpty()) drawText(ctx, text, x + 19, y + 5, color(stack));
            }
            if (orientation.is("Horizontal")) x += cell(); else y += 17;
        }
    }
}
