package dev.onix.client.module.impl.combat;

import dev.onix.client.module.ItemCounterModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.util.InventoryUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class BlockCounterModule extends ItemCounterModule {
    private final BoolSetting onlyHeldType = add(new BoolSetting("heldType", "Only Held Block Type", false)
            .describe("Count only the block you are holding instead of all blocks."));

    public BlockCounterModule() {
        super("blocks", "Block Counter", "Placeable blocks you carry – great for bridging.", Anchor.BOTTOM_CENTER, 0, -64);
        tags("bridging", "bedwars", "skywars");
    }

    @Override
    protected Item icon() {
        if (mc().player != null && mc().player.getMainHandItem().getItem() instanceof BlockItem held) return held;
        return Items.WHITE_WOOL;
    }

    @Override
    protected int count() {
        if (onlyHeldType.isOn() && mc().player != null && mc().player.getMainHandItem().getItem() instanceof BlockItem held) {
            return InventoryUtil.count(held);
        }
        return InventoryUtil.count(stack -> stack.getItem() instanceof BlockItem);
    }
}
