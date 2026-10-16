package gg.carbon.client.module.impl.pvp;

import gg.carbon.client.module.Category;
import gg.carbon.client.module.HudModule;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.ModeSetting;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.HudStyle;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.util.Stats;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Counts important PvP items in your inventory (Dawn: itemCounter + totem). */
public class ItemCounter extends HudModule {
	private final BoolSetting totems = add(new BoolSetting("Totems", "Count totems of undying", true));
	private final BoolSetting pearls = add(new BoolSetting("Ender Pearls", "Count ender pearls", true));
	private final BoolSetting gapples = add(new BoolSetting("Golden Apples", "Count golden apples", true));
	private final BoolSetting arrows = add(new BoolSetting("Arrows", "Count arrows", true));
	private final BoolSetting crystals = add(new BoolSetting("End Crystals", "Count end crystals", false));
	private final BoolSetting xp = add(new BoolSetting("XP Bottles", "Count bottles o' enchanting", false));
	private final BoolSetting pops = add(new BoolSetting("Totem Pops", "Show how many totems you popped", true));
	private final BoolSetting hideEmpty = add(new BoolSetting("Hide Empty", "Hide items you have none of", true));
	private final ModeSetting layout = add(new ModeSetting("Layout", "Arrangement", "Horizontal", "Horizontal", "Vertical"));
	private final NumberSetting bgOpacity = add(new NumberSetting("Background Opacity", "Panel transparency", 0.45, 0, 1, 0.05));

	public ItemCounter() {
		super("itemCounter", "Item Counter", "Counts totems, pearls, gapples and more", Category.PVP, 0.6f, 0.86f);
	}

	private record Entry(ItemStack icon, String text) {}

	private List<Entry> entries(boolean editor) {
		List<Entry> list = new ArrayList<>();
		add(list, totems.on(), Items.TOTEM_OF_UNDYING, editor);
		add(list, pearls.on(), Items.ENDER_PEARL, editor);
		add(list, gapples.on(), Items.GOLDEN_APPLE, editor);
		add(list, arrows.on(), Items.ARROW, editor);
		add(list, crystals.on(), Items.END_CRYSTAL, editor);
		add(list, xp.on(), Items.EXPERIENCE_BOTTLE, editor);
		if (pops.on() && (Stats.totemPops() > 0 || editor)) list.add(new Entry(ItemStack.EMPTY, Stats.totemPops() + " pops"));
		return list;
	}

	private void add(List<Entry> list, boolean on, Item item, boolean editor) {
		if (!on) return;
		int n = count(item);
		if (n == 0 && item == Items.GOLDEN_APPLE) n = count(Items.ENCHANTED_GOLDEN_APPLE);
		if (n == 0 && hideEmpty.on() && !editor) return;
		list.add(new Entry(new ItemStack(item), String.valueOf(editor && n == 0 ? 16 : n)));
	}

	private int count(Item item) {
		if (mc.player == null) return 0;
		Inventory inv = mc.player.getInventory();
		int n = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.is(item)) n += s.getCount();
		}
		return n;
	}

	private float cell(Entry e) {
		return (e.icon().isEmpty() ? 0 : 18) + Fonts.width(e.text(), true, 1f);
	}

	@Override
	public boolean shouldRender() {
		return !entries(false).isEmpty();
	}

	@Override
	public float width() {
		List<Entry> list = entries(true);
		if (layout.is("Vertical")) {
			float w = 20;
			for (Entry e : list) w = Math.max(w, cell(e));
			return w + 10;
		}
		float w = 0;
		for (Entry e : list) w += cell(e) + 8;
		return Math.max(20, w - 8) + 10;
	}

	@Override
	public float height() {
		int n = Math.max(1, entries(true).size());
		return layout.is("Vertical") ? n * 18 + 6 : 24;
	}

	@Override
	public void render(GuiGraphics g, boolean editor) {
		List<Entry> list = entries(editor);
		HudStyle.panel(g, 0, 0, width(), height(), bgOpacity.f());
		float x = 5, y = 4;
		for (Entry e : list) {
			float tx = x;
			if (!e.icon().isEmpty()) {
				R2D.push(g);
				R2D.translate(g, x, y);
				g.renderItem(e.icon(), 0, 0);
				R2D.pop(g);
				tx += 18;
			}
			Fonts.draw(g, e.text(), tx, y + 4.5f, Theme.WHITE, true, 1f, false);
			if (layout.is("Vertical")) y += 18;
			else x += cell(e) + 8;
		}
	}
}
