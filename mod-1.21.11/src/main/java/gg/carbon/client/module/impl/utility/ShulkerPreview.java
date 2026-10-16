package gg.carbon.client.module.impl.utility;

import com.mojang.blaze3d.platform.InputConstants;
import gg.carbon.client.module.Category;
import gg.carbon.client.module.Module;
import gg.carbon.client.setting.BoolSetting;
import gg.carbon.client.setting.ModeSetting;
import gg.carbon.client.setting.NumberSetting;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.Fonts.Face;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/** Dawn "Shulker Tooltips": shows the contents of a shulker box when hovering it in any inventory. */
public class ShulkerPreview extends Module {
	private final ModeSetting trigger = add(new ModeSetting("Show", "When the preview appears", "Always", "Always", "Hold Shift", "Hold Alt"));
	private final ModeSetting layout = add(new ModeSetting("Layout", "Grid mirrors the box, Compact merges equal items", "Grid", "Grid", "Compact"));
	private final ModeSetting tint = add(new ModeSetting("Frame Color", "Colour of the preview frame", "Monochrome", "Monochrome", "Shulker Color"));
	private final BoolSetting title = add(new BoolSetting("Show Title", "Show the box name above the items", true));
	private final BoolSetting emptySlots = add(new BoolSetting("Show Empty Slots", "Draw empty slots in grid layout", true)).visibleIf(() -> layout.is("Grid"));
	private final ModeSetting counts = add(new ModeSetting("Stack Counts", "How item amounts are written", "Vanilla", "Vanilla", "Short", "Stacks"));
	private final BoolSetting totals = add(new BoolSetting("Show Totals", "Footer with item and stack totals", true));
	private final BoolSetting emptyHint = add(new BoolSetting("Preview Empty Boxes", "Show the preview even when the box is empty", false));
	private final NumberSetting opacity = add(new NumberSetting("Background Opacity", "Transparency of the panel", 0.94, 0.4, 1, 0.02));
	private final NumberSetting scale = add(new NumberSetting("Scale", "Size of the preview", 1, 0.6, 1.6, 0.05));

	public ShulkerPreview() {
		super("shulkertooltips", "Shulker Preview", "See inside shulker boxes from the tooltip", Category.UTILITY);
	}

	private static boolean isShulker(ItemStack stack) {
		return stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
	}

	private boolean triggered() {
		return switch (trigger.get()) {
			case "Hold Shift" -> InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), InputConstants.KEY_RSHIFT);
			case "Hold Alt" -> InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), InputConstants.KEY_LALT) || InputConstants.isKeyDown(net.minecraft.client.Minecraft.getInstance().getWindow(), InputConstants.KEY_RALT);
			default -> true;
		};
	}

	private static NonNullList<ItemStack> contents(ItemStack stack) {
		NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
		ItemContainerContents c = stack.get(DataComponents.CONTAINER);
		if (c != null) c.copyInto(items);
		return items;
	}

	/** Whether the preview replaces the vanilla tooltip for this stack right now. */
	public boolean handles(ItemStack stack) {
		if (!enabled() || !isShulker(stack) || !triggered()) return false;
		if (emptyHint.on()) return true;
		for (ItemStack s : contents(stack)) if (!s.isEmpty()) return true;
		return false;
	}

	private String countText(int n) {
		return switch (counts.get()) {
			case "Short" -> n >= 1000 ? String.format(java.util.Locale.ROOT, "%.1fk", n / 1000f) : String.valueOf(n);
			case "Stacks" -> n > 64 ? (n / 64) + "x64" + (n % 64 > 0 ? "+" : "") : String.valueOf(n);
			default -> String.valueOf(n);
		};
	}

	public void render(GuiGraphics g, ItemStack box, int mouseX, int mouseY, int screenW, int screenH) {
		NonNullList<ItemStack> items = contents(box);
		List<ItemStack> shown = new ArrayList<>();
		int stacks = 0, total = 0;
		for (ItemStack s : items) {
			if (s.isEmpty()) continue;
			stacks++;
			total += s.getCount();
		}
		boolean compact = layout.is("Compact");
		if (compact) {
			for (ItemStack s : items) {
				if (s.isEmpty()) continue;
				boolean merged = false;
				for (ItemStack m : shown) {
					if (ItemStack.isSameItemSameComponents(m, s)) {
						m.setCount(m.getCount() + s.getCount());
						merged = true;
						break;
					}
				}
				if (!merged) shown.add(s.copy());
			}
			shown.sort((a, b) -> Integer.compare(b.getCount(), a.getCount()));
		} else {
			shown.addAll(items);
		}

		int cols = compact ? Math.max(1, Math.min(9, shown.size())) : 9;
		int rows = compact ? Math.max(1, (shown.size() + 8) / 9) : 3;
		float pad = 6, cell = 18;
		float titleH = title.on() ? 16 : 0;
		float footH = totals.on() ? 13 : 0;
		float w = pad * 2 + cols * cell;
		String name = box.getHoverName().getString();
		if (title.on()) w = Math.max(w, pad * 2 + Fonts.width(name, Face.BODY_BOLD, 0.9f) + 40);
		float h = pad * 2 + titleH + rows * cell + footH;

		float s = scale.f();
		float x = mouseX + 12, y = mouseY - 12;
		if (x + w * s > screenW - 4) x = mouseX - 12 - w * s;
		if (y + h * s > screenH - 4) y = screenH - 4 - h * s;
		x = Math.max(4, x);
		y = Math.max(4, y);

		DyeColor dye = box.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock sb ? sb.getColor() : null;
		int accent = tint.is("Shulker Color") && dye != null ? (0xFF000000 | dye.getTextureDiffuseColor()) : 0xFFD8D8DC;

		g.nextStratum();
		R2D.push(g);
		R2D.translate(g, x, y);
		if (s != 1f) g.pose().scale(s, s);

		int bg = Theme.withAlpha(0x0C0C0E, Math.round(opacity.f() * 255));
		R2D.shadow(g, 0, 2, w, h, 6, 8, 0x80000000);
		R2D.rrect(g, 0, 0, w, h, 6, bg);
		R2D.outline(g, 0, 0, w, h, 6, 1, Theme.alpha(accent, 0.55f));
		R2D.rect(g, 6, 0.75f, w - 12, 0.75f, Theme.alpha(accent, 0.25f));

		float cy = pad;
		if (title.on()) {
			R2D.rect(g, pad, pad + 4, 3, 3, accent);
			Fonts.draw(g, name, pad + 7, pad + 1, Theme.WHITE, Face.BODY_BOLD, 0.9f, false);
			String fill = stacks + "/27";
			Fonts.draw(g, fill, w - pad - Fonts.width(fill, Face.MONO, 0.72f), pad + 2, 0xFF8E8E98, Face.MONO, 0.72f, false);
			cy += titleH;
		}

		for (int i = 0; i < (compact ? shown.size() : 27); i++) {
			float sx = pad + (i % cols) * cell, sy = cy + (i / cols) * cell;
			ItemStack it = i < shown.size() ? shown.get(i) : ItemStack.EMPTY;
			if (!compact && (emptySlots.on() || !it.isEmpty())) {
				R2D.rrect(g, sx + 0.5f, sy + 0.5f, cell - 1, cell - 1, 2, 0x50000000);
				R2D.outline(g, sx + 0.5f, sy + 0.5f, cell - 1, cell - 1, 2, 0.75f, 0x14FFFFFF);
			} else if (compact) {
				R2D.rrect(g, sx + 0.5f, sy + 0.5f, cell - 1, cell - 1, 2, 0x50000000);
			}
			if (it.isEmpty()) continue;
			R2D.push(g);
			R2D.translate(g, sx + 1, sy + 1);
			g.renderItem(it, 0, 0);
			String text = it.getCount() > 1 || compact ? countText(it.getCount()) : null;
			g.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, it, 0, 0, text);
			R2D.pop(g);
		}

		if (totals.on()) {
			String foot = total + (total == 1 ? " item" : " items") + "  ·  " + stacks + (stacks == 1 ? " stack" : " stacks");
			Fonts.draw(g, foot, pad, h - pad - 8.5f, 0xFF7E7E88, Face.BODY, 0.75f, false);
		}
		R2D.pop(g);
	}
}
