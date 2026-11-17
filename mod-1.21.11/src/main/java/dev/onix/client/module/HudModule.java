package dev.onix.client.module;

import dev.onix.client.gui.Theme;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A module that draws an element on the HUD. Elements draw at (0, 0) in their own,
 * unscaled size; position, scale and the shared appearance are handled here.
 *
 * <p>Positions are stored relative to one of nine anchors (chosen automatically when
 * the element is dropped in the HUD editor), so layouts survive window resizes.
 */
public abstract class HudModule extends Module {
    public enum Anchor {
        TOP_LEFT(0, 0), TOP_CENTER(0.5f, 0), TOP_RIGHT(1, 0),
        MIDDLE_LEFT(0, 0.5f), CENTER(0.5f, 0.5f), MIDDLE_RIGHT(1, 0.5f),
        BOTTOM_LEFT(0, 1), BOTTOM_CENTER(0.5f, 1), BOTTOM_RIGHT(1, 1);

        public final float fx;
        public final float fy;

        Anchor(float fx, float fy) {
            this.fx = fx;
            this.fy = fy;
        }

        public static Anchor of(float fx, float fy) {
            for (Anchor a : values()) if (a.fx == fx && a.fy == fy) return a;
            return TOP_LEFT;
        }
    }

    private final Anchor defaultAnchor;
    private final int defaultOffsetX;
    private final int defaultOffsetY;
    private Anchor anchor;
    private int offsetX;
    private int offsetY;

    protected final NumberSetting scale;
    protected final BoolSetting background;
    protected final ColorSetting backgroundColor;
    protected final BoolSetting border;
    protected final ColorSetting borderColor;
    protected final NumberSetting cornerRadius;
    protected final NumberSetting padding;
    protected final BoolSetting textShadow;
    protected final ModeSetting font;

    protected HudModule(String id, String name, String description, Category category, boolean enabledByDefault,
                        Anchor anchor, int offsetX, int offsetY) {
        super(id, name, description, category, enabledByDefault);
        this.defaultAnchor = anchor;
        this.defaultOffsetX = offsetX;
        this.defaultOffsetY = offsetY;
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;

        group(GROUP_APPEARANCE);
        scale = add(new NumberSetting("scale", "Scale", 1.0, 0.5, 3.0, 0.05).unit("x")
                .describe("Size of the element. Tip: scroll over it in the HUD editor."));
        background = add(new BoolSetting("background", "Background", true));
        backgroundColor = add(new ColorSetting("backgroundColor", "Background Color", 0x73000000)
                .visibleIf(background::isOn));
        border = add(new BoolSetting("border", "Border", false));
        borderColor = add(ColorSetting.accent("borderColor", "Border Color").visibleIf(border::isOn));
        cornerRadius = add(new NumberSetting("radius", "Corner Radius", 6, 0, 12, 1).unit("px")
                .visibleIf(() -> background.isOn() || border.isOn()));
        padding = add(new NumberSetting("padding", "Padding", 4, 0, 10, 1).unit("px"));
        textShadow = add(new BoolSetting("shadow", "Text Shadow", true));
        font = add(new ModeSetting("font", "Font", "Onix", "Minecraft")
                .describe("Minecraft's pixel font or the Onix interface font."));
        group(GROUP_GENERAL);
    }

    /** Switches this element to the smooth Onix font (one-time update for everyone). */
    public void useOnixFont() {
        font.set("Onix");
    }

    /** Lifts old square-ish corners to the new round default. */
    public void useRoundCorners() {
        if (cornerRadius.getInt() < 6) cornerRadius.set(6.0);
    }

    /** Unscaled width. */
    public abstract int getWidth();

    /** Unscaled height. */
    public abstract int getHeight();

    /** Draws at (0, 0). In the HUD editor {@code preview} is true: show sample data if there is nothing real. */
    public abstract void render(GuiGraphics ctx, boolean preview);

    /** Whether there is anything to show right now (e.g. effects only while active). */
    public boolean hasContent() {
        return true;
    }

    public void renderAtPosition(GuiGraphics ctx, boolean preview) {
        if (!preview && !hasContent()) return;
        float s = scale.getFloat();
        ctx.pose().pushMatrix();
        ctx.pose().translate(getX(), getY());
        ctx.pose().scale(s, s);
        render(ctx, preview);
        ctx.pose().popMatrix();
    }

    /** Draws the module centred in a box, scaled down if needed (live preview in the menu). */
    public void renderPreview(GuiGraphics ctx, int cx, int cy, int maxWidth, int maxHeight) {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        float s = Math.min(scale.getFloat(), Math.min(maxWidth / (float) w, maxHeight / (float) h));
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx - w * s / 2f, cy - h * s / 2f);
        ctx.pose().scale(s, s);
        render(ctx, true);
        ctx.pose().popMatrix();
    }

    // ---------------------------------------------------------------- drawing helpers

    protected int pad() {
        return padding.getInt();
    }

    /** The look of every HUD element (Design › HUD › HUD Style). */
    protected static String hudStyle() {
        return dev.onix.client.OnixClient.CONFIG == null ? "Glass" : dev.onix.client.OnixClient.CONFIG.hudStyle.get();
    }

    /** A small panel inside an element (a key of Keystrokes, …): glass in the Glass style, flat otherwise. */
    protected void panel(GuiGraphics ctx, int x1, int y1, int x2, int y2, int r, int color) {
        if (hudStyle().equals("Glass")) {
            gg.carbon.client.ui.R2D.rrect(ctx, x1, y1, x2 - x1, y2 - y1, r, color);
            gg.carbon.client.ui.R2D.outline(ctx, x1, y1, x2 - x1, y2 - y1, r, 0.6f, 0x26FFFFFF);
        } else {
            Theme.round(ctx, x1, y1, x2, y2, r, color);
        }
    }

    /** Background and border as configured. */
    protected void drawBackground(GuiGraphics ctx, int w, int h) {
        int r = cornerRadius.getInt();
        String style = hudStyle();
        if (style.equals("Minimal")) return;
        if (style.equals("Glass") && background.isOn()) {
            // dark glass: the configured colour a little deeper, a light rim (or the border colour) and a sheen on top
            int fill = backgroundColor.color();
            int alpha = Math.min(255, Math.round(((fill >>> 24) & 0xFF) * 1.15f) + 12);
            int glass = alpha << 24 | (Theme.lerpColor(fill | 0xFF000000, 0xFF0B0C12, 0.35f) & 0xFFFFFF);
            int rim = border.isOn() ? borderColor.color() : 0x2EFFFFFF;
            // smooth, anti-aliased shapes at the screen's real pixel size (follow the element's scale)
            gg.carbon.client.ui.R2D.rrect(ctx, 0, 0, w, h, r, glass);
            gg.carbon.client.ui.R2D.outline(ctx, 0, 0, w, h, r, 0.6f, rim);
            if (w > 2 * r + 4) ctx.fill(r + 1, 1, w - r - 1, 2, 0x14FFFFFF);
            return;
        }
        if (border.isOn()) {
            Theme.round(ctx, 0, 0, w, h, r, borderColor.color());
            if (background.isOn()) {
                Theme.round(ctx, 1, 1, w - 1, h - 1, Math.max(0, r - 1), backgroundColor.color());
            } else {
                Theme.round(ctx, 1, 1, w - 1, h - 1, Math.max(0, r - 1), 0x00000000);
            }
        } else if (background.isOn()) {
            Theme.round(ctx, 0, 0, w, h, r, backgroundColor.color());
        }
    }

    protected Component hudText(String s) {
        return font.is("Onix") ? Theme.ui(s) : Component.literal(s);
    }

    protected int textWidth(String s) {
        return Theme.font().width(hudText(s));
    }

    protected int drawText(GuiGraphics ctx, String s, int x, int y, int color) {
        Component text = hudText(s);
        // without a panel the text needs its shadow to stay readable
        ctx.drawString(Theme.font(), text, x, y, color, textShadow.isOn() || hudStyle().equals("Minimal"));
        return x + Theme.font().width(text);
    }

    // ---------------------------------------------------------------- position

    public int getScaledWidth() {
        return (int) Math.ceil(getWidth() * scale.get());
    }

    public int getScaledHeight() {
        return (int) Math.ceil(getHeight() * scale.get());
    }

    private int screenW() {
        return mc().getWindow().getGuiScaledWidth();
    }

    private int screenH() {
        return mc().getWindow().getGuiScaledHeight();
    }

    /** Left edge on screen, clamped so the element stays visible. */
    public int getX() {
        int w = getScaledWidth();
        int x = Math.round(anchor.fx * (screenW() - w)) + offsetX;
        return Math.max(0, Math.min(x, screenW() - w));
    }

    public int getY() {
        int h = getScaledHeight();
        int y = Math.round(anchor.fy * (screenH() - h)) + offsetY;
        return Math.max(0, Math.min(y, screenH() - h));
    }

    /** Places the element at an absolute screen position and picks the nearest anchor. */
    public void setScreenPosition(int x, int y) {
        int w = getScaledWidth();
        int h = getScaledHeight();
        float cx = (x + w / 2f) / screenW();
        float cy = (y + h / 2f) / screenH();
        float fx = cx < 1 / 3f ? 0 : cx > 2 / 3f ? 1 : 0.5f;
        float fy = cy < 1 / 3f ? 0 : cy > 2 / 3f ? 1 : 0.5f;
        anchor = Anchor.of(fx, fy);
        offsetX = x - Math.round(fx * (screenW() - w));
        offsetY = y - Math.round(fy * (screenH() - h));
    }

    public void resetPosition() {
        anchor = defaultAnchor;
        offsetX = defaultOffsetX;
        offsetY = defaultOffsetY;
    }

    @Override
    public void resetToDefaults() {
        super.resetToDefaults();
        resetPosition();
    }

    public Anchor getAnchor() { return anchor; }
    public int getOffsetX() { return offsetX; }
    public int getOffsetY() { return offsetY; }

    public void setStoredPosition(Anchor anchor, int offsetX, int offsetY) {
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    public NumberSetting scaleSetting() {
        return scale;
    }

    public boolean isInside(double mx, double my) {
        return mx >= getX() && mx < getX() + getScaledWidth() && my >= getY() && my < getY() + getScaledHeight();
    }
}
