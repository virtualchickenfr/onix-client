package dev.onix.client.module.impl.utility;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** From Carbon: stops the drop key from throwing away valuable items. */
public class DropPreventionModule extends Module {
    private final BoolSetting weapons = add(new BoolSetting("weapons", "Weapons & Tools", true)
            .describe("Swords, axes, maces, tridents, bows and pickaxes."));
    private final BoolSetting enchanted = add(new BoolSetting("enchanted", "Enchanted Items", true));
    private final BoolSetting named = add(new BoolSetting("named", "Named Items", true));
    private final BoolSetting totems = add(new BoolSetting("totems", "Totems", true));
    private long lastWarn;

    public DropPreventionModule() {
        super("dropprevention", "Drop Prevention", "Stops you from accidentally dropping valuable items (from Carbon).", Category.UTILITY, false);
        tags("drop", "protect", "carbon");
    }

    public boolean shouldBlock(ItemStack s) {
        if (!isEnabled() || s.isEmpty()) return false;
        boolean block = (weapons.isOn() && (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE) || s.is(Items.TRIDENT)
                || s.is(Items.BOW) || s.is(Items.CROSSBOW) || s.is(ItemTags.PICKAXES)))
                || (enchanted.isOn() && s.isEnchanted())
                || (named.isOn() && s.has(DataComponents.CUSTOM_NAME))
                || (totems.isOn() && s.is(Items.TOTEM_OF_UNDYING));
        if (block && mc().player != null && System.currentTimeMillis() - lastWarn > 1500) {
            lastWarn = System.currentTimeMillis();
            mc().player.displayClientMessage(Component.literal("§7Drop prevented — turn off in §fOnix §7› Drop Prevention"), true);
        }
        return block;
    }
}
