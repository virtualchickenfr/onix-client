package dev.onix.client.module.impl.utility;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

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
        return !protectTools.isOn() || !stack.isDamageable() || stack.getMaxDamage() - stack.getDamage() > threshold.getInt();
    }

    @Override
    public void onTick() {
        if (mc().player == null || mc().world == null || mc().currentScreen != null) return;
        if (survivalOnly.isOn() && (mc().player.isCreative() || mc().player.isSpectator())) return;
        PlayerInventory inv = mc().player.getInventory();
        boolean mining = mc().options.attackKey.isPressed() && !(sneakToSkip.isOn() && mc().player.isSneaking())
                && mc().crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK;
        if (!mining) {
            if (previousSlot >= 0 && (!switchBack.isOn() || ++idleTicks > backDelay.getInt())) {
                if (switchBack.isOn()) inv.selectedSlot = previousSlot;
                previousSlot = -1;
            }
            return;
        }
        idleTicks = 0;
        BlockState state = mc().world.getBlockState(((BlockHitResult) mc().crosshairTarget).getBlockPos());
        int best = inv.selectedSlot;
        float bestSpeed = usable(inv.getStack(best)) ? inv.getStack(best).getMiningSpeedMultiplier(state) : 0f;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = inv.getStack(slot);
            if (!usable(stack)) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }
        if (best != inv.selectedSlot) {
            if (previousSlot < 0) previousSlot = inv.selectedSlot;
            inv.selectedSlot = best;
        }
    }

    @Override
    protected void onDisable() {
        previousSlot = -1;
        idleTicks = 0;
    }
}
