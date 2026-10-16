package gg.carbon.client.module.impl.pvp;

import gg.carbon.client.module.Category;
import gg.carbon.client.module.Module;
import gg.carbon.client.setting.BoolSetting;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PvpMods {
	private PvpMods() {}

	public static class Hitbox extends Module {
		public Hitbox() {
			super("hitbox", "Hitboxes", "Shows hitboxes around entities", Category.PVP);
		}

		@Override
		protected void onEnable() {
			mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.ALWAYS_ON);
		}

		@Override
		protected void onDisable() {
			mc.debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES, DebugScreenEntryStatus.NEVER);
		}
	}

	public static class DropPrevention extends Module {
		private final BoolSetting weapons = add(new BoolSetting("Weapons & Tools", "Protect swords, axes, maces, tridents, bows", true));
		private final BoolSetting enchanted = add(new BoolSetting("Enchanted Items", "Protect anything enchanted", true));
		private final BoolSetting named = add(new BoolSetting("Named Items", "Protect renamed items", true));
		private final BoolSetting totems = add(new BoolSetting("Totems", "Protect totems of undying", true));
		private long lastWarn;

		public DropPrevention() {
			super("dropprevention", "Drop Prevention", "Stops you from accidentally dropping valuable items", Category.PVP);
		}

		public boolean shouldBlock(ItemStack s) {
			if (s.isEmpty()) return false;
			boolean block = (weapons.on() && (s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.MACE) || s.is(Items.TRIDENT)
				|| s.is(Items.BOW) || s.is(Items.CROSSBOW) || s.is(ItemTags.PICKAXES)))
				|| (enchanted.on() && s.isEnchanted())
				|| (named.on() && s.has(DataComponents.CUSTOM_NAME))
				|| (totems.on() && s.is(Items.TOTEM_OF_UNDYING));
			if (block && mc.player != null && System.currentTimeMillis() - lastWarn > 1500) {
				lastWarn = System.currentTimeMillis();
				mc.player.displayClientMessage(Component.literal("§7Drop prevented — disable in §fCarbon §7› Drop Prevention"), true);
			}
			return block;
		}
	}
}
