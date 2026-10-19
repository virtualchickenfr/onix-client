package dev.onix.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Flat, rounded button in the Onix style. */
public class OnixButton extends Button {
    private final boolean primary;
    private final Anim hover = new Anim(0);
    private final Anim sheen = new Anim(0);
    private static boolean block() {
        return dev.onix.client.OnixClient.CONFIG != null && dev.onix.client.OnixClient.CONFIG.buttonStyle.is("Block");
    }

    public OnixButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, false);
    }

    public OnixButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean primary) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.primary = primary;
    }

    @Override
    protected void renderContents(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
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
        Component label = Theme.bold(getMessage().getString());
        int w = Theme.width(label);
        if (block()) {
            // Carbon block: a darker depth band below the face; hover lifts the face, press pushes it down
            float sh = sheen.to(t > 0.5f ? 1f : 0f, t > 0.5f ? 3.2f : 100f);
            int depth = 3;
            int lift = -Math.round(t);
            int dark = Theme.lerpColor(fill, 0xFF000000, Theme.isLight() ? 0.28f : 0.55f);
            int fy = y1 + lift;
            Theme.card(ctx, x1, y1 + 1, x2, y2, 6, dark, dark);
            Theme.card(ctx, x1, fy, x2, y2 - depth + lift, 6, fill, border);
            ctx.fill(x1 + 5, fy + 1, x2 - 5, fy + 2, Theme.isLight() ? 0x50FFFFFF : 0x16FFFFFF);
            if (active && sh > 0.02f && sh < 0.98f) {
                int bw = Math.max(8, getWidth() * 2 / 5);
                int bx = x1 - bw + Math.round((getWidth() + bw) * sh);
                ctx.enableScissor(x1 + 3, fy + 1, x2 - 3, y2 - depth + lift - 1);
                for (int i = 0; i < bw; i++) {
                    float k = 1f - Math.abs(i - bw / 2f) / (bw / 2f);
                    int a = Math.round(k * 0x20);
                    if (a > 0) ctx.fill(bx + i, fy + 1, bx + i + 1, y2 - depth + lift - 1, a << 24 | 0xFFFFFF);
                }
                ctx.disableScissor();
            }
            ctx.drawString(Theme.font(), label, x1 + (getWidth() - w) / 2, fy + (getHeight() - depth - 8) / 2 + 1, text, false);
            return;
        }
        Theme.card(ctx, x1, y1, x2, y2, 6, fill, border);
        ctx.drawString(Theme.font(), label, x1 + (getWidth() - w) / 2, y1 + (getHeight() - 8) / 2 + 1, text, false);
    }
}
