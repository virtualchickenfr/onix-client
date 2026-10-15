package dev.onix.client.module;

import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A HUD element made of "label value" lines, e.g. "FPS 144". Handles label styles,
 * colors, alignment and a stable width (so the box does not jitter while numbers change).
 */
public abstract class TextHudModule extends HudModule {
    /** One line. {@code valueColor} 0 means "use the configured value color". */
    public record Line(String label, String value, int valueColor) {
        public Line(String label, String value) {
            this(label, value, 0);
        }
    }

    private static final int LINE_HEIGHT = 9;

    protected final ModeSetting labelStyle;
    protected final BoolSetting colon;
    protected final ColorSetting labelColor;
    protected final ColorSetting valueColor;
    protected final ModeSetting alignment;
    protected final NumberSetting lineSpacing;
    protected final BoolSetting stableWidth;

    private final List<Line> buffer = new ArrayList<>();
    private int widthMemory;
    private long widthMemoryTime;

    protected TextHudModule(String id, String name, String description, Category category, boolean enabledByDefault,
                            Anchor anchor, int x, int y) {
        super(id, name, description, category, enabledByDefault, anchor, x, y);
        group("Text");
        labelStyle = add(new ModeSetting("labelStyle", "Label Style", "Prefix", "Suffix", "Brackets", "Value Only")
                .describe("Prefix: FPS 144 · Suffix: 144 FPS · Brackets: [FPS] 144."));
        colon = add(new BoolSetting("colon", "Colon After Label", false)
                .visibleIf(() -> labelStyle.is("Prefix")));
        labelColor = add(ColorSetting.accent("labelColor", "Label Color").visibleIf(() -> !labelStyle.is("Value Only")));
        valueColor = add(new ColorSetting("valueColor", "Value Color", 0xFFFFFFFF));
        alignment = add(new ModeSetting("align", "Alignment", "Left", "Center", "Right"));
        lineSpacing = add(new NumberSetting("lineSpacing", "Line Spacing", 2, 0, 8, 1).unit("px"));
        stableWidth = add(new BoolSetting("stableWidth", "Stable Width", true)
                .describe("Keep the box wide enough for typical values so it does not jump around."));
        group(GROUP_GENERAL);
    }

    /** Adds the lines to show. Keep the number of lines stable where possible. */
    protected abstract void lines(List<Line> out, boolean preview);

    /** A typical widest value, e.g. "000" for FPS. Null = no stable width. */
    protected String sampleValue() {
        return null;
    }

    private List<Line> collect(boolean preview) {
        buffer.clear();
        lines(buffer, preview);
        return buffer;
    }

    private String[] parts(Line line) {
        String label = line.label();
        String value = line.value();
        if (label == null || label.isEmpty()) return new String[]{"", value};
        return switch (labelStyle.get()) {
            case "Suffix" -> new String[]{value + " ", label, "suffix"};
            case "Brackets" -> new String[]{"[" + label + "] ", value};
            case "Value Only" -> new String[]{"", value};
            default -> new String[]{label + (colon.isOn() ? ": " : " "), value};
        };
    }

    private int lineWidth(Line line) {
        String[] p = parts(line);
        return textWidth(p[0]) + textWidth(p[1]);
    }

    private int contentWidth(boolean preview) {
        int w = 0;
        for (Line line : collect(preview)) w = Math.max(w, lineWidth(line));
        String sample = sampleValue();
        if (stableWidth.isOn() && sample != null && !buffer.isEmpty()) {
            Line first = buffer.get(0);
            w = Math.max(w, lineWidth(new Line(first.label(), sample)));
        }
        return w;
    }

    @Override
    public int getWidth() {
        int w = contentWidth(true) + pad() * 2;
        if (!stableWidth.isOn()) return w;
        long now = System.currentTimeMillis();
        if (w >= widthMemory) {
            widthMemory = w;
            widthMemoryTime = now;
        } else if (now - widthMemoryTime > 2500) {
            widthMemory = w; // shrink again after values stayed smaller for a while
            widthMemoryTime = now;
        }
        return widthMemory;
    }

    @Override
    public int getHeight() {
        int n = Math.max(1, collect(true).size());
        return pad() * 2 + n * LINE_HEIGHT + (n - 1) * lineSpacing.getInt();
    }

    @Override
    public boolean hasContent() {
        return !collect(false).isEmpty();
    }

    @Override
    public void render(DrawContext ctx, boolean preview) {
        int w = getWidth();
        List<Line> lines = new ArrayList<>(collect(preview));
        int h = pad() * 2 + Math.max(1, lines.size()) * LINE_HEIGHT + (Math.max(1, lines.size()) - 1) * lineSpacing.getInt();
        drawBackground(ctx, w, h);
        int y = pad() + 1;
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            String[] p = parts(line);
            int lw = textWidth(p[0]) + textWidth(p[1]);
            int x = switch (alignment.get()) {
                case "Center" -> (w - lw) / 2;
                case "Right" -> w - pad() - lw;
                default -> pad();
            };
            float hue = i * 0.08f;
            int value = line.valueColor() != 0 ? line.valueColor() : valueColor.color(hue);
            int label = labelColor.color(hue);
            if (p.length == 3) {
                // suffix: value first, label second
                x = drawText(ctx, p[0], x, y, value);
                drawText(ctx, p[1], x, y, label);
            } else {
                if (!p[0].isEmpty()) x = drawText(ctx, p[0], x, y, label);
                drawText(ctx, p[1], x, y, value);
            }
            y += LINE_HEIGHT + lineSpacing.getInt();
        }
    }
}
