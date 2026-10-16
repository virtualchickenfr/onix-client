package gg.carbon.client.module.impl.hud;

import gg.carbon.client.module.HudModule;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.ModeSetting;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.HudStyle;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ArmorStatus extends HudModule {
	private final ModeSetting layout = add(new ModeSetting("Layout", "Arrangement of the items", "Vertical", "Vertical", "Horizontal"));
	private final ModeSetting durability = add(new ModeSetting("Durability", "How durability is displayed", "Value", "Value", "Percent", "Bar", "None"));
	private final BoolSetting held = add(new BoolSetting("Held Item", "Include the item in your hand", true));
	private final BoolSetting background = add(new BoolSetting("Background", "Draw a panel", true));
	private final NumberSetting bgOpacity = add(new NumberSetting("Background Opacity", "Panel transparency", 0.45, 0, 1, 0.05)).visibleIf(background::on);
	private final BoolSetting colorize = add(new BoolSetting("Color Durability", "Fade text towards grey as durability drops", true));

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	public ArmorStatus() {
		super("armorStatus", "Armor Status", "Shows durability of your armour and held item", 0.995f, 0.75f);
	}

	private List<ItemStack> items(boolean editor) {
		List<ItemStack> list = new ArrayList<>();
		if (mc.player != null) {
			for (EquipmentSlot slot : SLOTS) {
				ItemStack s = mc.player.getItemBySlot(slot);
				if (!s.isEmpty()) list.add(s);
			}
			if (held.on() && !mc.player.getMainHandItem().isEmpty()) list.add(mc.player.getMainHandItem());
		}
		if (list.isEmpty() && editor) {
			list.add(new ItemStack(Items.NETHERITE_HELMET));
			list.add(new ItemStack(Items.NETHERITE_CHESTPLATE));
			list.add(new ItemStack(Items.NETHERITE_LEGGINGS));
			list.add(new ItemStack(Items.NETHERITE_BOOTS));
			if (held.on()) list.add(new ItemStack(Items.NETHERITE_SWORD));
		}
		return list;
	}

	private String label(ItemStack s) {
		if (!s.isDamageableItem()) return s.getCount() > 1 ? String.valueOf(s.getCount()) : "";
		int left = s.getMaxDamage() - s.getDamageValue();
		return switch (durability.get()) {
			case "Percent" -> (left * 100 / s.getMaxDamage()) + "%";
			case "Value" -> String.valueOf(left);
			default -> "";
		};
	}

	private float cell(ItemStack s) {
		String l = label(s);
		return 16 + (l.isEmpty() ? 0 : 4 + Fonts.width(l, true, 1f));
	}

	private boolean vertical() {
		return layout.is("Vertical");
	}

	@Override
	public float width() {
		List<ItemStack> list = items(true);
		float pad = background.on() ? 8 : 0;
		if (vertical()) {
			float w = 16;
			for (ItemStack s : list) w = Math.max(w, cell(s));
			return w + pad;
		}
		float w = 0;
		for (ItemStack s : list) w += cell(s) + 4;
		return Math.max(16, w - 4) + pad;
	}

	@Override
	public float height() {
		int n = Math.max(1, items(true).size());
		float pad = background.on() ? 8 : 0;
		return (vertical() ? n * 18 - 2 : 16 + (durability.is("Bar") ? 3 : 0)) + pad;
	}

	@Override
	public void render(GuiGraphics g, boolean editor) {
		List<ItemStack> list = items(editor);
		if (list.isEmpty()) return;
		if (background.on()) HudStyle.panel(g, 0, 0, width(), height(), bgOpacity.f());
		float x = background.on() ? 4 : 0, y = background.on() ? 4 : 0;
		for (ItemStack s : list) {
			R2D.push(g);
			R2D.translate(g, x, y);
			g.renderItem(s, 0, 0);
			R2D.pop(g);
			float frac = s.isDamageableItem() ? 1f - (float) s.getDamageValue() / s.getMaxDamage() : 1f;
			if (durability.is("Bar") && s.isDamageableItem()) {
				R2D.rrect(g, x + 1, y + 16.5f, 14, 2, 1, 0x55FFFFFF);
				R2D.rrect(g, x + 1, y + 16.5f, 14 * frac, 2, 1, Theme.WHITE);
			}
			String l = label(s);
			if (!l.isEmpty()) {
				int col = colorize.on() ? Theme.lerp(0xFF6A6A70, 0xFFFFFFFF, frac) : Theme.WHITE;
				Fonts.draw(g, l, x + 20, y + 4.5f, col, true, 1f, false);
			}
			if (vertical()) y += 18;
			else x += cell(s) + 4;
		}
	}
}
