package dev.onix.client.gui;

import dev.onix.client.OnixClient;
import dev.onix.client.config.OnixConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * The first start of Onix: a short tour that sets the look (theme, light or dark, main menu, menu size) and the basics
 * (animations, sounds, friends) before the title screen. Shown once; config "introDone" remembers it.
 */
public class WelcomeScreen extends Screen {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("onix", "textures/gui/logo.png");
    private static final Identifier CHECK = Identifier.fromNamespaceAndPath("onix", "textures/gui/icon_check.png");
    private static final String[] TITLES = {"Welcome", "Pick your theme", "Main menu", "A few basics", "All set"};
    private static final String[][] MENU_DESIGNS = {
            {"Onix", "Clean menu with your theme and moving backgrounds"},
            {"Carbon", "Carbon Client's designs, with a picker for more"},
            {"Classic", "Familiar Minecraft layout in Onix colours"},
            {"Vanilla", "The normal Minecraft title screen"},
    };

    private record Area(int x1, int y1, int x2, int y2, Runnable action) {}

    private final List<Area> areas = new ArrayList<>();
    private final Map<String, Anim> hover = new HashMap<>();
    private final Anim intro = new Anim(0);
    private final Anim pageAnim = new Anim(1);
    private final MenuParticles particles = new MenuParticles();
    private final long start = System.nanoTime();
    private int page;
    private int direction = 1;

    public WelcomeScreen() {
        super(Component.literal("Welcome to " + OnixClient.NAME));
    }

    /** True until the tour was finished or skipped once. */
    public static boolean shouldShow() {
        return OnixClient.CONFIG != null && !OnixClient.CONFIG.introDone.isOn();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private OnixConfig config() {
        return OnixClient.CONFIG;
    }

    private void click() {
        if (config().clickSounds.isOn()) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2f, 0.35f));
    }

    private void go(int to) {
        if (to < 0 || to >= TITLES.length || to == page) return;
        direction = to > page ? 1 : -1;
        page = to;
        pageAnim.set(0);
    }

    private void finish() {
        config().introDone.set(true);
        config().save();
        minecraft.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
    }

    // ================================================================== drawing helpers

    private float hov(String key, boolean on) {
        return hover.computeIfAbsent(key, k -> new Anim(0)).to(on ? 1f : 0f, 16f);
    }

    private static boolean in(int mx, int my, int x1, int y1, int x2, int y2) {
        return mx >= x1 && mx < x2 && my >= y1 && my < y2;
    }

    private void button(GuiGraphics ctx, int mx, int my, int x, int y, int w, String label, boolean primary, Runnable action) {
        int h = 20;
        float t = hov("btn:" + label, in(mx, my, x, y, x + w, y + h));
        int lift = Math.round(t);
        int fill = primary ? Theme.lerpColor(Theme.accent(), 0xFFFFFFFF, t * 0.12f) : Theme.lerpColor(Theme.SURFACE_2, Theme.SURFACE_3, t);
        int border = primary ? Theme.accent() : Theme.lerpColor(Theme.BORDER, Theme.BORDER_STRONG, t);
        Theme.card(ctx, x, y - lift, x + w, y + h - lift, Theme.corner(7), fill, border);
        Theme.textCentered(ctx, label, x + w / 2, y + 6 - lift, primary ? Theme.onAccent() : Theme.TEXT);
        areas.add(new Area(x, y, x + w, y + h, action));
    }

    /** A selectable card; returns its hover amount. */
    private float option(GuiGraphics ctx, int mx, int my, String key, int x, int y, int w, int h, boolean selected, Runnable action) {
        float t = hov(key, in(mx, my, x, y, x + w, y + h));
        float s = hov(key + ":sel", selected);
        int border = Theme.lerpColor(Theme.lerpColor(Theme.BORDER, Theme.BORDER_STRONG, t), Theme.accent(), s);
        Theme.card(ctx, x, y, x + w, y + h, Theme.corner(8), Theme.lerpColor(Theme.SURFACE_2, Theme.SURFACE_3, Math.max(t, s * 0.6f)), border);
        if (s > 0.01f) Theme.round(ctx, x + w - 10, y + 5, x + w - 5, y + 10, 3, Theme.withAlpha(Theme.accent(), Math.round(255 * s)));
        areas.add(new Area(x, y, x + w, y + h, action));
        return t;
    }

    // ================================================================== render

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        Theme.refresh();
        float time = (System.nanoTime() - start) / 1_000_000_000f;
        TitleBackgrounds.draw(ctx, width, height, mouseX, mouseY, time);
        ctx.fill(0, 0, width, height, 0x8C050507);
        ctx.fillGradient(0, 0, width, height, Theme.withAlpha(Theme.current().sky(), 40), Theme.withAlpha(Theme.current().sky(), 0));
        particles.render(ctx, width, height, 0.5f);
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        areas.clear();
        boolean animate = config().menuAnimations.isOn();
        float in = animate ? Anim.easeOut(intro.to(1f, 5f)) : 1f;
        float p = animate ? Anim.easeOut(pageAnim.to(1f, 9f)) : 1f;

        int w = Math.min(380, width - 40);
        int h = Math.min(250, height - 40);
        int x1 = (width - w) / 2;
        int y1 = (height - h) / 2 + Math.round((1 - in) * 14);
        Theme.shadow(ctx, x1, y1, x1 + w, y1 + h, 14);
        Theme.card(ctx, x1, y1, x1 + w, y1 + h, Theme.corner(12), Theme.SURFACE, Theme.BORDER_STRONG);

        // progress
        int dots = TITLES.length;
        int dw = 6;
        int gap = 5;
        int total = dots * dw + (dots - 1) * gap + 10;
        int dx = x1 + (w - total) / 2;
        for (int i = 0; i < dots; i++) {
            float on = hov("dot" + i, i == page);
            int len = dw + Math.round(10 * on);
            int col = i <= page ? Theme.accent() : Theme.BORDER_STRONG;
            Theme.round(ctx, dx, y1 + h - 16, dx + len, y1 + h - 12, 2, col);
            int target = i;
            if (i < page) areas.add(new Area(dx - 2, y1 + h - 20, dx + len + 2, y1 + h - 8, () -> go(target)));
            dx += len + gap;
        }

        // page content slides in from the side it comes from
        int slide = Math.round((1 - p) * 30 * direction);
        ctx.pose().pushMatrix();
        ctx.pose().translate(slide, 0);
        int cx1 = x1 + 18;
        int cx2 = x1 + w - 18;
        int cy = y1 + 16;
        if (page > 0 && page < TITLES.length - 1) {
            Theme.small(ctx, "STEP " + page + " OF " + (TITLES.length - 2), cx1, cy, Theme.TEXT_3);
            Theme.textBold(ctx, TITLES[page], cx1, cy + 10, Theme.TEXT);
            cy += 30;
        }
        switch (page) {
            case 0 -> renderWelcome(ctx, x1, y1, w, h);
            case 1 -> renderThemes(ctx, mouseX, mouseY, cx1, cy, cx2);
            case 2 -> renderMenus(ctx, mouseX, mouseY, cx1, cy, cx2);
            case 3 -> renderBasics(ctx, mouseX, mouseY, cx1, cy, cx2);
            default -> renderDone(ctx, x1, y1, w, h);
        }
        ctx.pose().popMatrix();

        // navigation
        int by = y1 + h - 34;
        if (page == 0) {
            button(ctx, mouseX, mouseY, x1 + w / 2 - 60, by - 10, 120, "Get started", true, () -> go(1));
            String skip = "Skip setup";
            int sw = Theme.smallWidth(skip);
            boolean sh = in(mouseX, mouseY, x1 + w / 2 - sw / 2 - 4, by + 14, x1 + w / 2 + sw / 2 + 4, by + 24);
            Theme.small(ctx, skip, x1 + w / 2 - sw / 2, by + 15, sh ? Theme.TEXT : Theme.TEXT_3);
            areas.add(new Area(x1 + w / 2 - sw / 2 - 4, by + 14, x1 + w / 2 + sw / 2 + 4, by + 24, this::finish));
        } else if (page == TITLES.length - 1) {
            button(ctx, mouseX, mouseY, x1 + 18, by, 70, "Back", false, () -> go(page - 1));
            button(ctx, mouseX, mouseY, x1 + w - 18 - 110, by, 110, "Start playing", true, this::finish);
        } else {
            button(ctx, mouseX, mouseY, x1 + 18, by, 70, "Back", false, () -> go(page - 1));
            button(ctx, mouseX, mouseY, x1 + w - 18 - 90, by, 90, "Next", true, () -> go(page + 1));
        }
    }

    private void renderWelcome(GuiGraphics ctx, int x1, int y1, int w, int h) {
        float t = (System.nanoTime() - start) / 1_000_000_000f;
        int logo = 40;
        int lx = x1 + w / 2 - logo / 2;
        int ly = y1 + 30 + Math.round((float) Math.sin(t * 1.6f) * 2);
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO, lx, ly, 0f, 0f, logo, logo, 128, 128, 128, 128, net.minecraft.util.ARGB.colorFromFloat(1f, 1f, 1f, 1f));
        String title = "Welcome to Onix";
        Theme.scaled(ctx, title, x1 + w / 2 - Math.round(Theme.boldWidth(title) * 0.8f), y1 + 82, 1.6f, Theme.TEXT, false);
        Theme.textCentered(ctx, "Let's set up your client – it only takes a moment.", x1 + w / 2, y1 + 108, Theme.TEXT_2);
        Theme.smallCentered(ctx, "You can change everything later in the Onix menu (Right Shift).", x1 + w / 2, y1 + 122, Theme.TEXT_3);
    }

    private void renderThemes(GuiGraphics ctx, int mx, int my, int x1, int y, int x2) {
        ThemeDef[] themes = ThemeDef.BUILT_IN;
        int cols = 4;
        int gap = 6;
        int cw = (x2 - x1 - gap * (cols - 1)) / cols;
        int ch = 38;
        for (int i = 0; i < themes.length; i++) {
            ThemeDef def = themes[i];
            int cx = x1 + (i % cols) * (cw + gap);
            int cy = y + (i / cols) * (ch + gap);
            boolean sel = config().theme.get().equals(def.id());
            option(ctx, mx, my, "theme:" + def.id(), cx, cy, cw, ch, sel, () -> {
                config().theme.set(def.id());
                Theme.refresh();
            });
            Theme.round(ctx, cx + 7, cy + 7, cx + cw - 7, cy + 19, 4, def.panel());
            Theme.round(ctx, cx + 9, cy + 9, cx + 9 + (cw - 18) / 2, cy + 17, 3, def.accent());
            Theme.small(ctx, def.name(), cx + 7, cy + 25, sel ? Theme.TEXT : Theme.TEXT_2);
        }
        int ry = y + ((themes.length + cols - 1) / cols) * (ch + gap) + 4;
        Theme.small(ctx, "Brightness", x1, ry + 4, Theme.TEXT_2);
        int bw = 60;
        boolean light = config().lightMode.isOn();
        option(ctx, mx, my, "mode:dark", x1 + 60, ry, bw, 16, !light, () -> config().lightMode.set(false));
        Theme.textCentered(ctx, "Dark", x1 + 60 + bw / 2, ry + 4, Theme.TEXT);
        option(ctx, mx, my, "mode:light", x1 + 66 + bw, ry, bw, 16, light, () -> config().lightMode.set(true));
        Theme.textCentered(ctx, "Light", x1 + 66 + bw + bw / 2, ry + 4, Theme.TEXT);
    }

    private void renderMenus(GuiGraphics ctx, int mx, int my, int x1, int y, int x2) {
        int gap = 6;
        int cw = (x2 - x1 - gap) / 2;
        int ch = 42;
        for (int i = 0; i < MENU_DESIGNS.length; i++) {
            String id = MENU_DESIGNS[i][0];
            int cx = x1 + (i % 2) * (cw + gap);
            int cy = y + (i / 2) * (ch + gap);
            boolean sel = config().menuDesign.get().equals(id) && config().customMainMenu.isOn()
                    || id.equals("Vanilla") && !config().customMainMenu.isOn();
            option(ctx, mx, my, "menu:" + id, cx, cy, cw, ch, sel, () -> {
                config().customMainMenu.set(true);
                config().menuDesign.set(id);
            });
            Theme.textBold(ctx, id, cx + 8, cy + 8, Theme.TEXT);
            Theme.small(ctx, Theme.ellipsize(MENU_DESIGNS[i][1], cw - 16), cx + 8, cy + 24, Theme.TEXT_3);
        }
        Theme.small(ctx, "More designs (Voxel, Portal …) are in the title screen's design picker.", x1, y + 2 * (ch + gap) + 2, Theme.TEXT_3);
    }

    private void renderBasics(GuiGraphics ctx, int mx, int my, int x1, int y, int x2) {
        Object[][] rows = {
                {"Menu animations", "Smooth movement in every Onix menu", config().menuAnimations},
                {"Click sounds", "A soft click on buttons", config().clickSounds},
                {"Onix loading screens", "Themed screens while worlds load", config().loadingScreens},
                {"Friends & chat", "Friend list and messages through the Onix server", config().socialOn},
        };
        int rh = 22;
        for (int i = 0; i < rows.length; i++) {
            int ry = y + i * (rh + 3);
            var setting = (dev.onix.client.setting.BoolSetting) rows[i][2];
            float t = hov("row" + i, in(mx, my, x1, ry, x2, ry + rh));
            Theme.card(ctx, x1, ry, x2, ry + rh, Theme.corner(7), Theme.lerpColor(Theme.SURFACE_2, Theme.SURFACE_3, t), Theme.BORDER);
            Theme.text(ctx, (String) rows[i][0], x1 + 8, ry + 4, Theme.TEXT);
            Theme.small(ctx, (String) rows[i][1], x1 + 8, ry + 13, Theme.TEXT_3);
            Theme.toggle(ctx, x2 - 30, ry + 6, hov("tg" + i, setting.isOn()));
            areas.add(new Area(x1, ry, x2, ry + rh, () -> setting.set(!setting.isOn())));
        }
        int sy = y + rows.length * (rh + 3) + 4;
        Theme.small(ctx, "Menu size", x1, sy + 4, Theme.TEXT_2);
        String[] sizes = {"Small", "Normal", "Large"};
        int bw = 54;
        for (int i = 0; i < sizes.length; i++) {
            String size = sizes[i];
            int bx = x1 + 60 + i * (bw + 6);
            option(ctx, mx, my, "size:" + size, bx, sy, bw, 16, config().menuScale.is(size), () -> config().menuScale.set(size));
            Theme.textCentered(ctx, size, bx + bw / 2, sy + 4, Theme.TEXT);
        }
    }

    private void renderDone(GuiGraphics ctx, int x1, int y1, int w, int h) {
        int cx = x1 + w / 2;
        Theme.round(ctx, cx - 14, y1 + 36, cx + 14, y1 + 64, 14, Theme.accent());
        int c = Theme.onAccent();
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CHECK, cx - 7, y1 + 43, 0f, 0f, 14, 14, 64, 64, 64, 64,
                net.minecraft.util.ARGB.colorFromFloat(1f, Theme.red(c), Theme.green(c), Theme.blue(c)));
        String title = "You're all set";
        Theme.scaled(ctx, title, cx - Math.round(Theme.boldWidth(title) * 0.7f), y1 + 76, 1.4f, Theme.TEXT, false);
        String key = OnixClient.MENU_KEY == null ? "Right Shift" : OnixClient.MENU_KEY.getTranslatedKeyMessage().getString();
        Theme.textCentered(ctx, "Press " + key + " in game for modules, cosmetics and settings.", cx, y1 + 102, Theme.TEXT_2);
        Theme.smallCentered(ctx, "The HUD editor moves every on-screen display – open it from the menu.", cx, y1 + 116, Theme.TEXT_3);
    }

    // ================================================================== input

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent onix$e) {
        double mouseX = onix$e.x(); double mouseY = onix$e.y(); int button = onix$e.button();
        if (button == 0) {
            for (int i = areas.size() - 1; i >= 0; i--) {
                Area a = areas.get(i);
                if (Theme.inside(mouseX, mouseY, a.x1(), a.y1(), a.x2(), a.y2())) {
                    click();
                    a.action().run();
                    config().save();
                    return true;
                }
            }
        }
        return super.mouseReleased(onix$e);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent onix$k) {
        int keyCode = onix$k.key(); int scanCode = onix$k.scancode(); int modifiers = onix$k.modifiers();
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (page == TITLES.length - 1) finish();
            else go(page + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            go(page - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            finish();
            return true;
        }
        return super.keyPressed(onix$k);
    }
}
