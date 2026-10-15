package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;

/** Shows the main inventory (without hotbar) on screen. */
public class InventoryViewerModule extends HudModule {
    private final NumberSetting rows = add(new NumberSetting("rows", "Rows", 3, 1, 3, 1));
    private final NumberSetting slotSize = add(new NumberSetting("slotSize", "Slot Size", 18, 16, 22, 1).unit("px"));
    private final BoolSetting slots = add(new BoolSetting("slots", "Draw Slots", true));
    private final ColorSetting slotColor = add(new ColorSetting("slotColor", "Slot Color", 0x30FFFFFF).visibleIf(slots::isOn));
    private final BoolSetting hideEmpty = add(new BoolSetting("hideEmpty", "Hide When Empty", false));

    public InventoryViewerModule() {
        super("inventory", "Inventory Viewer", "Your inventory on screen at all times.", Category.HUD, false, Anchor.BOTTOM_RIGHT, -4, -4);
        tags("inv", "items");
    }

    @Override
    public boolean hasContent() {
        if (!hideEmpty.isOn() || mc().player == null) return true;
        for (int i = 9; i < 36; i++) if (!mc().player.getInventory().getStack(i).isEmpty()) return true;
        return false;
    }

    @Override
    public int getWidth() {
        return pad() * 2 + 9 * slotSize.getInt();
    }

    @Override
    public int getHeight() {
        return pad() * 2 + rows.getInt() * slotSize.getInt();
    }

    @Override
    public void render(DrawContext ctx, boolean preview) {
        drawBackground(ctx, getWidth(), getHeight());
        int s = slotSize.getInt();
        int inset = (s - 16) / 2;
        for (int r = 0; r < rows.getInt(); r++) {
            for (int c = 0; c < 9; c++) {
                int x = pad() + c * s;
                int y = pad() + r * s;
                if (slots.isOn()) ctx.fill(x + 1, y + 1, x + s - 1, y + s - 1, slotColor.color());
                if (mc().player == null) continue;
                ItemStack stack = mc().player.getInventory().getStack(9 + r * 9 + c);
                if (stack.isEmpty()) continue;
                ctx.drawItem(stack, x + inset, y + inset);
                ctx.drawItemInSlot(mc().textRenderer, stack, x + inset, y + inset);
            }
        }
    }
}
