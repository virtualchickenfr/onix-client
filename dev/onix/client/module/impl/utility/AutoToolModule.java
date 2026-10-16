package dev.onix.client.module.impl.utility;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Picks the fastest tool from your hotbar while mining. Some servers do not allow this. */
public class AutoToolModule extends Module {
    private final BoolSetting switchBack = add(new BoolSetting("back", "Switch Back Afterwards", true));
    private final NumberSetting backDelay = add(new NumberSetting("backDelay", "Switch Back After", 0, 0, 40, 1).unit("ticks")
            .describe("Wait a moment before switching back, handy when mining several blocks in a row.").visibleIf(switchBack::isOn));
    private final BoolSetting sneakToSkip = add(new BoolSetting("sneakSkip", "Sneak to Disable", true)
            .describe("Hold sneak to mine with whatever you are holding."));
    private final BoolSetting survivalOnly = add(new BoolSetting("survivalOnly", "Survival Only", true));

    {
        group("Protection");
    }

    private final BoolSetting protectTools = add(new BoolSetting("protect", "Skip Nearly Broken Tools", true));
    private final NumberSetting threshold = add(new NumberSetting("threshold", "Minimum Durability Left", 5, 1, 100, 1)
            .visibleIf(protectTools::isOn));

    private int previousSlot = -1;
    private int idleTicks;

    public AutoToolModule() {
        super("autotool", "Auto Tool", "Picks the best tool from your hotbar while mining.", Category.UTILITY, false);
        restricted("Switches items for you while mining. Many servers count this as automation.");
        tags("mining", "pickaxe", "best tool");
    }

    private boolean usable(ItemStack stack) {
        return !protectTools.isOn() || !stack.isDamageableItem() || stack.getMaxDamage() - stack.getDamageValue() > threshold.getInt();
    }

    @Override
    public void onTick() {
        if (mc().player == null || mc().level == null || mc().screen != null) return;
        if (survivalOnly.isOn() && (mc().player.isCreative() || mc().player.isSpectator())) return;
        Inventory inv = mc().player.getInventory();
        boolean mining = mc().options.keyAttack.isDown() && !(sneakToSkip.isOn() && mc().player.isShiftKeyDown())
                && mc().hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;
        if (!mining) {
            if (previousSlot >= 0 && (!switchBack.isOn() || ++idleTicks > backDelay.getInt())) {
                if (switchBack.isOn()) inv.selected = previousSlot;
                previousSlot = -1;
            }
            return;
        }
        idleTicks = 0;
        BlockState state = mc().level.getBlockState(((BlockHitResult) mc().hitResult).getBlockPos());
        int best = inv.selected;
        float bestSpeed = usable(inv.getItem(best)) ? inv.getItem(best).getDestroySpeed(state) : 0f;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (!usable(stack)) continue;
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }
        if (best != inv.selected) {
            if (previousSlot < 0) previousSlot = inv.selected;
            inv.selected = best;
        }
    }

    @Override
    protected void onDisable() {
        previousSlot = -1;
        idleTicks = 0;
    }
}
