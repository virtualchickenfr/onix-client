package dev.onix.client.module;

import dev.onix.client.gui.Theme;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Shows an item icon and how many of it are in the inventory. */
public abstract class ItemCounterModule extends HudModule {
    protected final ModeSetting layout;
    protected final BoolSetting hideWhenZero;
    protected final NumberSetting warnBelow;
    protected final ColorSetting countColor;
    protected final ColorSetting warnColor;
    protected final BoolSetting showLabel;

    protected ItemCounterModule(String id, String name, String description, Anchor anchor, int x, int y) {
        super(id, name, description, Category.COMBAT, false, anchor, x, y);
        layout = add(new ModeSetting("layout", "Layout", "Icon + Count", "Count + Icon", "Icon Only", "Text Only"));
        showLabel = add(new BoolSetting("label", "Show Name", false)
                .describe("Add the item name next to the count."));
        hideWhenZero = add(new BoolSetting("hideZero", "Hide When Empty", true));
        warnBelow = add(new NumberSetting("warnBelow", "Warn Below", 0, 0, 64, 1)
                .describe("Count turns into the warning color at or below this number. 0 = off."));
        countColor = add(new ColorSetting("countColor", "Count Color", 0xFFFFFFFF));
        warnColor = add(new ColorSetting("warnColor", "Warning Color", 0xFFFF5A5F).visibleIf(() -> warnBelow.getInt() > 0));
    }

    /** Item whose icon is drawn. */
    protected abstract Item icon();

    /** How many there are right now. */
    protected abstract int count();

    protected String label() {
        return getName();
    }

    @Override
    public boolean hasContent() {
        return !hideWhenZero.isOn() || count() > 0;
    }

    private String text(int c) {
        return showLabel.isOn() ? c + " " + label() : String.valueOf(c);
    }

    private boolean showIcon() {
        return !layout.is("Text Only");
    }

    private boolean showCount() {
        return !layout.is("Icon Only");
    }

    @Override
    public int getWidth() {
        int w = pad() * 2;
        if (showIcon()) w += 16;
        if (showCount()) w += textWidth(text(Math.max(count(), 99))) + (showIcon() ? 3 : 0);
        return w;
    }

    @Override
    public int getHeight() {
        return Math.max(16, 9) + pad() * 2;
    }

    @Override
    public void render(GuiGraphics ctx, boolean preview) {
        int c = count();
        if (preview && c == 0) c = 16;
        int w = getWidth();
        int h = getHeight();
        drawBackground(ctx, w, h);
        int warn = warnBelow.getInt();
        int color = warn > 0 && c <= warn ? warnColor.color() : countColor.color();
        String label = text(c);
        int x = pad();
        boolean countFirst = layout.is("Count + Icon");
        if (showCount() && countFirst) {
            x = drawText(ctx, label, x, (h - 8) / 2, color) + 3;
        }
        if (showIcon()) {
            ctx.renderItem(new ItemStack(icon()), x, (h - 16) / 2);
            x += 19;
        }
        if (showCount() && !countFirst) drawText(ctx, label, x, (h - 8) / 2, color);
        if (layout.is("Icon Only") && c > 1) {
            ctx.pose().pushMatrix();
            ctx.pose().translate(0, 0);
            String s = String.valueOf(c);
            ctx.drawString(Theme.font(), s, pad() + 17 - Theme.font().width(s), (h - 16) / 2 + 9, color, true);
            ctx.pose().popMatrix();
        }
    }
}
