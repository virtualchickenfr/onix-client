package dev.onix.client.gui;

import dev.onix.client.OnixClient;
import dev.onix.client.module.HudModule;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Drag HUD elements around, scroll to resize, right-click or double-click for settings,
 * arrow keys to nudge (Shift = 10 px), Delete to hide, Ctrl+Z to undo.
 * Elements snap to edges, the center and each other.
 */
public class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudModule dragging;
    private HudModule selected;
    private double grabX;
    private double grabY;
    private Integer guideX;
    private Integer guideY;
    private final Anim fade = new Anim(0);

    /** One undo step: the state of one or more elements before a change. */
    private record Saved(HudModule hud, int x, int y, double scale, boolean enabled) {}
    private final Deque<List<Saved>> undo = new ArrayDeque<>();
    private HudModule lastEdited;
    private String lastKind = "";
    private long lastEditTime;
    private long lastClickTime;
    private HudModule lastClicked;

    private static Saved save(HudModule hud) {
        return new Saved(hud, hud.getX(), hud.getY(), hud.scaleSetting().get(), hud.isEnabled());
    }

    /** Records the state before a change. Repeated scroll or arrow steps on one element merge into one step. */
    private void remember(HudModule hud, String kind) {
        long now = System.currentTimeMillis();
        boolean merge = hud == lastEdited && kind.equals(lastKind) && now - lastEditTime < 700 && !kind.equals("drag");
        lastEdited = hud;
        lastKind = kind;
        lastEditTime = now;
        if (merge) return;
        undo.push(List.of(save(hud)));
        while (undo.size() > 50) undo.removeLast();
    }

    private void undoLast() {
        if (undo.isEmpty()) {
            Notifications.show("Nothing to undo", "Move, resize or hide an element first");
            return;
        }
        for (Saved s : undo.pop()) {
            s.hud().setEnabled(s.enabled());
            s.hud().scaleSetting().set(s.scale());
            s.hud().setScreenPosition(s.x(), s.y());
        }
        lastEdited = null;
    }

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Edit HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int y = height - 30;
        addRenderableWidget(new OnixButton(width / 2 - 154, y, 100, 20, Component.literal("Done"), b -> onClose(), true));
        addRenderableWidget(new OnixButton(width / 2 - 50, y, 100, 20, Component.literal("Modules"),
                b -> minecraft.setScreen(new OnixMenuScreen(this, OnixMenuScreen.View.MODULES))));
        addRenderableWidget(new OnixButton(width / 2 + 54, y, 100, 20, Component.literal("Reset Layout"), b -> {
            List<Saved> before = new ArrayList<>();
            for (HudModule hud : OnixClient.MODULES.hudModules()) before.add(save(hud));
            undo.push(before);
            for (HudModule hud : OnixClient.MODULES.hudModules()) hud.resetPosition();
            Notifications.show("Layout reset", "Ctrl+Z brings your layout back");
        }));
    }

    @Override
    public void onClose() {
        OnixClient.save();
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<HudModule> enabled() {
        return OnixClient.MODULES.hudModules().stream().filter(HudModule::isEnabled).toList();
    }

    private HudModule at(double mx, double my) {
        List<HudModule> list = enabled();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).isInside(mx, my)) return list.get(i);
        return null;
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        Theme.refresh();
        float t = fade.to(1f, 10f);
        if (minecraft.level == null) ctx.fillGradient(0, 0, width, height, 0xFF111116, 0xFF07070A);
        else ctx.fill(0, 0, width, height, Theme.withAlpha(0xFF000000, (int) (80 * t)));
        if (OnixClient.CONFIG.editorGrid.isOn()) {
            int alpha = dragging != null ? 0x14 : 0x08;
            for (int x = 0; x < width; x += 16) ctx.fill(x, 0, x + 1, height, Theme.withAlpha(0xFFFFFFFF, alpha));
            for (int y = 0; y < height; y += 16) ctx.fill(0, y, width, y + 1, Theme.withAlpha(0xFFFFFFFF, alpha));
        }
        ctx.fill(width / 2, 0, width / 2 + 1, height, 0x10FFFFFF);
        ctx.fill(0, height / 2, width, height / 2 + 1, 0x10FFFFFF);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        if (dragging != null && guideX != null) ctx.fill(guideX, 0, guideX + 1, height, Theme.withAlpha(Theme.accent(), 210));
        if (dragging != null && guideY != null) ctx.fill(0, guideY, width, guideY + 1, Theme.withAlpha(Theme.accent(), 210));

        HudModule hovered = dragging != null ? dragging : at(mouseX, mouseY);
        for (HudModule hud : enabled()) {
            hud.renderAtPosition(ctx, true);
            int x = hud.getX();
            int y = hud.getY();
            boolean active = hud == hovered || hud == selected;
            Theme.outline(ctx, x - 2, y - 2, x + hud.getScaledWidth() + 2, y + hud.getScaledHeight() + 2,
                    active ? Theme.accent() : Theme.withAlpha(0xFFFFFFFF, 40));
        }
        HudModule label = hovered != null ? hovered : selected;
        if (label != null) {
            String text = label.getName() + "  " + label.scaleSetting().display();
            int lw = Theme.boldWidth(text) + 10;
            int lx = Math.max(2, Math.min(label.getX() - 2, width - lw - 2));
            int ly = label.getY() > 18 ? label.getY() - 17 : label.getY() + label.getScaledHeight() + 4;
            Theme.round(ctx, lx, ly, lx + lw, ly + 13, 4, Theme.accent());
            Theme.textBold(ctx, text, lx + 5, ly + 3, Theme.onAccent());
        }

        String hint = enabled().isEmpty()
                ? "No HUD modules are enabled – turn some on under Modules."
                : "Drag to move  ·  Scroll to resize  ·  Double-click for settings  ·  Arrows to nudge  ·  Ctrl+Z to undo";
        int hw = Theme.width(hint) + 20;
        int hy = height - 54;
        Theme.shadow(ctx, width / 2 - hw / 2, hy, width / 2 + hw / 2, hy + 18, 7);
        Theme.card(ctx, width / 2 - hw / 2, hy, width / 2 + hw / 2, hy + 18, 7, Theme.SURFACE, Theme.BORDER_STRONG);
        Theme.text(ctx, hint, width / 2 - hw / 2 + 10, hy + 5, Theme.TEXT_2);
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent onix$e, boolean onix$dbl) {
        double mouseX = onix$e.x(); double mouseY = onix$e.y(); int button = onix$e.button();
        if (super.mouseClicked(onix$e, onix$dbl)) return true;
        HudModule hud = at(mouseX, mouseY);
        selected = hud;
        if (hud == null) return false;
        long now = System.currentTimeMillis();
        boolean doubleClick = button == 0 && hud == lastClicked && now - lastClickTime < 350;
        lastClicked = hud;
        lastClickTime = now;
        if (button == 1 || doubleClick) {
            OnixClient.save();
            minecraft.setScreen(OnixMenuScreen.forModule(this, hud));
            return true;
        }
        if (button == 0) {
            remember(hud, "drag");
            dragging = hud;
            grabX = mouseX - hud.getX();
            grabY = mouseY - hud.getY();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        HudModule hud = at(mouseX, mouseY);
        if (hud == null) return false;
        remember(hud, "scale");
        int x = hud.getX();
        int y = hud.getY();
        hud.scaleSetting().set(hud.scaleSetting().get() + (verticalAmount > 0 ? 0.05 : -0.05));
        hud.setScreenPosition(x, y);
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent onix$k) {
        int keyCode = onix$k.key(); int scanCode = onix$k.scancode(); int modifiers = onix$k.modifiers();
        if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            undoLast();
            return true;
        }
        if (selected != null) {
            int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
            int dx = keyCode == GLFW.GLFW_KEY_LEFT ? -step : keyCode == GLFW.GLFW_KEY_RIGHT ? step : 0;
            int dy = keyCode == GLFW.GLFW_KEY_UP ? -step : keyCode == GLFW.GLFW_KEY_DOWN ? step : 0;
            if (dx != 0 || dy != 0) {
                remember(selected, "nudge");
                selected.setScreenPosition(clampX(selected, selected.getX() + dx), clampY(selected, selected.getY() + dy));
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                remember(selected, "hide");
                selected.setEnabled(false);
                Notifications.show("Hidden " + selected.getName(), "Ctrl+Z to bring it back");
                selected = null;
                return true;
            }
        }
        return super.keyPressed(onix$k);
    }

    private int clampX(HudModule hud, int x) {
        return Math.max(0, Math.min(x, width - hud.getScaledWidth()));
    }

    private int clampY(HudModule hud, int y) {
        return Math.max(0, Math.min(y, height - hud.getScaledHeight()));
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent onix$e, double onix$dx, double onix$dy) {
        double mouseX = onix$e.x(); double mouseY = onix$e.y(); int button = onix$e.button(); double deltaX = onix$dx; double deltaY = onix$dy;
        if (dragging == null) return super.mouseDragged(onix$e, onix$dx, onix$dy);
        int w = dragging.getScaledWidth();
        int h = dragging.getScaledHeight();
        int x = (int) Math.round(mouseX - grabX);
        int y = (int) Math.round(mouseY - grabY);
        guideX = null;
        guideY = null;

        if (OnixClient.CONFIG.snapping.isOn()) {
            int snap = OnixClient.CONFIG.snapDistance.getInt();
            int margin = 2;
            if (Math.abs(x - margin) < snap) x = margin;
            if (Math.abs(x + w - (width - margin)) < snap) x = width - margin - w;
            if (Math.abs(y - margin) < snap) y = margin;
            if (Math.abs(y + h - (height - margin)) < snap) y = height - margin - h;
            if (Math.abs(x + w / 2 - width / 2) < snap) {
                x = width / 2 - w / 2;
                guideX = width / 2;
            }
            if (Math.abs(y + h / 2 - height / 2) < snap) {
                y = height / 2 - h / 2;
                guideY = height / 2;
            }
            for (HudModule other : enabled()) {
                if (other == dragging) continue;
                int ox1 = other.getX(), ox2 = ox1 + other.getScaledWidth();
                int oy1 = other.getY(), oy2 = oy1 + other.getScaledHeight();
                if (guideX == null) {
                    if (Math.abs(x - ox1) < snap) { x = ox1; guideX = ox1; }
                    else if (Math.abs(x + w - ox2) < snap) { x = ox2 - w; guideX = ox2; }
                }
                if (guideY == null) {
                    if (Math.abs(y - (oy2 + 2)) < snap) { y = oy2 + 2; guideY = y; }
                    else if (Math.abs(y + h + 2 - oy1) < snap) { y = oy1 - h - 2; guideY = oy1; }
                }
            }
        }
        dragging.setScreenPosition(clampX(dragging, x), clampY(dragging, y));
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent onix$e) {
        double mouseX = onix$e.x(); double mouseY = onix$e.y(); int button = onix$e.button();
        if (dragging != null) {
            dragging = null;
            guideX = guideY = null;
            return true;
        }
        return super.mouseReleased(onix$e);
    }
}
