package dev.onix.client.module.impl.hud;

import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Lists items you just picked up, e.g. "+12 Iron Ingot". */
public class PickupNotifierModule extends HudModule {
    private static final class Entry {
        final Item item;
        int amount;
        long time;

        Entry(Item item, int amount, long time) {
            this.item = item;
            this.amount = amount;
            this.time = time;
        }
    }

    private final NumberSetting duration = add(new NumberSetting("duration", "Show For", 3, 1, 10, 0.5).unit("s"));
    private final NumberSetting maxLines = add(new NumberSetting("max", "Max Lines", 5, 1, 12, 1));
    private final BoolSetting icons = add(new BoolSetting("icons", "Item Icons", true));
    private final BoolSetting losses = add(new BoolSetting("losses", "Show Losses", false).describe("Also list items that left your inventory."));
    private final ColorSetting gainColor = add(new ColorSetting("gainColor", "Gain Color", 0xFF34D399));
    private final ColorSetting lossColor = add(new ColorSetting("lossColor", "Loss Color", 0xFFFF5A5F).visibleIf(losses::isOn));
    private final ColorSetting nameColor = add(new ColorSetting("nameColor", "Name Color", 0xFFFFFFFF));

    private final List<Entry> entries = new ArrayList<>();
    private Map<Item, Integer> lastCounts;

    public PickupNotifierModule() {
        super("pickups", "Pickup Notifier", "Shows the items you just picked up.", Category.HUD, false, Anchor.MIDDLE_RIGHT, -4, 80);
        tags("loot", "items", "collect");
    }

    @Override
    protected void onEnable() {
        lastCounts = null;
    }

    private Map<Item, Integer> counts() {
        Map<Item, Integer> map = new HashMap<>();
        Inventory inv = mc().player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) map.merge(s.getItem(), s.getCount(), Integer::sum);
        }
        ItemStack cursor = mc().player.containerMenu.getCarried();
        if (!cursor.isEmpty()) map.merge(cursor.getItem(), cursor.getCount(), Integer::sum);
        return map;
    }

    @Override
    public void onTick() {
        if (mc().player == null) {
            lastCounts = null;
            return;
        }
        Map<Item, Integer> now = counts();
        long time = System.currentTimeMillis();
        if (lastCounts != null) {
            for (Map.Entry<Item, Integer> e : now.entrySet()) {
                int diff = e.getValue() - lastCounts.getOrDefault(e.getKey(), 0);
                if (diff > 0 || (diff < 0 && losses.isOn())) add(e.getKey(), diff, time);
            }
            if (losses.isOn()) {
                for (Map.Entry<Item, Integer> e : lastCounts.entrySet()) {
                    if (!now.containsKey(e.getKey())) add(e.getKey(), -e.getValue(), time);
                }
            }
        }
        lastCounts = now;
        entries.removeIf(e -> time - e.time > duration.get() * 1000);
    }

    private void add(Item item, int amount, long time) {
        for (Entry e : entries) {
            if (e.item == item && Integer.signum(e.amount) == Integer.signum(amount)) {
                e.amount += amount;
                e.time = time;
                return;
            }
        }
        entries.add(0, new Entry(item, amount, time));
        while (entries.size() > maxLines.getInt()) entries.remove(entries.size() - 1);
    }

    private List<Entry> shown(boolean preview) {
        if (entries.isEmpty() && preview) {
            List<Entry> sample = new ArrayList<>();
            sample.add(new Entry(Items.IRON_INGOT, 12, System.currentTimeMillis()));
            sample.add(new Entry(Items.DIAMOND, 2, System.currentTimeMillis()));
            return sample;
        }
        return entries;
    }

    private String text(Entry e) {
        return (e.amount > 0 ? "+" : "") + e.amount + " ";
    }

    @Override
    public boolean hasContent() {
        return !entries.isEmpty();
    }

    @Override
    public int getWidth() {
        int w = 60;
        for (Entry e : shown(true)) w = Math.max(w, textWidth(text(e)) + textWidth(new ItemStack(e.item).getHoverName().getString()));
        return w + pad() * 2 + (icons.isOn() ? 18 : 0);
    }

    @Override
    public int getHeight() {
        return pad() * 2 + Math.max(1, shown(true).size()) * (icons.isOn() ? 17 : 11);
    }

    @Override
    public void render(GuiGraphics ctx, boolean preview) {
        drawBackground(ctx, getWidth(), getHeight());
        int y = pad();
        long now = System.currentTimeMillis();
        int row = icons.isOn() ? 17 : 11;
        Iterator<Entry> it = shown(preview).iterator();
        while (it.hasNext()) {
            Entry e = it.next();
            float age = (now - e.time) / (float) (duration.get() * 1000);
            float alpha = preview ? 1f : Math.max(0.15f, Math.min(1f, (1f - age) * 3f));
            int x = pad();
            if (icons.isOn()) {
                ctx.renderItem(new ItemStack(e.item), x, y);
                x += 18;
            }
            int ty = y + (row - 8) / 2;
            x = drawText(ctx, text(e), x, ty, Theme.fade(e.amount > 0 ? gainColor.color() : lossColor.color(), alpha));
            drawText(ctx, new ItemStack(e.item).getHoverName().getString(), x, ty, Theme.fade(nameColor.color(), alpha));
            y += row;
        }
    }
}
