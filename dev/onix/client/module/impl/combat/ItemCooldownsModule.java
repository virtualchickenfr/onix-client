package dev.onix.client.module.impl.combat;

import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Cooldowns of ender pearls, chorus fruit, shields and wind charges. */
public class ItemCooldownsModule extends HudModule {
    private final BoolSetting pearl = add(new BoolSetting("pearl", "Ender Pearl", true));
    private final BoolSetting chorus = add(new BoolSetting("chorus", "Chorus Fruit", true));
    private final BoolSetting shield = add(new BoolSetting("shield", "Shield", true));
    private final BoolSetting windCharge = add(new BoolSetting("windCharge", "Wind Charge", true));
    private final BoolSetting goatHorn = add(new BoolSetting("goatHorn", "Goat Horn", false));
    private final BoolSetting seconds = add(new BoolSetting("seconds", "Show Seconds", true));
    private final ColorSetting barColor = add(ColorSetting.accent("barColor", "Bar Color"));
    private final ColorSetting trackColor = add(new ColorSetting("trackColor", "Track Color", 0x40FFFFFF));

    public ItemCooldownsModule() {
        super("itemcooldowns", "Item Cooldowns", "Cooldown bars for pearls, shields and more.", Category.COMBAT, false, Anchor.CENTER, -60, 30);
        tags("pearl cooldown", "shield", "timer");
    }

    private List<Item> active(boolean preview) {
        List<Item> list = new ArrayList<>();
        if (pearl.isOn()) list.add(Items.ENDER_PEARL);
        if (chorus.isOn()) list.add(Items.CHORUS_FRUIT);
        if (shield.isOn()) list.add(Items.SHIELD);
        if (windCharge.isOn()) list.add(Items.WIND_CHARGE);
        if (goatHorn.isOn()) list.add(Items.GOAT_HORN);
        if (preview) return list.isEmpty() ? list : list.subList(0, Math.min(2, list.size()));
        list.removeIf(item -> progress(item) <= 0);
        return list;
    }

    private float progress(Item item) {
        return mc().player == null ? 0 : mc().player.getCooldowns().getCooldownPercent(item, 0f);
    }

    @Override
    public boolean hasContent() {
        return !active(false).isEmpty();
    }

    @Override
    public int getWidth() {
        return pad() * 2 + 18 + 40 + (seconds.isOn() ? textWidth("0.0s") + 4 : 0);
    }

    @Override
    public int getHeight() {
        return pad() * 2 + Math.max(1, active(true).size()) * 18;
    }

    @Override
    public void render(GuiGraphics ctx, boolean preview) {
        drawBackground(ctx, getWidth(), getHeight());
        int y = pad();
        for (Item item : active(preview)) {
            float p = preview ? 0.5f : progress(item);
            ctx.renderItem(new ItemStack(item), pad(), y);
            int bx = pad() + 18;
            Theme.round(ctx, bx, y + 7, bx + 40, y + 10, 1, trackColor.color());
            Theme.round(ctx, bx, y + 7, bx + Math.max(1, Math.round(40 * p)), y + 10, 1, barColor.color());
            if (seconds.isOn()) {
                // vanilla cooldowns: pearl 20 ticks, chorus 20, shield 100 (axe), wind charge 10
                float total = item == Items.SHIELD ? 5f : item == Items.WIND_CHARGE ? 0.5f : item == Items.GOAT_HORN ? 7f : 1f;
                drawText(ctx, String.format(java.util.Locale.ROOT, "%.1fs", p * total), bx + 44, y + 4, 0xFFFFFFFF);
            }
            y += 18;
        }
    }
}
