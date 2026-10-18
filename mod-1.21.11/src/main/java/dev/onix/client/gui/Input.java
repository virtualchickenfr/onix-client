package dev.onix.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * A minimal text field drawn in the Onix font. The caret always sits at the end,
 * which is all search fields and short names need, and keeps font and caret aligned.
 */
public final class Input {
    private String placeholder;
    private int maxLength;
    private String text = "";
    private boolean focused;
    private Runnable onChange = () -> {};
    private Runnable onSubmit = () -> {};

    public Input(String placeholder, int maxLength) {
        this.placeholder = placeholder;
        this.maxLength = maxLength;
    }

    public Input placeholder(String text) {
        this.placeholder = text;
        return this;
    }

    public Input onChange(Runnable r) {
        this.onChange = r;
        return this;
    }

    public Input onSubmit(Runnable r) {
        this.onSubmit = r;
        return this;
    }

    public Input maxLength(int max) {
        this.maxLength = max;
        return this;
    }

    public String text() {
        return text;
    }

    public void setText(String value) {
        String v = value == null ? "" : value;
        if (v.length() > maxLength) v = v.substring(0, maxLength);
        if (!v.equals(text)) {
            text = v;
            onChange.run();
        }
    }

    public boolean focused() {
        return focused;
    }

    public void focus(boolean value) {
        focused = value;
    }

    /** Draws the text (or the placeholder) at (x, y), cut to maxWidth. */
    public void render(GuiGraphics ctx, int x, int y, int maxWidth, int color, int placeholderColor) {
        if (text.isEmpty()) {
            Theme.text(ctx, Theme.ellipsize(placeholder, maxWidth), x, y, placeholderColor);
        } else {
            String shown = text;
            while (shown.length() > 1 && Theme.width(shown) > maxWidth - 4) shown = shown.substring(1);
            Theme.text(ctx, shown, x, y, color);
        }
        if (focused && (System.currentTimeMillis() / 530) % 2 == 0) {
            int cx = x + (text.isEmpty() ? 0 : Math.min(Theme.width(text), maxWidth - 3)) + 1;
            ctx.fill(cx, y - 1, cx + 1, y + 9, Theme.accent());
        }
    }

    /** @return true if the key was consumed. */
    public boolean keyPressed(int key, int modifiers) {
        if (!focused) return false;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!text.isEmpty()) {
                    if (ctrl) {
                        String t = text.stripTrailing();
                        int i = t.lastIndexOf(' ');
                        setText(i < 0 ? "" : t.substring(0, i + 1));
                    } else {
                        setText(text.substring(0, text.length() - 1));
                    }
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                onSubmit.run();
                return true;
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    setText(text + clip.replaceAll("[\\r\\n\\t]", " "));
                    return true;
                }
                return true;
            }
            case GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_X -> {
                return true; // letters arrive through charTyped
            }
            default -> {
                return key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_TAB;
            }
        }
    }

    public boolean charTyped(char chr) {
        if (!focused || chr < 32 || chr == 127) return false;
        setText(text + chr);
        return true;
    }
}
