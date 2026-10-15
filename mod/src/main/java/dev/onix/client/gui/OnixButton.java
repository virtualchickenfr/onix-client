package dev.onix.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Flat, rounded button in the Onix style. */
public class OnixButton extends ButtonWidget {
    private final boolean primary;
    private final Anim hover = new Anim(0);

    public OnixButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        this(x, y, width, height, message, onPress, false);
    }

    public OnixButton(int x, int y, int width, int height, Text message, PressAction onPress, boolean primary) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.primary = primary;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float t = hover.to((isHovered() || isFocused()) && active ? 1f : 0f, 18f);
        int x1 = getX(), y1 = getY(), x2 = x1 + getWidth(), y2 = y1 + getHeight();

        int fill, border, text;
        if (!active) {
            fill = Theme.SURFACE_2;
            border = Theme.BORDER;
            text = Theme.TEXT_3;
        } else if (primary) {
            fill = Theme.lerpColor(Theme.accent(), Theme.lerpColor(Theme.accent(), 0xFFFFFFFF, 0.18f), t);
            border = fill;
            text = Theme.onAccent();
        } else {
            fill = Theme.lerpColor(Theme.SURFACE_2, Theme.SURFACE_3, t);
            border = Theme.lerpColor(Theme.BORDER, Theme.BORDER_STRONG, t);
            text = Theme.TEXT;
        }
        Theme.card(ctx, x1, y1, x2, y2, 6, fill, border);
        Text label = Theme.bold(getMessage().getString());
        int w = Theme.width(label);
        ctx.drawText(Theme.font(), label, x1 + (getWidth() - w) / 2, y1 + (getHeight() - 8) / 2 + 1, text, false);
    }
}
