package dev.onix.client.module.impl.hud;

import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.CpsTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;

public class KeystrokesModule extends HudModule {
    private final NumberSetting keySize = add(new NumberSetting("keySize", "Key Size", 22, 14, 36, 1).unit("px"));
    private final NumberSetting gap = add(new NumberSetting("gap", "Gap", 2, 0, 6, 1).unit("px"));
    private final BoolSetting showMouse = add(new BoolSetting("mouse", "Mouse Buttons", true));
    private final BoolSetting showCps = add(new BoolSetting("cps", "CPS in Mouse Buttons", true).visibleIf(showMouse::isOn));
    private final BoolSetting showSpace = add(new BoolSetting("space", "Space Bar", true));
    private final BoolSetting showModifiers = add(new BoolSetting("modifiers", "Sneak & Sprint Keys", false));
    private final ModeSetting keyLabels = add(new ModeSetting("labels", "Key Labels", "Bound Keys", "Arrows"));

    {
        group("Colors");
    }

    private final ColorSetting idleColor = add(new ColorSetting("idleColor", "Key Color", 0x73000000));
    private final ColorSetting pressedColor = add(ColorSetting.accent("pressedColor", "Pressed Color"));
    private final ColorSetting textColor = add(new ColorSetting("textColor", "Text Color", 0xFFFFFFFF));
    private final ColorSetting pressedTextColor = add(new ColorSetting("pressedTextColor", "Pressed Text Color", 0xFF0B0B0E));
    private final NumberSetting fadeSpeed = add(new NumberSetting("fade", "Fade Speed", 22, 4, 60, 1)
            .describe("How quickly keys light up and fade out."));

    private final float[] fade = new float[9];
    private long lastFrame = System.nanoTime();

    public KeystrokesModule() {
        super("keystrokes", "Keystrokes", "Shows WASD, mouse buttons and space bar as you press them.", Category.HUD, true, Anchor.TOP_RIGHT, -4, 4);
        tags("keys", "wasd", "inputs");
        background.withDefault(false);
    }

    private int key() { return keySize.getInt(); }
    private int g() { return gap.getInt(); }
    private int fullWidth() { return key() * 3 + g() * 2; }

    @Override
    public int getWidth() {
        return fullWidth();
    }

    @Override
    public int getHeight() {
        int h = key() * 2 + g();
        if (showMouse.isOn()) h += g() + key();
        if (showModifiers.isOn()) h += g() + key() * 2 / 3;
        if (showSpace.isOn()) h += g() + Math.max(8, key() / 2);
        return h;
    }

    @Override
    public void render(GuiGraphics ctx, boolean preview) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        if (background.isOn() || border.isOn()) drawBackground(ctx, getWidth(), getHeight());

        Options o = mc().options;
        int k = key();
        int y = 0;
        boolean arrows = keyLabels.is("Arrows");
        key(ctx, 0, o.keyUp, arrows ? "▲" : label(o.keyUp), k + g(), y, k, k, dt);
        y += k + g();
        key(ctx, 1, o.keyLeft, arrows ? "◀" : label(o.keyLeft), 0, y, k, k, dt);
        key(ctx, 2, o.keyDown, arrows ? "▼" : label(o.keyDown), k + g(), y, k, k, dt);
        key(ctx, 3, o.keyRight, arrows ? "▶" : label(o.keyRight), (k + g()) * 2, y, k, k, dt);
        y += k + g();
        if (showMouse.isOn()) {
            int half = (fullWidth() - g()) / 2;
            mouse(ctx, 4, o.keyAttack, "LMB", CpsTracker.left(), 0, y, half, dt);
            mouse(ctx, 5, o.keyUse, "RMB", CpsTracker.right(), half + g(), y, fullWidth() - half - g(), dt);
            y += k + g();
        }
        if (showModifiers.isOn()) {
            int half = (fullWidth() - g()) / 2;
            int mh = k * 2 / 3;
            key(ctx, 6, o.keyShift, "Sneak", 0, y, half, mh, dt);
            key(ctx, 7, o.keySprint, "Sprint", half + g(), y, fullWidth() - half - g(), mh, dt);
            y += mh + g();
        }
        if (showSpace.isOn()) {
            int sh = Math.max(8, k / 2);
            float t = key(ctx, 8, o.keyJump, "", 0, y, fullWidth(), sh, dt);
            int bar = Theme.lerpColor(textColor.color(), pressedTextColor.color(), t);
            ctx.fill(fullWidth() / 2 - 10, y + sh / 2, fullWidth() / 2 + 10, y + sh / 2 + 1, bar);
        }
    }

    private static String label(KeyMapping binding) {
        String text = binding.getTranslatedKeyMessage().getString();
        return text.length() > 3 ? text.substring(0, 3) : text;
    }

    private float anim(int i, boolean pressed, float dt) {
        float target = pressed ? 1f : 0f;
        fade[i] += (target - fade[i]) * (1f - (float) Math.exp(-dt * fadeSpeed.get()));
        return fade[i];
    }

    private float key(GuiGraphics ctx, int i, KeyMapping b, String text, int x, int y, int w, int h, float dt) {
        float t = anim(i, b.isDown(), dt);
        Theme.round(ctx, x, y, x + w, y + h, cornerRadius.getInt(), Theme.lerpColor(idleColor.color(), pressedColor.color(), t));
        if (!text.isEmpty()) {
            int c = Theme.lerpColor(textColor.color(), pressedTextColor.color(), t);
            ctx.drawString(Theme.font(), hudText(text), x + (w - textWidth(text)) / 2 + 1, y + (h - 8) / 2 + 1, c, textShadow.isOn() && t < 0.5f);
        }
        return t;
    }

    private void mouse(GuiGraphics ctx, int i, KeyMapping b, String text, int cps, int x, int y, int w, float dt) {
        int k = key();
        float t = anim(i, b.isDown(), dt);
        Theme.round(ctx, x, y, x + w, y + k, cornerRadius.getInt(), Theme.lerpColor(idleColor.color(), pressedColor.color(), t));
        int c = Theme.lerpColor(textColor.color(), pressedTextColor.color(), t);
        if (showCps.isOn()) {
            ctx.drawString(Theme.font(), hudText(text), x + (w - textWidth(text)) / 2 + 1, y + k / 2 - 8, c, false);
            String cpsText = cps + " CPS";
            ctx.pose().pushPose();
            ctx.pose().translate(x + w / 2f, y + k / 2f + 2, 0);
            ctx.pose().scale(0.75f, 0.75f, 1f);
            ctx.drawString(Theme.font(), hudText(cpsText), -textWidth(cpsText) / 2, 0, Theme.fade(c, 0.8f), false);
            ctx.pose().popPose();
        } else {
            ctx.drawString(Theme.font(), hudText(text), x + (w - textWidth(text)) / 2 + 1, y + (k - 8) / 2 + 1, c, false);
        }
    }
}
