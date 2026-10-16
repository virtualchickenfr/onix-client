package gg.carbon.client.screen.widget;

import com.mojang.blaze3d.platform.InputConstants;
import gg.carbon.client.ui.Fonts;
import gg.carbon.client.ui.R2D;
import gg.carbon.client.ui.Theme;
import gg.carbon.client.ui.anim.Smooth;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Minimal single line text input that for GLFW key input. */
public class TextField {
	private String text;
	private final String placeholder;
	private final int maxLength;
	private final Consumer<String> onChange;
	private boolean focused;
	private final Smooth focus = new Smooth(0, 16f);
	private long lastType;

	public TextField(String text, String placeholder, int maxLength, Consumer<String> onChange) {
		this.text = text;
		this.placeholder = placeholder;
		this.maxLength = maxLength;
		this.onChange = onChange;
	}

	public String text() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
		onChange.accept(text);
	}

	public boolean focused() {
		return focused;
	}

	public void setFocused(boolean f) {
		if (f == focused) return;
		focused = f;
	}

	public void render(GuiGraphics g, float x, float y, float w, float h, double mx, double my, boolean drawBackground) {
		focus.target(focused ? 1 : 0);
		float f = focus.get();
		if (drawBackground) {
			R2D.rrect(g, x, y, w, h, 3.5f, Theme.lerp(0xFF161619, 0xFF1D1D21, f));
			R2D.outline(g, x, y, w, h, 3.5f, 0.75f, Theme.alpha(0xFFFFFFFF, 0.08f + 0.25f * f));
		}
		float tx = x + h / 2f + 2;
		float ty = y + (h - 8) / 2f;
		float avail = w - h - 6;
		String shown = text;
		while (Fonts.width(shown, false, 0.9f) > avail && !shown.isEmpty()) shown = shown.substring(1);
		g.enableScissor((int) x, (int) y, (int) (x + w), (int) (y + h));
		if (text.isEmpty() && !focused) {
			Fonts.draw(g, placeholder, tx, ty, Theme.TEXT_MUTED, false, 0.9f, false);
		} else {
			Fonts.draw(g, shown, tx, ty, Theme.TEXT, false, 0.9f, false);
		}
		if (focused) {
			boolean blink = (System.currentTimeMillis() - lastType) < 500 || (System.currentTimeMillis() / 500) % 2 == 0;
			if (blink) R2D.rect(g, tx + Fonts.width(shown, false, 0.9f) + 0.5f, ty - 1, 0.75f, 9, Theme.WHITE);
		}
		g.disableScissor();
	}

	public boolean keyPressed(int key, boolean ctrl) {
		if (!focused) return false;
		if (key == InputConstants.KEY_BACKSPACE) {
			if (!text.isEmpty()) {
				if (ctrl) {
					String t = text.stripTrailing();
					int i = t.lastIndexOf(' ');
					setText(i < 0 ? "" : t.substring(0, i + 1));
				} else {
					setText(text.substring(0, text.length() - 1));
				}
			}
			lastType = System.currentTimeMillis();
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			setFocused(false);
			return true;
		}
		if (ctrl && key == InputConstants.KEY_V) {
			String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
			insert(clip.replace("\n", " "));
			return true;
		}
		if (ctrl && key == InputConstants.KEY_A) {
			setText("");
			return true;
		}
		return true;
	}

	public boolean charTyped(String chars) {
		if (!focused) return false;
		insert(chars);
		return true;
	}

	private void insert(String s) {
		StringBuilder b = new StringBuilder(text);
		for (char c : s.toCharArray()) {
			if (c >= 32 && c != 127 && b.length() < maxLength) b.append(c);
		}
		lastType = System.currentTimeMillis();
		setText(b.toString());
	}
}
