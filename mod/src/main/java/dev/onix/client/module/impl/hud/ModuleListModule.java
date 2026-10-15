package dev.onix.client.module.impl.hud;

import dev.onix.client.OnixClient;
import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The classic "array list" of enabled modules. */
public class ModuleListModule extends HudModule {
    private final ModeSetting sort = add(new ModeSetting("sort", "Sort", "Length", "Alphabetical"));
    private final ModeSetting align = add(new ModeSetting("align", "Alignment", "Auto", "Left", "Right"));
    private final BoolSetting hideHud = add(new BoolSetting("hideHud", "Hide HUD Modules", true));
    private final BoolSetting showCategory = add(new BoolSetting("category", "Show Category", false));
    private final ModeSetting textCase = add(new ModeSetting("case", "Text Case", "Normal", "lowercase", "UPPERCASE"));
    private final NumberSetting maxShown = add(new NumberSetting("max", "Max Shown", 20, 3, 60, 1));
    private final BoolSetting sideBar = add(new BoolSetting("sideBar", "Side Bar", true));
    private final ModeSetting colorMode = add(new ModeSetting("colorMode", "Color", "Custom", "Category", "Fade"));
    private final ColorSetting textColor = add(ColorSetting.accent("textColor", "Text Color").visibleIf(() -> !colorMode.is("Category")));
    private final ColorSetting fadeTo = add(new ColorSetting("fadeTo", "Fade To", 0xFFFFFFFF).visibleIf(() -> colorMode.is("Fade")));

    public ModuleListModule() {
        super("modulelist", "Module List", "A list of all enabled modules.", Category.HUD, false, Anchor.TOP_RIGHT, -4, 4);
        tags("arraylist", "array list", "enabled modules");
        background.withDefault(false);
    }

    private static final int[] CATEGORY_COLORS = {0xFF8B7CFF, 0xFFFF5A5F, 0xFF34D399, 0xFF38BDF8, 0xFFFACC15, 0xFFF472B6, 0xFF2DD4BF};

    private String label(Module m) {
        String s = m.getName() + (showCategory.isOn() ? " · " + m.getCategory().getDisplayName() : "");
        return switch (textCase.get()) {
            case "lowercase" -> s.toLowerCase(Locale.ROOT);
            case "UPPERCASE" -> s.toUpperCase(Locale.ROOT);
            default -> s;
        };
    }

    private List<Module> entries(boolean preview) {
        List<Module> list = new ArrayList<>();
        for (Module m : OnixClient.MODULES.all()) {
            if (!m.isEnabled() || m == this) continue;
            if (hideHud.isOn() && m instanceof HudModule) continue;
            list.add(m);
        }
        if (list.isEmpty() && preview) {
            for (Module m : OnixClient.MODULES.all()) {
                if (!(m instanceof HudModule)) list.add(m);
                if (list.size() >= 4) break;
            }
        }
        if (sort.is("Length")) list.sort(Comparator.comparingInt((Module m) -> -textWidth(label(m))));
        else list.sort(Comparator.comparing(Module::getName));
        return list.size() > maxShown.getInt() ? list.subList(0, maxShown.getInt()) : list;
    }

    private boolean rightAligned() {
        if (align.is("Right")) return true;
        if (align.is("Left")) return false;
        return getAnchor().fx > 0.5f;
    }

    @Override
    public boolean hasContent() {
        return !entries(false).isEmpty();
    }

    @Override
    public int getWidth() {
        int w = 0;
        for (Module m : entries(true)) w = Math.max(w, textWidth(label(m)));
        return w + pad() * 2 + (sideBar.isOn() ? 3 : 0);
    }

    @Override
    public int getHeight() {
        return Math.max(1, entries(true).size()) * (9 + pad());
    }

    @Override
    public void render(DrawContext ctx, boolean preview) {
        List<Module> list = entries(preview);
        int w = getWidth();
        int rowH = 9 + pad();
        boolean right = rightAligned();
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            String s = label(m);
            int tw = textWidth(s) + pad() * 2;
            int x = right ? w - tw - (sideBar.isOn() ? 3 : 0) : (sideBar.isOn() ? 3 : 0);
            int y = i * rowH;
            int color = switch (colorMode.get()) {
                case "Category" -> CATEGORY_COLORS[m.getCategory().ordinal() % CATEGORY_COLORS.length];
                case "Fade" -> Theme.lerpColor(textColor.color(), fadeTo.color(), list.size() <= 1 ? 0 : i / (float) (list.size() - 1));
                default -> textColor.color(i * 0.06f);
            };
            if (background.isOn()) ctx.fill(x, y, x + tw, y + rowH, backgroundColor.color());
            if (sideBar.isOn()) {
                int bx = right ? w - 2 : 0;
                ctx.fill(bx, y, bx + 2, y + rowH, color);
            }
            drawText(ctx, s, x + pad(), y + pad() / 2 + 1, color);
        }
    }
}
