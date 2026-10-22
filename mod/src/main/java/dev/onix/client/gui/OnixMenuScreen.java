package dev.onix.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.onix.client.OnixClient;
import dev.onix.client.config.CwtMode;
import dev.onix.client.config.KeybindProfiles;
import dev.onix.client.config.ProfileManager;
import dev.onix.client.config.ThemeStore;
import dev.onix.client.util.ServerRules;
import dev.onix.client.module.impl.utility.KeybindProfilesModule;
import dev.onix.client.module.impl.visual.CustomSkyModule;
import dev.onix.client.render.Skies;
import dev.onix.client.cosmetics.Cosmetics;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.module.Module;
import dev.onix.client.module.ModuleManager;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.KeybindSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.setting.Setting;
import dev.onix.client.setting.TextSetting;
import dev.onix.client.util.ColorUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Onix menu (Right Shift by default).
 *
 * <p>Two styles share the same pages: "Classic" (a window with a sidebar, the default) and
 * "Bloom" (a flower of categories that opens floating windows). Every page draws into a
 * content rectangle.
 *
 * <p>The menu is immediate-mode: each frame registers click areas ({@link Hit}) that input
 * handling checks afterwards. Everything is laid out on a fixed design grid that is scaled
 * to the screen, so the menu looks identical at every GUI scale.
 */
public class OnixMenuScreen extends Screen {
    public enum View { HUB, MODULES, MODULE, COSMETICS, DESIGN, PROFILES, SETTINGS }

    /** Design size of the classic window in virtual pixels. */
    private static final int DW = 620;
    private static final int DH = 380;
    private static final int SIDEBAR = 150;
    private static final int PICKER_H = 100;

    private static final Identifier LOGO = gui("logo");
    private static final Identifier PETAL_FILL = gui("petal_fill");
    private static final Identifier PETAL_OUTLINE = gui("petal_outline");
    private static final Identifier CIRCLE = gui("circle");
    private static final Identifier CIRCLE_OUTLINE = gui("circle_outline");

    private static final String[] FILTERS = {"All", "Enabled", "Disabled", "Server Rules"};

    private record Nav(String label, String icon, View view, Category category, boolean favorites) {}

    private static final Nav[] NAV_MODULES = moduleNav();
    private static final Nav[] NAV_LIBRARY = {
            new Nav("Favorites", "star_empty", View.MODULES, null, true),
            new Nav("Cosmetics", "cosmetics", View.COSMETICS, null, false),
            new Nav("Profiles", "profile", View.PROFILES, null, false),
    };
    private static final Nav[] NAV_CLIENT = {
            new Nav("Design", "themes", View.DESIGN, null, false),
            new Nav("Settings", "settings", View.SETTINGS, null, false),
    };

    /** Petals of the Bloom hub, clockwise from the top. */
    private static final Nav[] PETALS = {
            categoryNav(Category.COMBAT),
            categoryNav(Category.HUD),
            categoryNav(Category.VISUAL),
            categoryNav(Category.CAMERA),
            categoryNav(Category.CHAT),
            categoryNav(Category.UTILITY),
            categoryNav(Category.PERFORMANCE),
            new Nav("Cosmetics", "cosmetics", View.COSMETICS, null, false),
    };

    private static Nav categoryNav(Category c) {
        return new Nav(c.getDisplayName(), c.getIcon(), View.MODULES, c, false);
    }

    private static Nav[] moduleNav() {
        Category[] categories = Category.values();
        Nav[] nav = new Nav[categories.length + 1];
        nav[0] = new Nav("All Modules", "all", View.MODULES, null, false);
        for (int i = 0; i < categories.length; i++) nav[i + 1] = categoryNav(categories[i]);
        return nav;
    }

    private record State(View view, Category category, boolean favorites, Module module) {}

    private record Hit(int x1, int y1, int x2, int y2, Runnable left, Runnable right) {}

    private record Section(String title, List<Setting<?, ?>> settings) {}

    private final Screen parent;
    private View view;
    private Category category;
    private boolean favoritesOnly;
    private Module module;
    private int filter;
    private final Deque<State> history = new ArrayDeque<>();

    private final Input search = new Input("Search modules and settings", 40);
    private final Input profileName = new Input("New profile name", 20);
    private final Input cosSearch = new Input("Search cosmetics", 30);
    private Cosmetics.Slot cosSlot = Cosmetics.Slot.CAPE;
    private String cosPalette;
    private boolean cosOwnedOnly;
    /** Buy / gift dialog on the cosmetics page. */
    private dev.onix.client.cosmetics.CosmeticCatalog.Item cosDialog;
    private boolean cosGiftMode;
    private boolean cosBusy;
    private String cosDialogError;
    private final Input giftTo = new Input("Player name", 16);
    private final Input giftMessage = new Input("Message (optional)", 80);
    private final Map<TextSetting, Input> textInputs = new HashMap<>();

    private final List<Hit> hits = new ArrayList<>();
    /** Areas that react to the mouse wheel (sliders, mode controls); checked before the page scrolls. */
    private record Wheel(int x1, int y1, int x2, int y2, java.util.function.IntConsumer action) {}
    private final List<Wheel> wheels = new ArrayList<>();
    private final List<Hit> middleHits = new ArrayList<>();
    /** Number being typed in after clicking a slider's value. */
    private NumberSetting editingNumber;
    private final Input numberInput = new Input("", 12);
    /** Keyboard focus in the module grid (arrow keys), -1 = none. */
    private int focusIndex = -1;
    private List<ModuleManager.Match> gridList = List.of();
    private int gridCols = 3;
    private int[] numberField;
    private String confirmKeyDelete;
    private long confirmKeyDeleteUntil;
    private int gridViewHeight;
    /** Toggle pulses on module cards: module id → start time. */
    private final Map<String, Long> pulses = new HashMap<>();
    private int[] clip;
    private final Map<Object, Anim> anims = new HashMap<>();
    private final Anim open = new Anim(0);
    private final Anim viewAnim = new Anim(1);
    private final MenuParticles particles = new MenuParticles();

    private double scroll;
    private double scrollTarget;
    private int contentHeight;

    // slider drag
    private NumberSetting draggingSlider;
    private int sliderX1;
    private int sliderX2;

    // color picker
    private ColorSetting expandedColor;
    private final Map<ColorSetting, float[]> hsvCache = new HashMap<>();
    private int colorDrag; // 0 none, 1 saturation/value, 2 hue, 3 alpha
    private int[] colorDragRect;

    // dropdown
    private ModeSetting dropdown;
    private int[] dropdownAnchor;
    private boolean dropdownSeen;
    private int dropdownOffset = -1;
    private int[] dropdownRect;

    // keybind capture
    private KeybindSetting listening;

    private boolean draggingPreview;
    private float previewRotation = 160f;
    private long lastFrame = System.nanoTime();
    private String tooltip;
    private String profileMessage = "";
    private long confirmResetUntil;
    private long confirmDeleteUntil;
    private final ThemeEditor themeEditor = new ThemeEditor();

    private int vw;
    private int vh;
    private float uiScale = 1f;
    private float openScale = 1f;

    public OnixMenuScreen(Screen parent) {
        this(parent, null);
    }

    public OnixMenuScreen(Screen parent, View start) {
        super(Text.literal("Onix"));
        this.parent = parent;
        View first = start != null ? start : View.HUB;
        if (first == View.HUB && !bloom()) first = View.MODULES;
        this.view = first;
        search.onChange(() -> {
            scroll = scrollTarget = 0;
            if (!search.text().isEmpty() && view != View.MODULES) navigate(View.MODULES, null, false, null);
        });
        profileName.onSubmit(this::createProfile);
        cosSearch.onChange(() -> scroll = scrollTarget = 0);
        giftTo.onSubmit(() -> focusOnly(giftMessage));
        giftMessage.onSubmit(() -> {
            if (cosDialog != null && cosGiftMode) giftCosmetic(cosDialog);
        });
    }

    /** Opens the menu directly on a module's settings page. */
    public static OnixMenuScreen forModule(Screen parent, Module module) {
        OnixMenuScreen screen = new OnixMenuScreen(parent, View.MODULE);
        screen.module = module;
        screen.category = module.getCategory();
        return screen;
    }

    private static Identifier gui(String name) {
        return Identifier.of("onix", "textures/gui/" + name + ".png");
    }

    private static Identifier icon(String name) {
        return gui("icon_" + name);
    }

    private static boolean bloom() {
        return OnixClient.CONFIG != null && OnixClient.CONFIG.menuStyle.is("Bloom");
    }

    private static boolean animationsOn() {
        return OnixClient.CONFIG == null || OnixClient.CONFIG.menuAnimations.isOn();
    }

    @Override
    protected void init() {
        if (!animationsOn()) open.set(1);
        if (view == View.MODULE && module == null) view = View.MODULES;
    }

    // ================================================================== navigation

    private void navigate(View v, Category c, boolean favorites, Module m) {
        history.push(new State(view, category, favoritesOnly, module));
        view = v;
        category = c;
        favoritesOnly = favorites;
        module = m;
        resetPageState();
        click();
    }

    private void resetPageState() {
        commitNumber();
        numberField = null;
        focusIndex = -1;
        scroll = scrollTarget = 0;
        viewAnim.set(animationsOn() ? 0 : 1);
        profileName.focus(false);
        unfocusTextInputs();
        dropdown = null;
        listening = null;
        expandedColor = null;
        draggingSlider = null;
        colorDrag = 0;
    }

    private void open(Nav nav) {
        if (!search.text().isEmpty()) search.setText("");
        navigate(nav.view(), nav.category(), nav.favorites(), null);
    }

    private View homeView() {
        return bloom() ? View.HUB : View.MODULES;
    }

    private void back() {
        if (!search.text().isEmpty() && view == View.MODULES) {
            search.setText("");
            search.focus(false);
            return;
        }
        if (history.isEmpty()) {
            if (view != homeView()) {
                view = homeView();
                category = null;
                favoritesOnly = false;
                module = null;
                resetPageState();
            }
            return;
        }
        State s = history.pop();
        view = s.view();
        category = s.category();
        favoritesOnly = s.favorites();
        module = s.module();
        if (view == View.HUB && !bloom()) view = View.MODULES;
        if (view == View.MODULE && module == null) view = View.MODULES;
        resetPageState();
    }

    private void openModule(Module m) {
        navigate(View.MODULE, m.getCategory(), false, m);
    }

    private void click() {
        if (client != null && (OnixClient.CONFIG == null || OnixClient.CONFIG.clickSounds.isOn())) {
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, 0.18f));
        }
    }

    @Override
    public void close() {
        Cosmetics.clearPreview();
        themeEditor.flush();
        OnixClient.save();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ================================================================== coordinates

    private double vx(double mx) {
        return ((mx - width / 2.0) / openScale + width / 2.0) / uiScale;
    }

    private double vy(double my) {
        return ((my - height / 2.0) / openScale + height / 2.0) / uiScale;
    }

    private int sx(double v) {
        return (int) Math.round((v * uiScale - width / 2.0) * openScale + width / 2.0);
    }

    private int sy(double v) {
        return (int) Math.round((v * uiScale - height / 2.0) * openScale + height / 2.0);
    }

    // ================================================================== render

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // drawn in render()
    }

    @Override
    public void render(DrawContext ctx, int rawX, int rawY, float delta) {
        Theme.refresh();
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        float t = Anim.easeOut(open.to(1f, 10f));
        float vt = Anim.easeOut(viewAnim.to(1f, 12f));
        scroll += (scrollTarget - scroll) * Math.min(1, dt * 18);
        hits.clear();
        wheels.clear();
        middleHits.clear();
        clip = null;
        tooltip = null;
        dropdownSeen = false;
        Cosmetics.clearPreview();

        // ---- background
        if (client.world == null) {
            ctx.fillGradient(0, 0, width, height, Theme.lerpColor(0xFF07070A, Theme.current().panel(), 0.6f), 0xFF050507);
        }
        if (OnixClient.CONFIG.backgroundDim.isOn() || client.world == null) ctx.fill(0, 0, width, height, Theme.fade(Theme.BACKDROP, t));
        if (OnixClient.CONFIG.menuParticles.isOn()) particles.render(ctx, width, height, t * 0.7f);

        // ---- scale the fixed design grid to the screen
        float fit = Math.min((width - 16f) / DW, (height - 16f) / DH);
        float base = Math.min(fit, 1.2f);
        uiScale = Math.max(0.45f, Math.min(base * OnixClient.CONFIG.menuScaleFactor(), fit));
        openScale = 0.965f + 0.035f * t;
        vw = Math.round(width / uiScale);
        vh = Math.round(height / uiScale);
        int mx = (int) Math.floor(vx(rawX));
        int my = (int) Math.floor(vy(rawY));

        ctx.getMatrices().push();
        ctx.getMatrices().translate(width / 2f, height / 2f, 0);
        ctx.getMatrices().scale(openScale, openScale, 1f);
        ctx.getMatrices().translate(-width / 2f, -height / 2f, 0);
        ctx.getMatrices().scale(uiScale, uiScale, 1f);

        if (bloom() && view == View.HUB) {
            renderHub(ctx, mx, my);
        } else if (bloom()) {
            renderFloating(ctx, mx, my, dt, vt);
        } else {
            renderClassic(ctx, mx, my, dt, vt);
        }
        renderDropdown(ctx, mx, my);
        ctx.getMatrices().pop();

        if (tooltip != null && OnixClient.CONFIG.tooltips.isOn()) {
            List<Text> lines = new ArrayList<>();
            for (String line : wrap(tooltip, 200, 6)) lines.add(Theme.ui(line));
            ctx.drawTooltip(textRenderer, lines, rawX, rawY);
        }
    }

    // ================================================================== classic window

    private void renderClassic(DrawContext ctx, int mx, int my, float dt, float vt) {
        int x0 = (vw - DW) / 2;
        int y0 = (vh - DH) / 2;
        int x1 = x0 + DW;
        int y1 = y0 + DH;
        Theme.window(ctx, x0, y0, x1, y1, 10);
        hit(x0, y0, x1, y1, () -> {}, () -> {}); // clicks inside the window never close it

        // sidebar
        Theme.round(ctx, x0 + 1, y0 + 1, x0 + SIDEBAR, y1 - 1, 9, Theme.SURFACE_SUNKEN);
        ctx.fill(x0 + SIDEBAR - 9, y0 + 1, x0 + SIDEBAR, y1 - 1, Theme.SURFACE_SUNKEN);
        ctx.fill(x0 + SIDEBAR, y0 + 1, x0 + SIDEBAR + 1, y1 - 1, Theme.BORDER);
        renderSidebar(ctx, mx, my, x0, y0, y1);

        // top bar + content
        int cx1 = x0 + SIDEBAR + 20;
        int cx2 = x1 - 20;
        renderTopbar(ctx, mx, my, cx1, y0, cx2, true);
        ctx.fill(x0 + SIDEBAR + 1, y0 + 50, x1 - 1, y0 + 51, Theme.BORDER);

        int[] content = {cx1, y0 + 64, cx2, y1 - 18};
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, (1 - vt) * 4, 0);
        renderPage(ctx, mx, my, content, dt);
        ctx.getMatrices().pop();
    }

    private void renderSidebar(DrawContext ctx, int mx, int my, int x0, int y0, int y1) {
        int x = x0 + 12;
        drawTinted(ctx, LOGO, x + 2, y0 + 15, 14, 14, 128, 0xFFFFFFFF, 1f);
        Theme.textBold(ctx, "Onix", x + 21, y0 + 18, Theme.TEXT);
        Theme.small(ctx, "Client", x + 23 + Theme.boldWidth("Onix"), y0 + 20, Theme.TEXT_3);

        int y = y0 + 44;
        y = navGroup(ctx, mx, my, "Modules", NAV_MODULES, x, y);
        y = navGroup(ctx, mx, my, "Library", NAV_LIBRARY, x, y + 5);
        navGroup(ctx, mx, my, "Client", NAV_CLIENT, x, y + 5);

        // account
        int ay = y1 - 36;
        int ax2 = x0 + SIDEBAR - 10;
        boolean hov = hovered(mx, my, x - 2, ay, ax2, ay + 28);
        if (hov) Theme.round(ctx, x - 2, ay, ax2, ay + 28, 6, Theme.SURFACE_2);
        SkinTextures skin = client.getSkinProvider().getSkinTextures(client.getGameProfile());
        PlayerSkinDrawer.draw(ctx, skin, x + 4, ay + 6, 16);
        Theme.text(ctx, Theme.ellipsize(client.getSession().getUsername(), 92), x + 26, ay + 6, Theme.TEXT);
        Theme.small(ctx, Theme.ellipsize("Profile: " + OnixClient.PROFILES.active(), 120), x + 26, ay + 17, Theme.TEXT_3);
        hit(x - 2, ay, ax2, ay + 28, () -> navigate(View.PROFILES, null, false, null), null);
    }

    private int navGroup(DrawContext ctx, int mx, int my, String title, Nav[] items, int x, int y) {
        Theme.label(ctx, title, x + 4, y, Theme.TEXT_3);
        y += 9;
        int x2 = x + SIDEBAR - 24;
        for (Nav nav : items) {
            boolean active = isActive(nav);
            boolean hov = hovered(mx, my, x - 2, y, x2, y + 17);
            float ht = anim("nav:" + nav.label(), hov || active, 18f);
            if (ht > 0.01f) Theme.round(ctx, x - 2, y, x2, y + 17, 5, Theme.fade(active ? Theme.SURFACE_3 : Theme.SURFACE_2, Math.max(ht, active ? 1 : 0)));
            if (active) Theme.round(ctx, x - 2, y + 4, x, y + 13, 1, Theme.accent());
            drawIcon(ctx, nav.icon(), x + 4, y + 4, 9, active ? Theme.accent() : Theme.lerpColor(Theme.TEXT_3, Theme.TEXT_2, ht));
            Theme.text(ctx, nav.label(), x + 19, y + 5, active ? Theme.TEXT : Theme.lerpColor(Theme.TEXT_2, Theme.TEXT, ht));
            String count = navCount(nav);
            if (count != null) Theme.small(ctx, count, x2 - 6 - Theme.smallWidth(count), y + 6, Theme.TEXT_3);
            hit(x - 2, y, x2, y + 17, () -> open(nav), null);
            y += 18;
        }
        return y;
    }

    private boolean isActive(Nav nav) {
        if (!search.text().isEmpty()) return false;
        View v = view == View.MODULE ? View.MODULES : view;
        if (v != nav.view()) return false;
        if (v != View.MODULES) return true;
        if (view == View.MODULE) return !nav.favorites() && nav.category() != null && nav.category() == category;
        return nav.favorites() == favoritesOnly && nav.category() == category;
    }

    private String navCount(Nav nav) {
        if (nav.view() != View.MODULES) return null;
        if (nav.favorites()) return String.valueOf(OnixClient.CONFIG.favorites.size());
        return String.valueOf(OnixClient.MODULES.count(nav.category()));
    }

    /** Title, subtitle, search field and HUD editor button. */
    private void renderTopbar(DrawContext ctx, int mx, int my, int x1, int y0, int x2, boolean withSearch) {
        int ty = y0 + 17;
        int tx = x1;
        boolean canGoBack = view == View.MODULE || (bloom() && view != View.HUB);
        if (canGoBack) {
            boolean bh = hovered(mx, my, tx - 4, ty - 4, tx + 14, ty + 14);
            if (bh) Theme.round(ctx, tx - 4, ty - 4, tx + 14, ty + 14, 4, Theme.SURFACE_3);
            drawIcon(ctx, "back", tx, ty, 10, bh ? Theme.TEXT : Theme.TEXT_2);
            hit(tx - 4, ty - 4, tx + 14, ty + 14, this::back, null);
            tooltipIf(bh, "Back");
            tx += 20;
        }
        int reserved = withSearch ? 250 : 0;
        Theme.scaled(ctx, Theme.display(Theme.ellipsize(pageTitle(), (int) ((x2 - tx - reserved) / 1.15f))), tx, ty - 1, 1.15f, Theme.TEXT);
        Theme.small(ctx, Theme.ellipsize(pageSubtitle(), (int) ((x2 - tx - reserved) / 0.75f)), tx, ty + 14, Theme.TEXT_3);

        if (!withSearch) return;
        int sw = 160;
        int sx1 = x2 - sw;
        int sy1 = y0 + 14;
        boolean focused = search.focused();
        boolean sh = hovered(mx, my, sx1, sy1, x2, sy1 + 20);
        Theme.card(ctx, sx1, sy1, x2, sy1 + 20, Theme.corner(6), Theme.SURFACE_SUNKEN,
                focused ? Theme.withAlpha(Theme.accent(), 200) : sh ? Theme.BORDER_STRONG : Theme.BORDER);
        drawIcon(ctx, "search", sx1 + 7, sy1 + 6, 8, focused ? Theme.accent() : Theme.TEXT_3);
        String kbd = "Ctrl F";
        int kw = Theme.smallWidth(kbd) + 8;
        boolean showKbd = !focused && search.text().isEmpty();
        if (showKbd) {
            Theme.card(ctx, x2 - kw - 5, sy1 + 4, x2 - 5, sy1 + 16, 3, Theme.SURFACE_2, Theme.BORDER);
            Theme.small(ctx, kbd, x2 - kw - 1, sy1 + 8, Theme.TEXT_3);
        }
        search.render(ctx, sx1 + 20, sy1 + 6, sw - 28 - (showKbd ? kw : 0), Theme.TEXT, Theme.TEXT_3);
        hit(sx1, sy1, x2, sy1 + 20, () -> {
            focusOnly(search);
        }, () -> search.setText(""));

        int hx2 = sx1 - 8;
        int hx1 = hx2 - 20;
        boolean hh = hovered(mx, my, hx1, sy1, hx2, sy1 + 20);
        Theme.card(ctx, hx1, sy1, hx2, sy1 + 20, 6, hh ? Theme.SURFACE_3 : Theme.SURFACE_2, hh ? Theme.BORDER_STRONG : Theme.BORDER);
        drawIcon(ctx, "move", hx1 + 5, sy1 + 5, 10, hh ? Theme.TEXT : Theme.TEXT_2);
        hit(hx1, sy1, hx2, sy1 + 20, () -> client.setScreen(new HudEditorScreen(this)), null);
        tooltipIf(hh, "Edit HUD layout");

        renderCwtButton(ctx, mx, my, hx1 - 6, sy1);
    }

    /** "CWT" pill in the top bar; lit while CWT mode is on. Returns its left edge. */
    private int renderCwtButton(DrawContext ctx, int mx, int my, int right, int y) {
        boolean on = CwtMode.isOn();
        String label = "CWT";
        int w = 16 + Theme.boldWidth(label) + 8;
        int x = right - w;
        boolean hov = hovered(mx, my, x, y, right, y + 20);
        float t = anim("cwt", on, 14f);
        int fill = Theme.lerpColor(hov ? Theme.SURFACE_3 : Theme.SURFACE_2, Theme.accent(), t);
        int border = Theme.lerpColor(hov ? Theme.BORDER_STRONG : Theme.BORDER, Theme.accent(), t);
        Theme.card(ctx, x, y, right, y + 20, Theme.corner(6), fill, border);
        int fg = Theme.lerpColor(hov ? Theme.TEXT : Theme.TEXT_2, Theme.onAccent(), t);
        // status dot: hollow when off, filled when on
        Theme.round(ctx, x + 7, y + 8, x + 11, y + 12, 2, on ? fg : Theme.withAlpha(fg, 110));
        Theme.textBold(ctx, label, x + 15, y + 6, fg);
        hit(x, y, right, y + 20, () -> {
            CwtMode.toggle();
            click();
        }, () -> navigate(View.PROFILES, null, false, null));
        tooltipIf(hov, on
                ? "CWT mode is on – click to return to \"" + returnProfileName() + "\".\nChanges you make now are saved to the CWT profile."
                : "CWT mode: switch to your clanwar setup in one click.\nRight-click to manage profiles.");
        return x;
    }

    private static String returnProfileName() {
        String back = OnixClient.CONFIG.cwtReturnProfile;
        return back == null || back.isEmpty() ? ProfileManager.DEFAULT : back;
    }

    private String pageTitle() {
        return switch (view) {
            case MODULES -> !search.text().isEmpty() ? "Search" : favoritesOnly ? "Favorites" : category == null ? "All Modules" : category.getDisplayName();
            case MODULE -> module.getName();
            case COSMETICS -> "Cosmetics";
            case DESIGN -> "Design";
            case PROFILES -> "Profiles";
            case SETTINGS -> "Settings";
            case HUB -> "Onix";
        };
    }

    private String pageSubtitle() {
        return switch (view) {
            case MODULES -> {
                if (!search.text().isEmpty()) {
                    int n = currentMatches().size();
                    yield n + (n == 1 ? " result" : " results") + " for \"" + search.text() + "\"";
                }
                if (category != null && !favoritesOnly) yield category.getDescription();
                List<ModuleManager.Match> list = currentMatches();
                long on = list.stream().filter(m -> m.module().isEnabled()).count();
                yield list.size() + (list.size() == 1 ? " module" : " modules") + " · " + on + " enabled";
            }
            case MODULE -> module.getDescription();
            case COSMETICS -> "Earn 1 coin per minute played. Only visible to you.";
            case DESIGN -> "Themes, backgrounds, light mode, buttons and loading screens.";
            case PROFILES -> "Save modules, settings and HUD layout as switchable sets.";
            case SETTINGS -> "Interface, HUD editor, notifications, server rules and profiles.";
            case HUB -> "";
        };
    }

    // ================================================================== Bloom style

    private void renderFloating(DrawContext ctx, int mx, int my, float dt, float vt) {
        int w = Math.min(470, vw - 20);
        int h = Math.min(350, vh - 30);
        int x0 = (vw - w) / 2;
        int y0 = (vh - h) / 2;
        Theme.glass(ctx, x0, y0, x0 + w, y0 + h, 10, 0.8f);
        hit(x0, y0, x0 + w, y0 + h, () -> {}, () -> {});
        renderTopbar(ctx, mx, my, x0 + 18, y0, x0 + w - 18, view == View.MODULES);
        ctx.fill(x0 + 1, y0 + 50, x0 + w - 1, y0 + 51, Theme.BORDER);
        int[] content = {x0 + 18, y0 + 64, x0 + w - 18, y0 + h - 16};
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, (1 - vt) * 4, 0);
        renderPage(ctx, mx, my, content, dt);
        ctx.getMatrices().pop();
    }

    private void renderHub(DrawContext ctx, int mx, int my) {
        int cx = vw / 2;
        int cy = vh / 2 + 4;
        float radius = Math.max(40f, Math.min((vh / 2f - 46f) / 1.5f, 100f));
        int petalSize = Math.round(radius * 0.92f);
        float bloomT = Anim.easeOut(open.get());
        float step = 360f / PETALS.length;

        int hovered = -1;
        double dx = mx - cx;
        double dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > radius * 0.42 && dist < radius * 1.55) {
            double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
            hovered = Math.floorMod((int) Math.round(angle / step), PETALS.length);
        }

        int accent = Theme.accent();
        int light = Theme.lerpColor(accent, 0xFFFFFFFF, 0.4f);
        for (int i = 0; i < PETALS.length; i++) {
            float h = anim("petal:" + i, i == hovered, 14f);
            float angle = i * step;
            float r = radius * (0.6f + 0.4f * bloomT) + h * 3f;
            float size = petalSize * (1f + h * 0.05f);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(cx, cy, 0);
            ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
            ctx.getMatrices().translate(-size / 2f, -r - size / 2f, 0);
            int s = Math.round(size);
            drawTinted(ctx, PETAL_FILL, 0, 0, s, s, 128, Theme.lerpColor(Theme.current().panel(), accent, 0.18f + 0.18f * h), 0.75f + 0.15f * h);
            drawTinted(ctx, PETAL_OUTLINE, 0, 0, s, s, 128, light, 0.35f + 0.5f * h);
            ctx.getMatrices().pop();

            double rad = Math.toRadians(angle - 90);
            int px = (int) Math.round(cx + Math.cos(rad) * r);
            int py = (int) Math.round(cy + Math.sin(rad) * r);
            drawIcon(ctx, PETALS[i].icon(), px - 6, py - 9, 12, Theme.lerpColor(Theme.TEXT_2, Theme.TEXT, h));
            String label = PETALS[i].label();
            Theme.small(ctx, label, px - Theme.smallWidth(label) / 2, py + 6, Theme.lerpColor(Theme.TEXT_2, Theme.TEXT, h));
        }
        if (hovered >= 0) {
            Nav nav = PETALS[hovered];
            hit(cx - (int) (radius * 2), cy - (int) (radius * 2), cx + (int) (radius * 2), cy + (int) (radius * 2), () -> open(nav), null);
        }

        int cr = Math.round(radius * 0.38f);
        drawTinted(ctx, CIRCLE, cx - cr, cy - cr, cr * 2, cr * 2, 64, Theme.current().panel(), 0.96f);
        drawTinted(ctx, CIRCLE_OUTLINE, cx - cr, cy - cr, cr * 2, cr * 2, 64, light, 0.55f);
        int head = Math.max(12, Math.round(cr * 0.85f));
        SkinTextures skin = client.getSkinProvider().getSkinTextures(client.getGameProfile());
        PlayerSkinDrawer.draw(ctx, skin, cx - head / 2, cy - head / 2 - 3, head);
        String name = client.getSession().getUsername();
        Theme.small(ctx, name, cx - Theme.smallWidth(name) / 2, cy + head / 2 + 1, Theme.TEXT);
        hit(cx - cr, cy - cr, cx + cr, cy + cr, () -> navigate(View.PROFILES, null, false, null), null);
        tooltipIf(Theme.inside(mx, my, cx - cr, cy - cr, cx + cr, cy + cr), "Profiles");

        drawTinted(ctx, LOGO, 12, 12, 14, 14, 128, 0xFFFFFFFF, 1f);
        Theme.textBold(ctx, "Onix", 31, 15, Theme.TEXT);

        // quick actions, top right
        int bx = vw - 12 - 20;
        bx = iconButton(ctx, mx, my, bx, 10, "settings", "Settings", () -> navigate(View.SETTINGS, null, false, null)) - 6;
        bx = iconButton(ctx, mx, my, bx, 10, "themes", "Design", () -> navigate(View.DESIGN, null, false, null)) - 6;
        bx = iconButton(ctx, mx, my, bx, 10, "star_empty", "Favorites", () -> navigate(View.MODULES, null, true, null)) - 6;
        iconButton(ctx, mx, my, bx, 10, "move", "Edit HUD layout", () -> client.setScreen(new HudEditorScreen(this)));

        // search, bottom
        int w = Math.min(240, vw - 40);
        int x = (vw - w) / 2;
        int y = vh - 30;
        Theme.card(ctx, x, y, x + w, y + 20, 10, Theme.SURFACE, search.focused() ? Theme.withAlpha(accent, 200) : Theme.BORDER_STRONG);
        drawIcon(ctx, "search", x + 9, y + 6, 8, Theme.TEXT_3);
        search.render(ctx, x + 22, y + 6, w - 32, Theme.TEXT, Theme.TEXT_3);
        hit(x, y, x + w, y + 20, () -> focusOnly(search), () -> search.setText(""));
    }

    /** Square 20px icon button; returns its left edge. */
    private int iconButton(DrawContext ctx, int mx, int my, int x, int y, String iconName, String tip, Runnable action) {
        boolean hov = hovered(mx, my, x, y, x + 20, y + 20);
        Theme.card(ctx, x, y, x + 20, y + 20, Theme.corner(6), hov ? Theme.SURFACE_3 : Theme.SURFACE_2, hov ? Theme.BORDER_STRONG : Theme.BORDER);
        drawIcon(ctx, iconName, x + 5, y + 5, 10, hov ? Theme.TEXT : Theme.TEXT_2);
        hit(x, y, x + 20, y + 20, action, null);
        tooltipIf(hov, tip);
        return x;
    }

    // ================================================================== pages

    private void renderPage(DrawContext ctx, int mx, int my, int[] r, float dt) {
        switch (view) {
            case MODULES -> renderModules(ctx, mx, my, r);
            case MODULE -> renderModulePage(ctx, mx, my, r);
            case COSMETICS -> renderCosmetics(ctx, mx, my, r, dt);
            case DESIGN -> renderDesign(ctx, mx, my, r);
            case PROFILES -> renderProfiles(ctx, mx, my, r);
            case SETTINGS -> renderSettingsPage(ctx, mx, my, r);
            default -> {}
        }
    }

    private List<ModuleManager.Match> currentMatches() {
        boolean searching = !search.text().isEmpty();
        List<ModuleManager.Match> list = new ArrayList<>(OnixClient.MODULES.search(searching ? null : category, search.text()));
        if (favoritesOnly && !searching) list.removeIf(m -> !OnixClient.CONFIG.favorites.contains(m.module().getId()));
        if (filter == 1) list.removeIf(m -> !m.module().isEnabled());
        if (filter == 2) list.removeIf(m -> m.module().isEnabled());
        if (filter == 3) list.removeIf(m -> !m.module().isRestricted());
        return list;
    }

    // ---------- module grid

    private void renderModules(DrawContext ctx, int mx, int my, int[] r) {
        // filter chips
        int fx = r[0];
        for (int i = 0; i < FILTERS.length; i++) {
            String label = FILTERS[i];
            int cw = Theme.smallWidth(label) + 16;
            boolean sel = filter == i;
            boolean hov = hovered(mx, my, fx, r[1], fx + cw, r[1] + 15);
            Theme.card(ctx, fx, r[1], fx + cw, r[1] + 15, Theme.corner(7), sel ? Theme.SURFACE_4 : hov ? Theme.SURFACE_3 : Theme.SURFACE_2,
                    sel ? Theme.BORDER_STRONG : Theme.BORDER);
            Theme.small(ctx, label, fx + 8, r[1] + 5, sel ? Theme.TEXT : Theme.TEXT_2);
            int index = i;
            hit(fx, r[1], fx + cw, r[1] + 15, () -> {
                filter = index;
                scroll = scrollTarget = 0;
                click();
            }, null);
            fx += cw + 5;
        }
        String hint = "Click to toggle · Right-click for settings";
        int hintX = r[2] - Theme.smallWidth(hint);
        boolean hintHov = hovered(mx, my, hintX, r[1] + 2, r[2], r[1] + 13);
        Theme.small(ctx, hint, hintX, r[1] + 5, hintHov ? Theme.TEXT_2 : Theme.TEXT_3);
        tooltipIf(hintHov, "Click: turn on/off\nRight-click: settings\nMiddle-click: favorite\n"
                + "Arrow keys: move · Enter: turn on/off · Shift+Enter: settings\nType anywhere to search");

        int top = r[1] + 24;
        List<ModuleManager.Match> list = currentMatches();
        int cw = r[2] - r[0];
        int cols = cw >= 380 ? 3 : 2;
        int gap = 8;
        int cardW = (cw - gap * (cols - 1)) / cols;
        int cardH = 70;
        gridList = list;
        gridCols = cols;
        gridViewHeight = r[3] - top;
        if (focusIndex >= list.size()) focusIndex = list.size() - 1;
        int rows = (list.size() + cols - 1) / cols;
        contentHeight = Math.max(0, rows * (cardH + gap) - gap);
        clampScroll(r[3] - top);

        if (list.isEmpty()) {
            int cx = (r[0] + r[2]) / 2;
            int cy = (top + r[3]) / 2 - 16;
            boolean favEmpty = favoritesOnly && search.text().isEmpty() && filter == 0;
            drawIcon(ctx, favEmpty ? "star_empty" : "search", cx - 8, cy - 8, 16, Theme.TEXT_3);
            Theme.textCentered(ctx, favEmpty ? "No favorites yet" : "Nothing found", cx, cy + 16, Theme.TEXT_2);
            Theme.textCentered(ctx, favEmpty ? "Star a module to pin it here." : "Try another word or filter.", cx, cy + 28, Theme.TEXT_3);
            return;
        }

        beginClip(ctx, r[0] - 4, top - 2, r[2] + 6, r[3]);
        for (int i = 0; i < list.size(); i++) {
            int x = r[0] + (i % cols) * (cardW + gap);
            int y = top + (i / cols) * (cardH + gap) - (int) Math.round(scroll);
            if (y + cardH < top || y > r[3]) continue;
            renderModuleCard(ctx, mx, my, list.get(i), x, y, cardW, cardH);
        }
        endClip(ctx);
        scrollbar(ctx, r[2] + 4, top, r[3]);
    }

    private void renderModuleCard(DrawContext ctx, int mx, int my, ModuleManager.Match match, int x, int y, int w, int h) {
        Module m = match.module();
        boolean hov = hovered(mx, my, x, y, x + w, y + h);
        float ht = anim("card:" + m.getId(), hov, 18f);
        float on = anim("on:" + m.getId(), m.isEnabled(), 14f);
        int border = Theme.lerpColor(Theme.lerpColor(Theme.BORDER, Theme.BORDER_STRONG, ht), Theme.lerpColor(Theme.BORDER_STRONG, Theme.accent(), 0.35f), on);
        // toggle pulse: a ring that grows and fades
        Long pulse = pulses.get(m.getId());
        if (pulse != null) {
            float pt = (System.currentTimeMillis() - pulse) / 450f;
            if (pt >= 1) {
                pulses.remove(m.getId());
            } else {
                int e = 1 + Math.round(5 * pt);
                Theme.round(ctx, x - e, y - e, x + w + e, y + h + e, Theme.corner(8) + e, Theme.withAlpha(Theme.accent(), Math.round(110 * (1 - pt))));
            }
        }
        boolean focused = focusIndex >= 0 && focusIndex < gridList.size() && gridList.get(focusIndex).module() == m;
        if (focused) Theme.round(ctx, x - 2, y - 2, x + w + 2, y + h + 2, Theme.corner(8) + 2, Theme.withAlpha(Theme.accent(), 170));
        Theme.card(ctx, x, y, x + w, y + h, Theme.corner(8), Theme.lerpColor(Theme.SURFACE_2, Theme.SURFACE_3, Math.max(ht, focused ? 0.6f : 0f)), border);

        drawIcon(ctx, m.getCategory().getIcon(), x + 11, y + 11, 9, Theme.lerpColor(Theme.TEXT_3, Theme.accent(), on));
        // server-rule note and favorite star, top right
        boolean note = m.isRestricted() && OnixClient.CONFIG.ruleNotes.isOn();
        Theme.textBold(ctx, Theme.ellipsize(m.getName(), w - (note ? 62 : 46)), x + 26, y + 11, Theme.TEXT);
        int starX = note ? x + w - 34 : x + w - 19;
        boolean noteHov = note && hovered(mx, my, x + w - 22, y + 6, x + w - 6, y + 22);
        if (note) drawIcon(ctx, "warning", x + w - 19, y + 10, 9, Theme.withAlpha(Theme.ORANGE, noteHov ? 255 : 200));
        boolean fav = OnixClient.CONFIG.favorites.contains(m.getId());
        boolean starHov = hovered(mx, my, starX - 3, y + 6, starX + 13, y + 22);
        if (fav || hov) drawIcon(ctx, fav ? "star" : "star_empty", starX, y + 10, 9, fav ? Theme.accent() : starHov ? Theme.TEXT : Theme.TEXT_3);

        int textW = (int) ((w - 22) / 0.75f);
        if (match.viaSetting() != null) {
            Theme.small(ctx, Theme.ellipsize(m.getDescription(), textW), x + 11, y + 27, Theme.TEXT_2);
            Theme.small(ctx, Theme.ellipsize("Setting: " + match.viaSetting(), textW), x + 11, y + 36, Theme.accent());
        } else {
            List<String> lines = wrap(m.getDescription(), textW, 2);
            for (int i = 0; i < lines.size(); i++) Theme.small(ctx, lines.get(i), x + 11, y + 27 + i * 9, Theme.TEXT_2);
        }

        // footer
        int fy = y + h - 19;
        int count = m.visibleSettingCount();
        String settingsLabel = count + (count == 1 ? " setting" : " settings");
        int gw = 14 + Theme.smallWidth(settingsLabel);
        boolean gearHov = hovered(mx, my, x + 7, fy - 2, x + 13 + gw, fy + 12);
        if (gearHov) Theme.round(ctx, x + 6, fy - 3, x + 14 + gw, fy + 12, 4, Theme.SURFACE_4);
        drawIcon(ctx, "settings", x + 10, fy + 1, 8, gearHov ? Theme.TEXT : Theme.TEXT_3);
        Theme.small(ctx, settingsLabel, x + 22, fy + 2, gearHov ? Theme.TEXT : Theme.TEXT_3);
        if (m.toggleKey.isBound()) {
            String key = m.toggleKey.display();
            int kw = Theme.smallWidth(key) + 8;
            int kx = x + w - 36 - kw;
            Theme.card(ctx, kx, fy - 1, kx + kw, fy + 10, 3, Theme.SURFACE_2, Theme.BORDER);
            Theme.small(ctx, key, kx + 4, fy + 2, Theme.TEXT_3);
        }
        Theme.toggle(ctx, x + w - 29, fy, on);

        hit(x, y, x + w, y + h, () -> toggleModule(m), () -> openModule(m));
        middle(x, y, x + w, y + h, () -> {
            boolean added = OnixClient.CONFIG.toggleFavorite(m.getId());
            click();
            Notifications.show(added ? "Added to favorites" : "Removed from favorites", m.getName());
        });
        hit(starX - 3, y + 6, starX + 13, y + 22, () -> OnixClient.CONFIG.toggleFavorite(m.getId()), null);
        if (note) hit(x + w - 22, y + 6, x + w - 6, y + 22, () -> openModule(m), () -> openModule(m));
        hit(x + 6, fy - 3, x + 14 + gw, fy + 12, () -> openModule(m), () -> openModule(m));
        tooltipIf(starHov, fav ? "Remove from favorites" : "Add to favorites");
        tooltipIf(noteHov, "May not be allowed on every server\n" + m.restriction());
    }

    // ---------- module page

    private void renderModulePage(DrawContext ctx, int mx, int my, int[] r) {
        Module m = module;
        int x = r[0];
        int w = r[2] - r[0];

        // status card
        float on = anim("on:" + m.getId(), m.isEnabled(), 14f);
        int hy = r[1];
        Theme.card(ctx, x, hy, r[2], hy + 36, 8, Theme.SURFACE_2, Theme.lerpColor(Theme.BORDER, Theme.withAlpha(Theme.accent(), 160), on * 0.6f));
        drawIcon(ctx, m.getCategory().getIcon(), x + 12, hy + 13, 10, Theme.lerpColor(Theme.TEXT_3, Theme.accent(), on));
        Theme.textBold(ctx, m.isEnabled() ? "Enabled" : "Disabled", x + 30, hy + 9, Theme.TEXT);
        String meta = m.getCategory().getDisplayName() + " · " + m.visibleSettingCount() + " settings"
                + (m.toggleKey.isBound() ? " · Key " + m.toggleKey.display() : "")
                + (m.statusLine() != null ? " · " + m.statusLine() : "");
        Theme.small(ctx, meta, x + 30, hy + 21, Theme.TEXT_3);
        Theme.toggle(ctx, r[2] - 30, hy + 13, on);
        boolean fav = OnixClient.CONFIG.favorites.contains(m.getId());
        int starX = r[2] - 52;
        boolean starHov = hovered(mx, my, starX - 4, hy + 9, starX + 14, hy + 27);
        drawIcon(ctx, fav ? "star" : "star_empty", starX, hy + 13, 10, fav ? Theme.accent() : starHov ? Theme.TEXT : Theme.TEXT_3);
        hit(x, hy, r[2], hy + 36, () -> {
            m.toggle();
            click();
            OnixClient.save();
        }, null);
        hit(starX - 4, hy + 9, starX + 14, hy + 27, () -> OnixClient.CONFIG.toggleFavorite(m.getId()), null);
        tooltipIf(starHov, fav ? "Remove from favorites" : "Add to favorites");

        int top = r[1] + 48;
        if (m.isRestricted() && OnixClient.CONFIG.ruleNotes.isOn()) {
            top += renderRuleNote(ctx, mx, my, m, x, top - 4, r[2]);
        }
        if (m instanceof HudModule hud) {
            // live preview: updates while you change settings
            int ph = 58;
            Theme.card(ctx, x, top - 4, r[2], top - 4 + ph, 8, Theme.SURFACE_SUNKEN, Theme.BORDER);
            Theme.label(ctx, "Live Preview", x + 10, top + 2, Theme.TEXT_3);
            ctx.enableScissor(sx(x + 1), sy(top - 3), sx(r[2] - 1), sy(top - 5 + ph));
            try {
                hud.renderPreview(ctx, (x + r[2]) / 2, top - 4 + ph / 2 + 3, w - 40, ph - 18);
            } catch (RuntimeException e) {
                Theme.textCentered(ctx, "Preview not available", (x + r[2]) / 2, top + ph / 2 - 8, Theme.TEXT_3);
            }
            ctx.disableScissor();
            top += ph + 6;
        }
        int bottom = r[3] - 30;
        List<Section> sections = sections(m.getSettings());
        boolean skyGallery = m instanceof CustomSkyModule;
        clampScroll(bottom - top);
        beginClip(ctx, x - 4, top - 2, r[2] + 6, bottom);
        int y = top - (int) Math.round(scroll);
        int contentTop = y;
        if (skyGallery) {
            Theme.label(ctx, "Skies · click for day, right-click for night", x + 2, y, Theme.TEXT_3);
            y += 10 + renderSkyGallery(ctx, mx, my, (CustomSkyModule) m, x, y + 10, w) + 12;
        }
        if (m instanceof KeybindProfilesModule keys) {
            Theme.label(ctx, "Keybind profiles · click to apply", x + 2, y, Theme.TEXT_3);
            y += 10 + renderKeybindProfiles(ctx, mx, my, keys, x, y + 10, w) + 12;
        }
        for (Section section : sections) y += renderSection(ctx, mx, my, section, x, y, w);
        contentHeight = y - contentTop;
        endClip(ctx);
        scrollbar(ctx, r[2] + 4, top, bottom);
        if (sections.isEmpty()) Theme.textCentered(ctx, "This module has no settings.", (r[0] + r[2]) / 2, top + 20, Theme.TEXT_3);

        int by = r[3] - 20;
        int bx = x;
        bx += button(ctx, mx, my, bx, by, 112, 20, "Reset to Defaults", false, () -> {
            for (Setting<?, ?> s : m.getSettings()) s.reset();
            if (m instanceof HudModule hud) hud.resetPosition();
            hsvCache.clear();
            OnixClient.save();
            Notifications.show(m.getName(), "Settings reset to defaults");
        }) + 6;
        if (m instanceof HudModule) {
            button(ctx, mx, my, bx, by, 96, 20, "Edit Position", false, () -> client.setScreen(new HudEditorScreen(this)));
        }
        String hint = "Right-click a setting to reset it";
        Theme.small(ctx, hint, r[2] - Theme.smallWidth(hint), by + 7, Theme.TEXT_3);
    }

    /** Orange note on modules some servers do not allow; returns the height used including the gap. */
    private int renderRuleNote(DrawContext ctx, int mx, int my, Module m, int x, int y, int x2) {
        boolean paused = ServerRules.isPaused(m);
        int h = 32;
        Theme.card(ctx, x, y, x2, y + h, 8, Theme.lerpColor(Theme.SURFACE_2, Theme.ORANGE, 0.07f), Theme.withAlpha(Theme.ORANGE, 110));
        drawIcon(ctx, "warning", x + 12, y + 11, 10, Theme.ORANGE);
        String title = paused ? "Paused on this server" : "May not be allowed on every server";
        Theme.textBold(ctx, title, x + 30, y + 7, Theme.TEXT);
        int maxW = (int) ((x2 - x - 40) / 0.75f);
        String reason = Theme.ellipsize(m.restriction() + " Check the rules before you use it.", maxW);
        Theme.small(ctx, reason, x + 30, y + 19, Theme.TEXT_2);
        boolean hov = hovered(mx, my, x, y, x2, y + h);
        tooltipIf(hov, m.restriction() + "\n\nSettings → Server Rules decides what happens when you join a server.");
        return h + 8;
    }

    /** Keybind profile list with apply, update and delete, plus "save current keys"; returns the height. */
    private int renderKeybindProfiles(DrawContext ctx, int mx, int my, KeybindProfilesModule module, int x, int y, int w) {
        List<String> names = KeybindProfiles.list();
        String active = KeybindProfiles.active();
        int rowH = 28;
        int listH = Math.max(1, names.size()) * rowH;
        int h = listH + 34;
        Theme.card(ctx, x, y, x + w, y + h, 8, Theme.SURFACE_2, Theme.BORDER);
        if (names.isEmpty()) {
            Theme.text(ctx, "No profiles yet – save your current keys below.", x + 12, y + 10, Theme.TEXT_3);
        }
        boolean confirming = System.currentTimeMillis() < confirmKeyDeleteUntil;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int ry = y + i * rowH;
            boolean on = name.equals(active);
            if (i > 0) ctx.fill(x + 12, ry, x + w - 12, ry + 1, Theme.BORDER);
            boolean hov = hovered(mx, my, x, ry, x + w, ry + rowH);
            if (hov) Theme.round(ctx, x + 2, ry + 1, x + w - 2, ry + rowH - 1, 6, Theme.withAlpha(Theme.SURFACE_3, 0x90));
            Theme.round(ctx, x + 12, ry + 9, x + 22, ry + 19, 5, on ? Theme.accent() : Theme.BORDER_STRONG);
            Theme.round(ctx, x + 13, ry + 10, x + 21, ry + 18, 4, on ? Theme.accent() : Theme.SURFACE_2);
            if (on) Theme.round(ctx, x + 15, ry + 12, x + 19, ry + 16, 2, Theme.onAccent());
            Theme.text(ctx, Theme.ellipsize(name, w - 170), x + 30, ry + 6, Theme.TEXT);
            Theme.small(ctx, on ? "Active" : "Click to apply", x + 30, ry + 17, on ? Theme.accent() : Theme.TEXT_3);
            hit(x, ry, x + w, ry + rowH, () -> {
                module.apply(name);
                click();
            }, null);

            // delete (second click confirms) and "save current keys here"
            boolean askDelete = confirming && name.equals(confirmKeyDelete);
            int dx2 = x + w - 8;
            int dx1 = dx2 - (askDelete ? Theme.smallWidth("Delete?") + 12 : 18);
            boolean dh = hovered(mx, my, dx1, ry + 6, dx2, ry + rowH - 6);
            if (askDelete) {
                Theme.round(ctx, dx1, ry + 6, dx2, ry + rowH - 6, 4, Theme.withAlpha(Theme.RED, dh ? 90 : 60));
                Theme.small(ctx, "Delete?", dx1 + 6, ry + 11, Theme.TEXT);
            } else if (hov) {
                if (dh) Theme.round(ctx, dx1, ry + 6, dx2, ry + rowH - 6, 4, Theme.SURFACE_4);
                drawIcon(ctx, "x", dx1 + 5, ry + 10, 8, dh ? Theme.RED : Theme.TEXT_3);
            }
            hit(dx1, ry + 6, dx2, ry + rowH - 6, () -> {
                if (askDelete) {
                    KeybindProfiles.delete(name);
                    confirmKeyDeleteUntil = 0;
                    Notifications.show("Keybind profile deleted", name);
                } else {
                    confirmKeyDelete = name;
                    confirmKeyDeleteUntil = System.currentTimeMillis() + 3000;
                }
                click();
            }, null);
            tooltipIf(dh && !askDelete, "Delete this profile");

            String update = "Save keys here";
            int ux2 = dx1 - 6;
            int ux1 = ux2 - Theme.smallWidth(update) - 12;
            boolean uh = hovered(mx, my, ux1, ry + 6, ux2, ry + rowH - 6);
            if (hov) {
                Theme.card(ctx, ux1, ry + 6, ux2, ry + rowH - 6, 4, uh ? Theme.SURFACE_4 : Theme.SURFACE_3, Theme.BORDER);
                Theme.small(ctx, update, ux1 + 6, ry + 11, uh ? Theme.TEXT : Theme.TEXT_2);
            }
            hit(ux1, ry + 6, ux2, ry + rowH - 6, () -> {
                KeybindProfiles.save(name);
                click();
                Notifications.show("Keys saved", "Your current keys are now \"" + name + "\"");
            }, null);
            tooltipIf(uh, "Overwrite this profile with the keys you use right now");
        }

        // save current keys as a new profile
        int iy = y + listH + 7;
        ctx.fill(x + 1, y + listH, x + w - 1, y + listH + 1, Theme.BORDER);
        Runnable saveNew = () -> {
            String name = module.newName.get().trim();
            if (name.isEmpty()) {
                Notifications.warn("Name missing", "Type a name for the new keybind profile");
                return;
            }
            if (KeybindProfiles.save(name)) {
                Notifications.show("Keybind profile saved", ProfileManager.sanitize(name));
                module.newName.set("");
                Input field = textInputs.get(module.newName);
                if (field != null) {
                    field.setText("");
                    field.focus(false);
                }
            }
        };
        Input field = textInputs.computeIfAbsent(module.newName, k -> {
            Input in = new Input("Name, e.g. PvP or Building", k.maxLength());
            in.onChange(() -> k.set(in.text()));
            return in;
        });
        field.onSubmit(saveNew);
        int bw = 118;
        int fx2 = x + w - 12 - bw - 6;
        boolean fh = hovered(mx, my, x + 10, iy, fx2, iy + 20);
        Theme.card(ctx, x + 10, iy, fx2, iy + 20, 5, Theme.SURFACE_SUNKEN,
                field.focused() ? Theme.withAlpha(Theme.accent(), 200) : fh ? Theme.BORDER_STRONG : Theme.BORDER);
        field.render(ctx, x + 17, iy + 6, fx2 - x - 24, Theme.TEXT, Theme.TEXT_3);
        hit(x + 10, iy, fx2, iy + 20, () -> focusOnly(field), null);
        button(ctx, mx, my, x + w - 12 - bw, iy, bw, 20, "Save Current Keys", true, saveNew);
        return h;
    }

    /** Sky thumbnails for the Custom Sky module; returns the height. */
    private int renderSkyGallery(DrawContext ctx, int mx, int my, CustomSkyModule sky, int x0, int y0, int w) {
        List<Skies.Sky> list = Skies.all();
        int cols = 4;
        int gap = 8;
        int tw = (w - gap * (cols - 1)) / cols;
        int th = Math.round(tw * 9 / 16f);
        int count = list.size() + 1;
        for (int i = 0; i < count; i++) {
            int x = x0 + (i % cols) * (tw + gap);
            int y = y0 + (i / cols) * (th + 18 + gap);
            boolean hov = hovered(mx, my, x, y, x + tw, y + th + 14);
            if (i == list.size()) {
                Theme.card(ctx, x, y, x + tw, y + th, 6, hov ? Theme.SURFACE_2 : Theme.SURFACE_SUNKEN, hov ? Theme.BORDER_STRONG : Theme.BORDER);
                drawIcon(ctx, "plus", x + tw / 2 - 6, y + th / 2 - 6, 12, hov ? Theme.TEXT : Theme.TEXT_3);
                Theme.text(ctx, "Your Skies", x + 2, y + th + 5, hov ? Theme.TEXT : Theme.TEXT_2);
                hit(x, y, x + tw, y + th + 14, Skies::openFolder, null);
                tooltipIf(hov, "Opens config/onix/skies – add PNG sky boxes (3 x 2 cube faces).");
                continue;
            }
            Skies.Sky s = list.get(i);
            boolean isDay = sky.daySky.is(s.name());
            boolean isNight = sky.nightSky.is(s.name());
            ctx.enableScissor(sx(x), sy(y), sx(x + tw), sy(y + th));
            RenderSystem.enableBlend();
            if (s.thumbnail() != null) {
                ctx.drawTexture(s.thumbnail(), x, y, tw, th, 0f, 0f, 256, 144, 256, 144);
            } else {
                // custom sky: show its first side face
                ctx.drawTexture(s.texture(), x, y, tw, th, 300f, 80f, 300, 168, 900, 600);
            }
            RenderSystem.disableBlend();
            ctx.disableScissor();
            int border = isDay || isNight ? Theme.accent() : hov ? Theme.withAlpha(0xFFFFFFFF, 90) : Theme.withAlpha(0xFFFFFFFF, 26);
            Theme.outline(ctx, x, y, x + tw, y + th, border);
            int bx = x + 4;
            if (isDay) bx += badge(ctx, bx, y + 4, "Day") + 3;
            if (isNight) badge(ctx, bx, y + 4, "Night");
            Theme.text(ctx, Theme.ellipsize(s.name(), tw - 4), x + 2, y + th + 5, isDay || isNight ? Theme.TEXT : Theme.TEXT_2);
            hit(x, y, x + tw, y + th + 14, () -> {
                sky.daySky.set(s.name());
                click();
                OnixClient.save();
            }, () -> {
                sky.nightSky.set(isNight ? CustomSkyModule.SAME_AS_DAY : s.name());
                click();
                OnixClient.save();
            });
        }
        int rows = (count + cols - 1) / cols;
        return rows * (th + 18 + gap) - gap;
    }

    /** Small pill label on top of a thumbnail; returns its width. */
    private int badge(DrawContext ctx, int x, int y, String text) {
        int w = Theme.smallWidth(text) + 8;
        Theme.round(ctx, x, y, x + w, y + 11, 3, Theme.withAlpha(0xFF000000, 170));
        Theme.small(ctx, text, x + 4, y + 3, 0xFFFFFFFF);
        return w;
    }

    /** Groups visible settings by their section; "Controls" always comes last. */
    private static List<Section> sections(List<Setting<?, ?>> settings) {
        Map<String, List<Setting<?, ?>>> groups = new LinkedHashMap<>();
        for (Setting<?, ?> s : settings) {
            if (!s.isVisible()) continue;
            groups.computeIfAbsent(s.getGroup(), k -> new ArrayList<>()).add(s);
        }
        List<Section> result = new ArrayList<>();
        List<Setting<?, ?>> controls = groups.remove(Module.GROUP_CONTROLS);
        groups.forEach((name, list) -> result.add(new Section(name, list)));
        if (controls != null) result.add(new Section(Module.GROUP_CONTROLS, controls));
        return result;
    }

    private int sectionsHeight(List<Section> sections) {
        int h = 0;
        for (Section s : sections) h += 10 + groupHeight(s.settings()) + 12;
        return Math.max(0, h - 12);
    }

    /** Draws a section label and its settings card; returns the height used including the gap after it. */
    private int renderSection(DrawContext ctx, int mx, int my, Section section, int x, int y, int w) {
        Theme.label(ctx, section.title(), x + 2, y, Theme.TEXT_3);
        int h = renderGroup(ctx, mx, my, section.settings(), x, y + 10, w);
        return 10 + h + 12;
    }

    // ---------- settings as grouped rows

    private static boolean hasDescription(Setting<?, ?> s) {
        return s.getDescription() != null && !s.getDescription().isEmpty();
    }

    private static int baseHeight(Setting<?, ?> s) {
        if (s instanceof NumberSetting) return 36;
        return hasDescription(s) ? 34 : 28;
    }

    private int rowHeight(Setting<?, ?> s) {
        return baseHeight(s) + (s == expandedColor ? PICKER_H : 0);
    }

    private int groupHeight(List<Setting<?, ?>> settings) {
        int h = 0;
        for (Setting<?, ?> s : settings) if (s.isVisible()) h += rowHeight(s);
        return h;
    }

    /** Draws settings as one card with separators; returns its height. */
    private int renderGroup(DrawContext ctx, int mx, int my, List<Setting<?, ?>> settings, int x, int y, int w) {
        int h = groupHeight(settings);
        if (h == 0) return 0;
        Theme.card(ctx, x, y, x + w, y + h, 8, Theme.SURFACE_2, Theme.BORDER);
        int ry = y;
        boolean first = true;
        for (Setting<?, ?> s : settings) {
            if (!s.isVisible()) continue;
            int rh = rowHeight(s);
            if (!first) ctx.fill(x + 12, ry, x + w - 12, ry + 1, Theme.BORDER);
            first = false;
            boolean hov = hovered(mx, my, x, ry, x + w, ry + baseHeight(s));
            if (hov) Theme.round(ctx, x + 2, ry + 1, x + w - 2, ry + baseHeight(s) - 1, 6, Theme.withAlpha(Theme.SURFACE_3, 0x90));
            renderSettingRow(ctx, mx, my, s, x, ry, w);
            ry += rh;
        }
        return h;
    }

    /** Approximate width of the control on the right of a row, so labels can be shortened. */
    private int controlWidth(Setting<?, ?> s, int w) {
        if (s instanceof BoolSetting) return 30;
        if (s instanceof NumberSetting num) return Theme.width(num.display()) + 4;
        if (s instanceof ModeSetting mode) {
            String[] options = mode.modes();
            int seg = segmentedWidth(options);
            return options.length <= 3 && seg < w * 0.45f ? seg : dropdownWidth(mode);
        }
        if (s instanceof ColorSetting) return 86;
        if (s instanceof KeybindSetting kb) return keyButtonWidth(kb);
        if (s instanceof TextSetting) return textFieldWidth(w);
        return 40;
    }

    private void renderSettingRow(DrawContext ctx, int mx, int my, Setting<?, ?> s, int x, int y, int w) {
        int base = baseHeight(s);
        boolean desc = hasDescription(s) && !(s instanceof NumberSetting);
        int textY = desc || s instanceof NumberSetting ? y + 8 : y + (base - 8) / 2;
        int labelMax = Math.max(40, w - 24 - controlWidth(s, w) - 10);

        String name = Theme.ellipsize(s.getName(), labelMax);
        Theme.text(ctx, name, x + 12, textY, Theme.TEXT);
        if (!s.isDefault()) Theme.round(ctx, x + 5, textY + 2, x + 8, textY + 5, 1, Theme.accent());
        if (desc) Theme.small(ctx, Theme.ellipsize(s.getDescription(), (int) (labelMax / 0.75f)), x + 12, y + 20, Theme.TEXT_3);
        boolean nameHov = hovered(mx, my, x + 4, y, x + 12 + Math.max(Theme.width(name), labelMax / 2), y + base);
        if (nameHov) {
            String tip = hasDescription(s) ? s.getDescription() : s.getName();
            if (!s.isDefault()) tip += "\nModified · right-click to reset";
            tooltip = tip;
        }

        // the whole row resets on right-click; controls are registered after it and win on left-click
        Runnable reset = () -> {
            s.reset();
            if (s instanceof ColorSetting c) hsvCache.remove(c);
            click();
            OnixClient.save();
            Theme.refresh();
        };

        if (s instanceof BoolSetting b) {
            Theme.toggle(ctx, x + w - 30, y + (base - 10) / 2, anim(b, b.isOn(), 14f));
            hit(x, y, x + w, y + base, () -> {
                b.toggle();
                click();
                OnixClient.save();
            }, reset);
        } else if (s instanceof NumberSetting num) {
            hit(x, y, x + w, y + base, null, reset);
            if (editingNumber == num) {
                int fw = 54;
                int fx = x + w - 10 - fw;
                Theme.card(ctx, fx, textY - 4, fx + fw, textY + 10, 4, Theme.SURFACE_SUNKEN, Theme.withAlpha(Theme.accent(), 200));
                numberField = new int[]{fx, textY - 4, fx + fw, textY + 10};
                numberInput.render(ctx, fx + 5, textY, fw - 10, Theme.TEXT, Theme.TEXT_3);
                hit(fx, textY - 4, fx + fw, textY + 10, () -> focusOnly(numberInput), null);
            } else {
                String value = num.display();
                int vw = Theme.width(value);
                boolean vh = hovered(mx, my, x + w - 14 - vw, textY - 3, x + w - 10, textY + 10);
                if (vh) Theme.round(ctx, x + w - 15 - vw, textY - 3, x + w - 9, textY + 10, 3, Theme.SURFACE_4);
                Theme.textRight(ctx, value, x + w - 12, textY, vh ? Theme.TEXT : Theme.TEXT_2);
                hit(x + w - 14 - vw, textY - 3, x + w - 10, textY + 10, () -> {
                    commitNumber();
                    editingNumber = num;
                    String plain = num.getStep() >= 1 ? String.valueOf(num.getInt()) : String.valueOf(num.get());
                    numberInput.setText(plain);
                    focusOnly(numberInput);
                }, reset);
                tooltipIf(vh, "Click to type a value · scroll on the slider to fine-tune (Ctrl = bigger steps)");
            }
            int tx1 = x + 12;
            int tx2 = x + w - 12;
            int ty = y + 24;
            float prog = (float) num.progress();
            Theme.round(ctx, tx1, ty, tx2, ty + 3, 1, Theme.SURFACE_4);
            int fillX = tx1 + Math.round((tx2 - tx1) * prog);
            Theme.round(ctx, tx1, ty, Math.max(tx1 + 2, fillX), ty + 3, 1, Theme.accent());
            boolean kh = draggingSlider == num || hovered(mx, my, tx1 - 3, ty - 5, tx2 + 3, ty + 8);
            int k = kh ? 5 : 4;
            Theme.round(ctx, fillX - k, ty + 1 - k, fillX + k, ty + 2 + k, k, Theme.TEXT);
            wheel(tx1 - 5, ty - 7, tx2 + 5, ty + 10, d -> {
                num.nudge(d * (hasControlDown() ? 10 : 1));
                Theme.refresh();
                OnixClient.save();
            });
            hit(tx1 - 5, y + 16, tx2 + 5, y + base, () -> {
                draggingSlider = num;
                sliderX1 = tx1;
                sliderX2 = tx2;
            }, reset);
        } else if (s instanceof ModeSetting mode) {
            hit(x, y, x + w, y + base, () -> {
                mode.cycle(1);
                click();
                OnixClient.save();
            }, reset);
            renderModeControl(ctx, mx, my, mode, x, y, w, base);
        } else if (s instanceof ColorSetting color) {
            renderColorControl(ctx, mx, my, color, x, y, w, base, reset);
        } else if (s instanceof KeybindSetting kb) {
            int bw = keyButtonWidth(kb);
            int bx = x + w - 10 - bw;
            int by = y + (base - 16) / 2;
            boolean active = listening == kb;
            boolean bh = hovered(mx, my, bx, by, bx + bw, by + 16);
            Theme.card(ctx, bx, by, bx + bw, by + 16, 5, active ? Theme.withAlpha(Theme.accent(), 60) : bh ? Theme.SURFACE_4 : Theme.SURFACE_3,
                    active ? Theme.accent() : Theme.BORDER);
            drawIcon(ctx, "keyboard", bx + 6, by + 4, 8, active ? Theme.accent() : Theme.TEXT_3);
            String label = active ? "Press a key…" : kb.display();
            Theme.text(ctx, label, bx + 18, by + 4, active ? Theme.TEXT : kb.isBound() ? Theme.TEXT : Theme.TEXT_3);
            hit(x, y, x + w, y + base, null, reset);
            hit(bx, by, bx + bw, by + 16, () -> {
                listening = kb;
                click();
            }, reset);
            tooltipIf(bh && !active, "Click, then press a key or a middle/side mouse button.\nBackspace clears, Esc cancels.");
        } else if (s instanceof TextSetting text) {
            Input field = textInputs.computeIfAbsent(text, k -> {
                Input in = new Input(k.placeholder().isEmpty() ? "Type here" : k.placeholder(), k.maxLength());
                in.setText(k.get());
                in.onChange(() -> k.set(in.text()));
                in.onSubmit(() -> {
                    in.focus(false);
                    OnixClient.save();
                });
                return in;
            });
            if (!field.focused() && !field.text().equals(text.get())) field.setText(text.get());
            int fw = textFieldWidth(w);
            int fx = x + w - 10 - fw;
            int fy = y + (base - 18) / 2;
            boolean fh = hovered(mx, my, fx, fy, fx + fw, fy + 18);
            Theme.card(ctx, fx, fy, fx + fw, fy + 18, 5, Theme.SURFACE_SUNKEN,
                    field.focused() ? Theme.withAlpha(Theme.accent(), 200) : fh ? Theme.BORDER_STRONG : Theme.BORDER);
            field.render(ctx, fx + 7, fy + 5, fw - 12, Theme.TEXT, Theme.TEXT_3);
            hit(x, y, x + w, y + base, () -> focusOnly(field), reset);
        }
    }

    // ---------- mode: segmented control or dropdown

    private static int segmentedWidth(String[] options) {
        int w = 4;
        for (String o : options) w += Theme.smallWidth(o) + 14;
        return w;
    }

    private static int dropdownWidth(ModeSetting mode) {
        return Math.min(150, Theme.width(mode.get()) + 26);
    }

    private void renderModeControl(DrawContext ctx, int mx, int my, ModeSetting mode, int x, int y, int w, int base) {
        String[] options = mode.modes();
        int segW = segmentedWidth(options);
        if (options.length <= 3 && segW < w * 0.45f) {
            int sx = x + w - 10 - segW;
            int sy = y + (base - 16) / 2;
            Theme.card(ctx, sx, sy, sx + segW, sy + 16, 5, Theme.SURFACE_SUNKEN, Theme.BORDER);
            wheel(sx, sy, sx + segW, sy + 16, d -> {
                mode.cycle(d > 0 ? -1 : 1);
                OnixClient.save();
            });
            int ox = sx + 2;
            for (String opt : options) {
                int ow = Theme.smallWidth(opt) + 14;
                boolean sel = mode.is(opt);
                boolean oh = hovered(mx, my, ox, sy + 2, ox + ow, sy + 14);
                if (sel) Theme.round(ctx, ox, sy + 2, ox + ow, sy + 14, 4, Theme.SURFACE_4);
                Theme.small(ctx, opt, ox + 7, sy + 6, sel ? Theme.TEXT : oh ? Theme.TEXT_2 : Theme.TEXT_3);
                hit(ox, sy + 2, ox + ow, sy + 14, () -> {
                    mode.set(opt);
                    click();
                    OnixClient.save();
                }, null);
                ox += ow;
            }
            return;
        }
        int dw = dropdownWidth(mode);
        int dx = x + w - 10 - dw;
        int dy = y + (base - 16) / 2;
        boolean open = dropdown == mode;
        boolean dh = hovered(mx, my, dx, dy, dx + dw, dy + 16);
        Theme.card(ctx, dx, dy, dx + dw, dy + 16, 5, open || dh ? Theme.SURFACE_4 : Theme.SURFACE_3, open ? Theme.BORDER_STRONG : Theme.BORDER);
        if (!open) {
            wheel(dx, dy, dx + dw, dy + 16, d -> {
                mode.cycle(d > 0 ? -1 : 1);
                OnixClient.save();
            });
        }
        tooltipIf(dh && !open, "Click to choose · scroll to step through");
        Theme.text(ctx, Theme.ellipsize(mode.get(), dw - 24), dx + 8, dy + 4, Theme.TEXT);
        drawIcon(ctx, "chevron", dx + dw - 13, dy + 4, 8, Theme.TEXT_3);
        hit(dx, dy, dx + dw, dy + 16, () -> {
            dropdown = open ? null : mode;
            dropdownOffset = -1;
            click();
        }, null);
        if (open) {
            int menuW = dw;
            for (String o : options) menuW = Math.max(menuW, Theme.width(o) + 30);
            dropdownAnchor = new int[]{dx + dw - menuW, dy + 18, menuW, dy - 2};
            dropdownSeen = true;
        }
    }

    private void renderDropdown(DrawContext ctx, int mx, int my) {
        if (dropdown != null && (!dropdownSeen || dropdownAnchor == null)) dropdown = null;
        if (dropdown == null) {
            dropdownRect = null;
            return;
        }
        String[] options = dropdown.modes();
        int itemH = 16;
        int visible = Math.min(options.length, 12);
        int h = visible * itemH + 6;
        int x = dropdownAnchor[0];
        int w = dropdownAnchor[2];
        int y = dropdownAnchor[1];
        if (y + h > vh - 6) y = Math.max(6, dropdownAnchor[3] - h);
        ModeSetting mode = dropdown;
        dropdownRect = new int[]{x, y, x + w, y + h};
        hit(0, 0, vw, vh, () -> dropdown = null, () -> dropdown = null);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 300);
        Theme.shadow(ctx, x, y, x + w, y + h, 7);
        Theme.card(ctx, x, y, x + w, y + h, 7, Theme.SURFACE_2, Theme.BORDER_STRONG);
        if (dropdownOffset < 0) dropdownOffset = mode.index() - visible / 2;
        dropdownOffset = Math.max(0, Math.min(dropdownOffset, options.length - visible));
        int start = dropdownOffset;
        if (options.length > visible) {
            // scroll position
            int trackH = h - 8;
            int thumbH = Math.max(10, trackH * visible / options.length);
            int thumbY = y + 4 + (trackH - thumbH) * start / Math.max(1, options.length - visible);
            Theme.round(ctx, x + w - 4, thumbY, x + w - 2, thumbY + thumbH, 1, Theme.withAlpha(Theme.TEXT, 60));
        }
        for (int i = 0; i < visible; i++) {
            String opt = options[start + i];
            int iy = y + 3 + i * itemH;
            boolean sel = mode.is(opt);
            boolean hov = Theme.inside(mx, my, x + 3, iy, x + w - 3, iy + itemH);
            if (hov) Theme.round(ctx, x + 3, iy, x + w - 3, iy + itemH, 4, Theme.SURFACE_4);
            if (sel) drawIcon(ctx, "check", x + 8, iy + 4, 8, Theme.accent());
            Theme.text(ctx, opt, x + 21, iy + 4, sel ? Theme.TEXT : Theme.TEXT_2);
            hit(x + 3, iy, x + w - 3, iy + itemH, () -> {
                mode.set(opt);
                dropdown = null;
                click();
                OnixClient.save();
            }, null);
        }
        ctx.getMatrices().pop();
    }

    // ---------- color picker

    private float[] hsv(ColorSetting c) {
        return hsvCache.computeIfAbsent(c, k -> ColorUtil.rgbToHsv(k.get()));
    }

    private void renderColorControl(DrawContext ctx, int mx, int my, ColorSetting color, int x, int y, int w, int base, Runnable reset) {
        boolean expanded = expandedColor == color;
        int sw = 22;
        int sx = x + w - 12 - sw - 12;
        int sy = y + (base - 12) / 2;
        String state = color.isChroma() ? "Chroma" : color.isAccent() ? "Accent" : color.hex().substring(3);
        Theme.small(ctx, state, sx - 6 - Theme.smallWidth(state), sy + 3, Theme.TEXT_3);
        checker(ctx, sx, sy, sx + sw, sy + 12);
        Theme.round(ctx, sx, sy, sx + sw, sy + 12, 3, color.color());
        Theme.outline(ctx, sx, sy, sx + sw, sy + 12, Theme.withAlpha(0xFFFFFFFF, 40));
        drawIcon(ctx, "chevron", x + w - 19, sy + 2, 8, expanded ? Theme.TEXT : Theme.TEXT_3);
        hit(x, y, x + w, y + base, () -> {
            expandedColor = expanded ? null : color;
            if (!expanded) hsvCache.put(color, ColorUtil.rgbToHsv(color.get()));
            click();
        }, reset);
        if (expanded) renderColorPicker(ctx, mx, my, color, x, y + base, w);
    }

    private void renderColorPicker(DrawContext ctx, int mx, int my, ColorSetting color, int x, int y, int w) {
        float[] hsv = hsv(color);
        int alpha = color.alpha();
        ctx.fill(x + 12, y, x + w - 12, y + 1, Theme.BORDER);
        int py = y + 8;

        // saturation / value square
        int ax1 = x + 12, ay1 = py, ax2 = x + 140, ay2 = py + 60;
        int cols = 64;
        for (int i = 0; i < cols; i++) {
            int c1 = ax1 + (ax2 - ax1) * i / cols;
            int c2 = ax1 + (ax2 - ax1) * (i + 1) / cols;
            ctx.fillGradient(c1, ay1, c2, ay2, ColorUtil.hsvToRgb(hsv[0], i / (float) (cols - 1), 1f), 0xFF000000);
        }
        Theme.outline(ctx, ax1, ay1, ax2, ay2, Theme.withAlpha(0xFFFFFFFF, 30));
        int kx = ax1 + Math.round(hsv[1] * (ax2 - ax1));
        int ky = ay1 + Math.round((1 - hsv[2]) * (ay2 - ay1));
        Theme.round(ctx, kx - 4, ky - 4, kx + 4, ky + 4, 4, 0xFFFFFFFF);
        Theme.round(ctx, kx - 3, ky - 3, kx + 3, ky + 3, 3, 0xFF000000 | ColorUtil.hsvToRgb(hsv[0], hsv[1], hsv[2]));
        int[] svRect = {ax1, ay1, ax2, ay2};
        hit(ax1 - 2, ay1 - 2, ax2 + 2, ay2 + 2, () -> startColorDrag(color, 1, svRect), null);

        // hue bar
        int hy1 = ay2 + 7, hy2 = hy1 + 8;
        for (int i = 0; i < cols; i++) {
            int c1 = ax1 + (ax2 - ax1) * i / cols;
            int c2 = ax1 + (ax2 - ax1) * (i + 1) / cols;
            ctx.fill(c1, hy1, c2, hy2, ColorUtil.hsvToRgb(i / (float) cols, 1f, 1f));
        }
        int hx = ax1 + Math.round(hsv[0] * (ax2 - ax1));
        Theme.round(ctx, hx - 2, hy1 - 2, hx + 2, hy2 + 2, 1, 0xFFFFFFFF);
        int[] hueRect = {ax1, hy1, ax2, hy2};
        hit(ax1 - 2, hy1 - 3, ax2 + 2, hy2 + 3, () -> startColorDrag(color, 2, hueRect), null);

        // alpha bar
        int ly1 = hy2 + 6, ly2 = ly1 + 8;
        checker(ctx, ax1, ly1, ax2, ly2);
        int rgb = color.color() & 0x00FFFFFF;
        for (int i = 0; i < cols; i++) {
            int c1 = ax1 + (ax2 - ax1) * i / cols;
            int c2 = ax1 + (ax2 - ax1) * (i + 1) / cols;
            ctx.fill(c1, ly1, c2, ly2, Theme.withAlpha(rgb, Math.round(255f * i / (cols - 1))));
        }
        int lx = ax1 + Math.round(alpha / 255f * (ax2 - ax1));
        Theme.round(ctx, lx - 2, ly1 - 2, lx + 2, ly2 + 2, 1, 0xFFFFFFFF);
        int[] alphaRect = {ax1, ly1, ax2, ly2};
        hit(ax1 - 2, ly1 - 3, ax2 + 2, ly2 + 3, () -> startColorDrag(color, 3, alphaRect), null);

        // presets
        int px = x + 156;
        int size = 11;
        int gap = 4;
        int perRow = Math.max(4, Math.min(8, (x + w - 12 - px + gap) / (size + gap)));
        for (int i = 0; i < ColorSetting.PRESETS.length; i++) {
            int preset = ColorSetting.PRESETS[i];
            int bx = px + (i % perRow) * (size + gap);
            int by = py + (i / perRow) * (size + gap);
            if (by + size > py + 34) break;
            boolean sel = !color.isAccent() && !color.isChroma() && (color.get() & 0x00FFFFFF) == (preset & 0x00FFFFFF);
            boolean hov = hovered(mx, my, bx, by, bx + size, by + size);
            if (sel || hov) Theme.round(ctx, bx - 2, by - 2, bx + size + 2, by + size + 2, 4, sel ? Theme.TEXT : Theme.BORDER_STRONG);
            Theme.round(ctx, bx, by, bx + size, by + size, 3, preset);
            hit(bx - 1, by - 1, bx + size + 1, by + size + 1, () -> {
                color.pick((alpha << 24) | (preset & 0x00FFFFFF));
                hsvCache.put(color, ColorUtil.rgbToHsv(preset));
                click();
                OnixClient.save();
            }, null);
        }

        // accent / chroma switches
        int ty = py + 40;
        int cx = px;
        if (!color.isPlain()) {
            cx += chip(ctx, mx, my, cx, ty, "Theme Accent", color.isAccent(), () -> {
                color.setAccent(!color.isAccent());
                OnixClient.save();
            }) + 5;
            chip(ctx, mx, my, cx, ty, "Chroma", color.isChroma(), () -> {
                color.setChroma(!color.isChroma());
                OnixClient.save();
            });
        }

        // value readout
        String readout = color.hex() + "  ·  " + Math.round(alpha / 2.55f) + "% opacity";
        Theme.small(ctx, readout, px, ty + 24, Theme.TEXT_3);
        String copy = "Copy";
        int cpx = px + Theme.smallWidth(readout) + 8;
        boolean ch = hovered(mx, my, cpx - 2, ty + 21, cpx + Theme.smallWidth(copy) + 2, ty + 32);
        Theme.small(ctx, copy, cpx, ty + 24, ch ? Theme.TEXT : Theme.accent());
        hit(cpx - 2, ty + 21, cpx + Theme.smallWidth(copy) + 2, ty + 32, () -> {
            client.keyboard.setClipboard(color.hex());
            Notifications.show("Copied", color.hex());
        }, null);
    }

    /** Small on/off pill; returns its width. */
    private int chip(DrawContext ctx, int mx, int my, int x, int y, String label, boolean on, Runnable action) {
        int w = Theme.smallWidth(label) + 18;
        boolean hov = hovered(mx, my, x, y, x + w, y + 15);
        Theme.card(ctx, x, y, x + w, y + 15, Theme.corner(7), on ? Theme.withAlpha(Theme.accent(), 70) : hov ? Theme.SURFACE_4 : Theme.SURFACE_3,
                on ? Theme.accent() : Theme.BORDER);
        Theme.round(ctx, x + 6, y + 6, x + 9, y + 9, 1, on ? Theme.accent() : Theme.TEXT_3);
        Theme.small(ctx, label, x + 13, y + 5, on ? Theme.TEXT : Theme.TEXT_2);
        hit(x, y, x + w, y + 15, () -> {
            action.run();
            click();
        }, null);
        return w;
    }

    private static void checker(DrawContext ctx, int x1, int y1, int x2, int y2) {
        int cell = 4;
        for (int y = y1; y < y2; y += cell) {
            for (int x = x1; x < x2; x += cell) {
                boolean dark = ((x - x1) / cell + (y - y1) / cell) % 2 == 0;
                ctx.fill(x, y, Math.min(x2, x + cell), Math.min(y2, y + cell), dark ? 0xFF2A2A30 : 0xFF45454D);
            }
        }
    }

    private void startColorDrag(ColorSetting color, int mode, int[] rect) {
        expandedColor = color;
        colorDrag = mode;
        colorDragRect = rect;
    }

    private void updateColorDrag(double mouseX, double mouseY) {
        ColorSetting c = expandedColor;
        if (c == null || colorDrag == 0 || colorDragRect == null) return;
        int[] r = colorDragRect;
        float fx = (float) Math.max(0, Math.min(1, (mouseX - r[0]) / (double) (r[2] - r[0])));
        float fy = (float) Math.max(0, Math.min(1, (mouseY - r[1]) / (double) (r[3] - r[1])));
        float[] hsv = hsv(c);
        int alpha = c.alpha();
        switch (colorDrag) {
            case 1 -> {
                hsv[1] = fx;
                hsv[2] = 1 - fy;
                c.pick((alpha << 24) | (ColorUtil.hsvToRgb(hsv[0], hsv[1], hsv[2]) & 0x00FFFFFF));
            }
            case 2 -> {
                hsv[0] = Math.min(0.999f, fx);
                c.pick((alpha << 24) | (ColorUtil.hsvToRgb(hsv[0], hsv[1], hsv[2]) & 0x00FFFFFF));
            }
            case 3 -> c.set((Math.round(fx * 255) << 24) | (c.get() & 0x00FFFFFF));
            default -> {}
        }
    }

    // ---------- keybind and text helpers

    private static int keyButtonWidth(KeybindSetting kb) {
        return Math.max(64, Math.max(Theme.width(kb.display()), Theme.width("Press a key…")) + 26);
    }

    private static int textFieldWidth(int rowWidth) {
        return Math.min(170, Math.max(90, rowWidth * 2 / 5));
    }

    private void focusOnly(Input input) {
        search.focus(false);
        profileName.focus(false);
        cosSearch.focus(false);
        giftTo.focus(false);
        giftMessage.focus(false);
        if (input != numberInput) numberInput.focus(false);
        unfocusTextInputs();
        input.focus(true);
    }

    private void unfocusTextInputs() {
        for (Input in : textInputs.values()) in.focus(false);
    }

    private boolean anyInputFocused() {
        if (search.focused() || profileName.focused() || numberInput.focused() || cosSearch.focused() || giftTo.focused() || giftMessage.focused()) return true;
        for (Input in : textInputs.values()) if (in.focused()) return true;
        return false;
    }

    private int linkRow(DrawContext ctx, int mx, int my, int x, int y, int w, String label, String value, String iconName, int labelColor, Runnable action) {
        int h = 28;
        boolean hov = hovered(mx, my, x, y, x + w, y + h);
        Theme.card(ctx, x, y, x + w, y + h, 8, hov ? Theme.SURFACE_3 : Theme.SURFACE_2, Theme.BORDER);
        int tx = x + 12;
        if (iconName != null) {
            drawIcon(ctx, iconName, tx, y + 9, 10, labelColor == Theme.TEXT ? Theme.TEXT_2 : labelColor);
            tx += 18;
        }
        Theme.text(ctx, label, tx, y + 10, labelColor);
        drawIcon(ctx, "chevron", x + w - 20, y + 10, 8, hov ? Theme.TEXT : Theme.TEXT_3);
        if (value != null) Theme.textRight(ctx, value, x + w - 26, y + 10, Theme.TEXT_2);
        hit(x, y, x + w, y + h, () -> {
            click();
            action.run();
        }, null);
        return h;
    }

    // ---------- client settings

    private void renderSettingsPage(DrawContext ctx, int mx, int my, int[] r) {
        var c = OnixClient.CONFIG;
        int x = r[0];
        int w = r[2] - r[0];
        List<Section> sections = List.of(
                new Section("Interface", c.interfaceSettings()),
                new Section("HUD", c.hudSettings()),
                new Section("Notifications", c.notificationSettings()),
                new Section("Profiles", c.profileSettings()),
                new Section("Server Rules", c.serverRuleSettings()),
                new Section("Onix Server", c.networkSettings()));
        contentHeight = sectionsHeight(sections) + 2 * (28 + 6) + 28 + 6 + 8;
        clampScroll(r[3] - r[1]);
        beginClip(ctx, x - 4, r[1] - 2, r[2] + 6, r[3]);
        int y = r[1] - (int) Math.round(scroll);

        y += renderSection(ctx, mx, my, sections.get(0), x, y, w);
        y += renderSection(ctx, mx, my, sections.get(1), x, y, w) - 6;
        y += linkRow(ctx, mx, my, x, y, w, "Edit HUD layout", null, "move", Theme.TEXT, () -> client.setScreen(new HudEditorScreen(this))) + 12;
        y += renderSection(ctx, mx, my, sections.get(2), x, y, w);
        y += renderSection(ctx, mx, my, sections.get(4), x, y, w);
        y += renderSection(ctx, mx, my, sections.get(5), x, y, w);
        y += renderSection(ctx, mx, my, sections.get(3), x, y, w) - 6;
        y += linkRow(ctx, mx, my, x, y, w, "Manage profiles", OnixClient.PROFILES.active(), "profile", Theme.TEXT,
                () -> navigate(View.PROFILES, null, false, null)) + 6;
        boolean confirming = System.currentTimeMillis() < confirmResetUntil;
        linkRow(ctx, mx, my, x, y, w, confirming ? "Click again to reset every module in this profile" : "Reset active profile to defaults",
                null, "reset", confirming ? Theme.RED : Theme.TEXT, () -> {
                    if (System.currentTimeMillis() < confirmResetUntil) {
                        OnixClient.PROFILES.resetActive();
                        hsvCache.clear();
                        confirmResetUntil = 0;
                        Notifications.show("Profile reset", OnixClient.PROFILES.active());
                    } else {
                        confirmResetUntil = System.currentTimeMillis() + 3000;
                    }
                });
        endClip(ctx);
        scrollbar(ctx, r[2] + 4, r[1], r[3]);
    }

    // ---------- themes

    private void renderDesign(DrawContext ctx, int mx, int my, int[] r) {
        var c = OnixClient.CONFIG;
        int w = r[2] - r[0];
        clampScroll(r[3] - r[1]);
        beginClip(ctx, r[0] - 4, r[1] - 2, r[2] + 6, r[3]);
        int y0 = r[1] - (int) Math.round(scroll);
        int y = y0;

        // themes
        ThemeDef selected = ThemeStore.byId(c.theme.get());
        Theme.label(ctx, "Themes", r[0] + 2, y, Theme.TEXT_3);
        y += 10 + renderThemeGrid(ctx, mx, my, r[0], y + 10, w, selected) + 12;
        themeEditor.edit(selected);
        if (selected.custom() && themeEditor.theme() != null) {
            Theme.label(ctx, "Edit " + themeEditor.theme().name(), r[0] + 2, y, Theme.TEXT_3);
            y += 10 + renderGroup(ctx, mx, my, themeEditor.settings(), r[0], y + 10, w) + 6;
            int bx = r[0];
            bx += button(ctx, mx, my, bx, y, 92, 20, "Duplicate", false, () -> selectTheme(ThemeStore.create(themeEditor.theme(), themeEditor.theme().name()))) + 6;
            boolean confirming = System.currentTimeMillis() < confirmDeleteUntil;
            button(ctx, mx, my, bx, y, 112, 20, confirming ? "Click to Confirm" : "Delete Theme", false, () -> {
                if (System.currentTimeMillis() < confirmDeleteUntil) {
                    ThemeStore.delete(selected.id());
                    selectTheme(ThemeDef.BUILT_IN[0]);
                    confirmDeleteUntil = 0;
                } else {
                    confirmDeleteUntil = System.currentTimeMillis() + 3000;
                }
            });
            y += 32;
        } else {
            y += linkRow(ctx, mx, my, r[0], y, w, "Customize " + selected.name(), "Make an editable copy", "plus", Theme.TEXT,
                    () -> selectTheme(ThemeStore.create(selected, selected.name() + " Copy"))) + 12;
        }

        // title screen background
        Theme.label(ctx, "Title Screen Background", r[0] + 2, y, Theme.TEXT_3);
        y += 10 + renderBackgroundGrid(ctx, mx, my, r[0], y + 10, w) + 6;
        y += renderGroup(ctx, mx, my, c.backgroundSettings(), r[0], y, w) + 12;

        for (Section section : List.of(new Section("Appearance", c.designSettings()), new Section("Loading Screens", c.loadingSettings()))) {
            y += renderSection(ctx, mx, my, section, r[0], y, w);
        }
        contentHeight = y - y0;
        endClip(ctx);
        scrollbar(ctx, r[2] + 4, r[1], r[3]);
        themeEditor.tick();
    }

    private void selectTheme(ThemeDef def) {
        OnixClient.CONFIG.theme.set(def.id());
        OnixClient.CONFIG.save();
        click();
        Theme.refresh();
    }

    /** Theme tiles with a miniature window in the theme's colors; returns the grid height. */
    private int renderThemeGrid(DrawContext ctx, int mx, int my, int x0, int y0, int w, ThemeDef selected) {
        List<ThemeDef> themes = ThemeStore.all();
        int cols = 4;
        int gap = 8;
        int cw = (w - gap * (cols - 1)) / cols;
        int ch = 62;
        int count = themes.size() + 1;
        for (int i = 0; i < count; i++) {
            int x = x0 + (i % cols) * (cw + gap);
            int y = y0 + (i / cols) * (ch + gap);
            boolean hov = hovered(mx, my, x, y, x + cw, y + ch);
            float ht = anim("themeTile:" + i, hov, 16f);
            if (i == themes.size()) {
                // "New Theme" tile
                Theme.card(ctx, x, y, x + cw, y + ch, 8, Theme.lerpColor(Theme.SURFACE_SUNKEN, Theme.SURFACE_2, ht),
                        Theme.lerpColor(Theme.BORDER, Theme.BORDER_STRONG, ht));
                drawIcon(ctx, "plus", x + cw / 2 - 6, y + 16, 12, Theme.lerpColor(Theme.TEXT_3, Theme.TEXT, ht));
                String label = "New Theme";
                Theme.text(ctx, label, x + cw / 2 - Theme.width(label) / 2, y + 40, Theme.lerpColor(Theme.TEXT_2, Theme.TEXT, ht));
                hit(x, y, x + cw, y + ch, () -> selectTheme(ThemeStore.create(Theme.current(), "My Theme")), null);
                continue;
            }
            ThemeDef def = themes.get(i);
            ThemeDef live = ThemeEditor.livePreview(def.id());
            if (live != null) def = live;
            boolean isSelected = def.id().equals(selected.id());
            Theme.card(ctx, x, y, x + cw, y + ch, 8, Theme.lerpColor(Theme.SURFACE_2, Theme.SURFACE_3, ht),
                    isSelected ? Theme.withAlpha(def.accent(), 220) : Theme.lerpColor(Theme.BORDER, Theme.BORDER_STRONG, ht));
            int px1 = x + 6, py1 = y + 6, px2 = x + cw - 6, py2 = y + 38;
            Theme.card(ctx, px1, py1, px2, py2, 5, def.panel(), Theme.withAlpha(Theme.lerpColor(def.panel(), 0xFFFFFFFF, 0.12f), 0xFF));
            ctx.fill(px1 + 1, py1 + 1, px1 + 18, py2 - 1, Theme.lerpColor(def.panel(), 0xFF000000, 0.3f));
            Theme.round(ctx, px1 + 4, py1 + 5, px1 + 14, py1 + 8, 1, def.accent());
            Theme.round(ctx, px1 + 4, py1 + 11, px1 + 12, py1 + 13, 1, Theme.withAlpha(0xFFFFFFFF, 60));
            Theme.round(ctx, px1 + 22, py1 + 5, px2 - 8, py1 + 14, 2, Theme.lerpColor(def.panel(), 0xFFFFFFFF, 0.07f));
            Theme.round(ctx, px1 + 22, py1 + 17, px2 - 18, py1 + 26, 2, Theme.lerpColor(def.panel(), 0xFFFFFFFF, 0.07f));
            Theme.round(ctx, px2 - 16, py1 + 19, px2 - 8, py1 + 24, 2, def.accent());
            Theme.text(ctx, Theme.ellipsize(def.name(), cw - 26), x + 9, y + 46, Theme.TEXT);
            if (isSelected) drawIcon(ctx, "check", x + cw - 17, y + 45, 9, def.accent());
            else if (def.custom()) Theme.round(ctx, x + cw - 13, y + 49, x + cw - 9, y + 53, 2, Theme.TEXT_3);
            ThemeDef pick = def;
            hit(x, y, x + cw, y + ch, () -> selectTheme(pick), null);
            tooltipIf(hov && def.custom(), "Custom theme");
        }
        int rows = (count + cols - 1) / cols;
        return rows * (ch + gap) - gap;
    }

    /** Background thumbnails plus a tile that opens the folder for your own images; returns the height. */
    private int renderBackgroundGrid(DrawContext ctx, int mx, int my, int x0, int y0, int w) {
        var c = OnixClient.CONFIG;
        List<TitleBackgrounds.Background> list = TitleBackgrounds.all();
        int cols = 3;
        int gap = 8;
        int tw = (w - gap * (cols - 1)) / cols;
        int th = Math.round(tw * 9 / 16f);
        int count = list.size() + 1;
        for (int i = 0; i < count; i++) {
            int x = x0 + (i % cols) * (tw + gap);
            int y = y0 + (i / cols) * (th + 18 + gap);
            boolean hov = hovered(mx, my, x, y, x + tw, y + th + 14);
            if (i == list.size()) {
                Theme.card(ctx, x, y, x + tw, y + th, 6, hov ? Theme.SURFACE_2 : Theme.SURFACE_SUNKEN, hov ? Theme.BORDER_STRONG : Theme.BORDER);
                drawIcon(ctx, "plus", x + tw / 2 - 6, y + th / 2 - 10, 12, hov ? Theme.TEXT : Theme.TEXT_3);
                Theme.small(ctx, "Add PNG images", x + tw / 2 - Theme.smallWidth("Add PNG images") / 2, y + th / 2 + 8, Theme.TEXT_3);
                Theme.text(ctx, "Your Images", x + 2, y + th + 5, hov ? Theme.TEXT : Theme.TEXT_2);
                hit(x, y, x + tw, y + th + 14, TitleBackgrounds::openFolder, null);
                tooltipIf(hov, "Opens config/onix/backgrounds – drop PNG files there and they appear here.");
                continue;
            }
            TitleBackgrounds.Background b = list.get(i);
            boolean selected = !c.backgroundSlideshow.isOn() && b.id().equals(c.titleBackground.get());
            ctx.enableScissor(sx(x), sy(y), sx(x + tw), sy(y + th));
            TitleBackgrounds.drawThumbnail(ctx, b, x, y, tw, th);
            ctx.disableScissor();
            int border = selected ? Theme.accent() : hov ? Theme.withAlpha(0xFFFFFFFF, 90) : Theme.withAlpha(0xFFFFFFFF, 26);
            Theme.outline(ctx, x, y, x + tw, y + th, border);
            if (selected) Theme.outline(ctx, x + 1, y + 1, x + tw - 1, y + th - 1, border);
            Theme.text(ctx, Theme.ellipsize(b.name(), tw - 14), x + 2, y + th + 5, selected ? Theme.TEXT : Theme.TEXT_2);
            if (selected) drawIcon(ctx, "check", x + tw - 10, y + th + 5, 8, Theme.accent());
            hit(x, y, x + tw, y + th + 14, () -> {
                c.titleBackground.set(b.id());
                c.backgroundSlideshow.set(false);
                c.save();
                click();
            }, null);
        }
        int rows = (count + cols - 1) / cols;
        return rows * (th + 18 + gap) - gap;
    }

    // ---------- cosmetics

    private static final Cosmetics.Slot[] COS_SLOTS = Cosmetics.Slot.values();

    private void renderCosmetics(DrawContext ctx, int mx, int my, int[] r, float dt) {
        int pv2 = r[0] + 116;
        Theme.card(ctx, r[0], r[1], pv2, r[3], 8, Theme.SURFACE_SUNKEN, Theme.BORDER);
        renderWallet(ctx, mx, my, r[0] + 6, r[1] + 6, pv2 - 6);
        if (PlayerPreview.available()) {
            if (!draggingPreview) previewRotation += dt * 22f;
            int size = Math.min(48, (r[3] - r[1] - 80) / 2);
            ctx.enableScissor(sx(r[0] + 1), sy(r[1] + 32), sx(pv2 - 1), sy(r[3] - 1));
            PlayerPreview.draw(ctx, (r[0] + pv2) / 2f, (r[1] + 30 + r[3] - 36) / 2f, size, previewRotation);
            ctx.disableScissor();
            String hint = "Drag to rotate";
            Theme.small(ctx, hint, (r[0] + pv2) / 2 - Theme.smallWidth(hint) / 2, r[3] - 31, Theme.TEXT_3);
            hit(r[0], r[1] + 32, pv2, r[3] - 24, () -> draggingPreview = true, null);
        } else {
            int cx = (r[0] + pv2) / 2;
            int cy = (r[1] + r[3]) / 2 - 12;
            drawIcon(ctx, "profile", cx - 9, cy - 22, 18, Theme.TEXT_3);
            Theme.textCentered(ctx, "Preview", cx, cy + 2, Theme.TEXT_2);
            Theme.textCentered(ctx, "available in a world", cx, cy + 13, Theme.TEXT_3);
        }
        // equipped count + clear all
        int equipped = Cosmetics.equippedCount();
        if (equipped > 0) {
            button(ctx, mx, my, r[0] + 8, r[3] - 22, pv2 - r[0] - 16, 15, "Remove all (" + equipped + ")", false, () -> {
                for (Cosmetics.Slot s : COS_SLOTS) Cosmetics.select(s, null);
            });
        } else {
            Theme.small(ctx, "Nothing equipped", (r[0] + pv2) / 2 - Theme.smallWidth("Nothing equipped") / 2, r[3] - 16, Theme.TEXT_3);
        }

        int x = pv2 + 14;
        int right = r[2];
        int y = r[1];

        // slot tabs
        int cx = x;
        for (Cosmetics.Slot slot : COS_SLOTS) {
            boolean on = slot == cosSlot;
            boolean worn = Cosmetics.selected(slot) != null;
            if (cx > x && cx + Theme.smallWidth(slot.displayName) + 18 > right) {
                cx = x;
                y += 19;
            }
            int w = chip(ctx, mx, my, cx, y, slot.displayName, on, () -> {
                cosSlot = slot;
                cosPalette = null;
                scroll = scrollTarget = 0;
            });
            if (worn) Theme.round(ctx, cx + w - 5, y + 1, cx + w - 1, y + 5, 2, Theme.accent());
            cx += w + 4;
        }
        y += 21;

        // search, owned, random, none
        int bw = 46;
        int sw = Math.max(80, right - x - 3 * (bw + 6));
        Theme.card(ctx, x, y, x + sw, y + 17, 8, Theme.SURFACE, cosSearch.focused() ? Theme.withAlpha(Theme.accent(), 200) : Theme.BORDER_STRONG);
        drawIcon(ctx, "search", x + 7, y + 5, 8, Theme.TEXT_3);
        cosSearch.render(ctx, x + 19, y + 5, sw - 26, Theme.TEXT, Theme.TEXT_3);
        hit(x, y, x + sw, y + 17, () -> focusOnly(cosSearch), () -> cosSearch.setText(""));
        List<dev.onix.client.cosmetics.CosmeticCatalog.Item> items = cosmeticList();
        button(ctx, mx, my, x + sw + 6, y, bw, 17, "Owned", cosOwnedOnly, () -> {
            cosOwnedOnly = !cosOwnedOnly;
            scroll = scrollTarget = 0;
        });
        button(ctx, mx, my, x + sw + 12 + bw, y, bw, 17, "Random", false, () -> {
            List<dev.onix.client.cosmetics.CosmeticCatalog.Item> mine = new ArrayList<>();
            for (var it : items) if (dev.onix.client.network.OnixWallet.owns(it)) mine.add(it);
            if (mine.isEmpty()) Notifications.show("Nothing to pick from", "You do not own any of these yet");
            else Cosmetics.select(cosSlot, mine.get((int) (Math.random() * mine.size())).id());
        });
        boolean none = Cosmetics.selected(cosSlot) == null;
        button(ctx, mx, my, x + sw + 18 + 2 * bw, y, bw, 17, "None", none, () -> Cosmetics.select(cosSlot, null));
        y += 23;

        // palette filter
        List<String> palettes = dev.onix.client.cosmetics.CosmeticCatalog.paletteIds();
        int px = x;
        int sq = 8;
        for (String pid : palettes) {
            if (px + sq > right) {
                px = x;
                y += sq + 2;
            }
            int[] colors = dev.onix.client.cosmetics.CosmeticCatalog.palette(pid);
            boolean on = pid.equals(cosPalette);
            boolean hov = hovered(mx, my, px - 1, y - 1, px + sq + 1, y + sq + 1);
            if (on || hov) Theme.round(ctx, px - 2, y - 2, px + sq + 2, y + sq + 2, 3, on ? Theme.accent() : Theme.BORDER_STRONG);
            Theme.round(ctx, px, y, px + sq, y + sq, 2, colors[0]);
            ctx.fill(px + sq / 2, y + 2, px + sq - 2, y + sq - 2, colors[2]);
            tooltipIf(hov, dev.onix.client.cosmetics.CosmeticCatalog.paletteName(pid) + (on ? "\nClick to show all colours" : ""));
            hit(px - 1, y - 1, px + sq + 1, y + sq + 1, () -> {
                cosPalette = pid.equals(cosPalette) ? null : pid;
                scroll = scrollTarget = 0;
                click();
            }, null);
            px += sq + 2;
        }
        y += sq + 7;

        // grid
        boolean cape = cosSlot == Cosmetics.Slot.CAPE;
        int tw = cape ? 34 : 38;
        int th = cape ? 46 : 38;
        int gap = 5;
        int top = y;
        int bottom = r[3] - 12;
        int cols = Math.max(1, (right - x + gap) / (tw + gap));
        int rows = (items.size() + cols - 1) / cols;
        contentHeight = Math.max(0, rows * (th + gap) - gap);
        clampScroll(bottom - top);
        float time = client.player != null ? client.player.age + client.getRenderTickCounter().getTickDelta(true) : (System.currentTimeMillis() % 1_000_000L) / 50f;
        dev.onix.client.cosmetics.CosmeticCatalog.Item hover = null;
        beginClip(ctx, x - 3, top - 2, right + 3, bottom);
        int first = Math.max(0, (int) (scroll / (th + gap)) * cols);
        int last = Math.min(items.size(), first + (((bottom - top) / (th + gap)) + 2) * cols);
        for (int i = first; i < last; i++) {
            var item = items.get(i);
            int gx = x + (i % cols) * (tw + gap);
            int gy = top + (i / cols) * (th + gap) - (int) Math.round(scroll);
            boolean sel = Cosmetics.isSelected(item);
            boolean hov = tile(ctx, mx, my, gx, gy, tw, th, sel, "cos:" + item.id());
            if (cape) {
                dev.onix.client.cosmetics.CapeRenderer.drawIcon(ctx, item, gx + 2, gy + 3, tw - 4, th - 6, hov ? time : 0f, hov ? (float) Math.sin(time * 0.06f) * 35f : 0f);
            } else {
                dev.onix.client.cosmetics.CosmeticModels.drawIcon(ctx, item, gx + 3, gy + 3, tw - 6, th - 6, hov ? time : 0f, hov ? (time * 3f) % 360f : 0f);
            }
            Theme.round(ctx, gx + tw - 6, gy + 3, gx + tw - 3, gy + 6, 1, tierColor(item.tier()));
            boolean mine = dev.onix.client.network.OnixWallet.owns(item);
            if (sel && mine) drawIcon(ctx, "check", gx + 3, gy + 3, 7, Theme.accent());
            if (!mine) {
                // price tag
                String price = String.valueOf(item.price());
                int pw = Theme.smallWidth(price) + 9;
                ctx.getMatrices().push();
                ctx.getMatrices().translate(0, 0, 260);
                Theme.round(ctx, gx + 2, gy + th - 10, gx + 2 + pw, gy + th - 2, 3, Theme.withAlpha(0xFF101014, 210));
                Theme.round(ctx, gx + 4, gy + th - 8, gx + 8, gy + th - 4, 2, 0xFFFFC23D);
                Theme.small(ctx, price, gx + 10, gy + th - 8, 0xFFFFE7A8);
                ctx.getMatrices().pop();
            }
            if (hov) hover = item;
            hit(gx, gy, gx + tw, gy + th, () -> {
                click();
                if (dev.onix.client.network.OnixWallet.owns(item)) Cosmetics.select(item.slot(), Cosmetics.isSelected(item) ? null : item.id());
                else openCosDialog(item, false);
            }, () -> {
                click();
                openCosDialog(item, item.price() > 0);
            });
        }
        ctx.draw(); // the 3D icons are buffered: draw them while the clip is still on
        endClip(ctx);
        scrollbar(ctx, right + 1, top, bottom);

        if (items.isEmpty()) {
            Theme.textCentered(ctx, "Nothing matches", (x + right) / 2, top + 30, Theme.TEXT_2);
            Theme.small(ctx, "Try another word or colour", (x + right) / 2 - Theme.smallWidth("Try another word or colour") / 2, top + 44, Theme.TEXT_3);
        }
        String footer = items.size() + " of " + Cosmetics.of(cosSlot).size() + " " + cosSlot.displayName.toLowerCase(Locale.ROOT)
                + " cosmetics · hover to try on, click to wear";
        Theme.small(ctx, footer, x, r[3] - 7, Theme.TEXT_3);

        if (cosDialog != null) {
            Cosmetics.preview(cosDialog.slot(), cosDialog.id());
            renderCosDialog(ctx, mx, my, x, r[1], right, r[3]);
        } else if (hover != null) {
            Cosmetics.preview(hover.slot(), hover.id());
            String tier = hover.tier().name().charAt(0) + hover.tier().name().substring(1).toLowerCase(Locale.ROOT);
            boolean mine = dev.onix.client.network.OnixWallet.owns(hover);
            tooltip = hover.name() + "\n" + tier + (hover.palette() != null ? " · " + dev.onix.client.cosmetics.CosmeticCatalog.paletteName(hover.palette()) : "")
                    + (mine ? (hover.price() == 0 ? " · Free" : " · Owned") : " · " + hover.price() + " coins")
                    + (mine ? "\nClick to wear · right-click to gift" : "\nClick to buy · right-click to gift")
                    + (cape ? "\nNeeds \"Cape\" on in Skin Customization" : "");
        }
    }

    // ---------- coins, buying and gifts

    /** Coin balance at the top of the preview column. */
    private void renderWallet(DrawContext ctx, int mx, int my, int x1, int y, int x2) {
        boolean noServer = OnixClient.serverUrl().isBlank();
        boolean hov = hovered(mx, my, x1, y, x2, y + 22);
        Theme.card(ctx, x1, y, x2, y + 22, 7, hov ? Theme.SURFACE_3 : Theme.SURFACE_2, Theme.BORDER);
        Theme.round(ctx, x1 + 6, y + 6, x1 + 16, y + 16, 5, 0xFFFFC23D);
        Theme.round(ctx, x1 + 8, y + 8, x1 + 14, y + 14, 3, 0xFFE0A21E);
        Theme.textBold(ctx, dev.onix.client.network.OnixWallet.balanceText(), x1 + 21, y + 3, Theme.TEXT);
        String sub;
        if (noServer) sub = "No Onix server set";
        else if (!dev.onix.client.network.OnixWallet.known()) sub = "Connecting…";
        else if (dev.onix.client.network.OnixWallet.unlimited()) sub = "Unlimited coins";
        else if (client.world == null) sub = "Play to earn coins";
        else sub = "+1 in " + dev.onix.client.network.OnixWallet.secondsToNextCoin() + "s";
        Theme.small(ctx, sub, x1 + 21, y + 13, Theme.TEXT_3);
        tooltipIf(hov, noServer
                ? "Coins need an Onix server.\nSet it in Settings → Onix Server."
                : "1 coin for every minute you play with Onix.\n" + dev.onix.client.network.OnixWallet.playMinutes() + " minutes played · "
                + dev.onix.client.network.OnixWallet.ownedCount() + " cosmetics bought");
        hit(x1, y, x2, y + 22, () -> {
            if (noServer) navigate(View.SETTINGS, null, false, null);
            else dev.onix.client.network.OnixWallet.refresh();
        }, null);
    }

    private void openCosDialog(dev.onix.client.cosmetics.CosmeticCatalog.Item item, boolean gift) {
        cosDialog = item;
        cosGiftMode = gift;
        cosBusy = false;
        cosDialogError = null;
        giftMessage.setText("");
        if (gift) focusOnly(giftTo);
    }

    private void closeCosDialog() {
        cosDialog = null;
        cosBusy = false;
        giftTo.focus(false);
        giftMessage.focus(false);
    }

    private void renderCosDialog(DrawContext ctx, int mx, int my, int ax1, int ay1, int ax2, int ay2) {
        var item = cosDialog;
        boolean mine = dev.onix.client.network.OnixWallet.owns(item);
        boolean online = !OnixClient.serverUrl().isBlank() && dev.onix.client.network.OnixWallet.known();
        int w = 206;
        int h = cosGiftMode ? 138 : 104;
        int x1 = (ax1 + ax2) / 2 - w / 2;
        int y1 = (ay1 + ay2) / 2 - h / 2;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(0, 0, 300);
        // dim the page, clicks outside close the dialog
        ctx.fill(ax1 - 4, ay1, ax2 + 4, ay2, Theme.withAlpha(0xFF000000, 110));
        hit(ax1 - 4, ay1, ax2 + 4, ay2, this::closeCosDialog, this::closeCosDialog);
        Theme.shadow(ctx, x1, y1, x1 + w, y1 + h, 10);
        Theme.card(ctx, x1, y1, x1 + w, y1 + h, 10, Theme.SURFACE, Theme.BORDER_STRONG);
        hit(x1, y1, x1 + w, y1 + h, () -> {}, () -> {});

        // icon + title
        Theme.card(ctx, x1 + 10, y1 + 10, x1 + 46, y1 + 46, 6, Theme.SURFACE_2, Theme.BORDER);
        if (item.isCape()) {
            float t = (System.currentTimeMillis() % 1_000_000L) / 50f;
            dev.onix.client.cosmetics.CapeRenderer.drawIcon(ctx, item, x1 + 12, y1 + 12, 32, 32, t, (float) Math.sin(t * 0.06f) * 35f);
            ctx.draw();
        } else {
            float t = (System.currentTimeMillis() % 1_000_000L) / 50f;
            dev.onix.client.cosmetics.CosmeticModels.drawIcon(ctx, item, x1 + 12, y1 + 12, 32, 32, t, (t * 3f) % 360f);
            ctx.draw();
        }
        Theme.textBold(ctx, Theme.ellipsize(item.name(), w - 66), x1 + 54, y1 + 12, Theme.TEXT);
        String tier = item.tier().name().charAt(0) + item.tier().name().substring(1).toLowerCase(Locale.ROOT);
        Theme.small(ctx, tier, x1 + 54, y1 + 25, tierColor(item.tier()));
        String priceText = item.price() == 0 ? "Free for everyone" : item.price() + " coins";
        Theme.round(ctx, x1 + 54, y1 + 35, x1 + 60, y1 + 41, 3, 0xFFFFC23D);
        Theme.small(ctx, priceText, x1 + 63, y1 + 35, Theme.TEXT_2);

        int by = y1 + h - 26;
        int bx = x1 + 10;
        int bw = (w - 30) / 2;
        if (!cosGiftMode) {
            String line;
            if (!online) line = OnixClient.serverUrl().isBlank() ? "Set an Onix server in Settings to use coins." : "Waiting for the Onix server…";
            else if (mine) line = item.price() == 0 ? "Everyone owns this one." : "You own this cosmetic.";
            else if (dev.onix.client.network.OnixWallet.canAfford(item)) line = "You have " + dev.onix.client.network.OnixWallet.balanceText() + " coins.";
            else line = "You need " + (item.price() - dev.onix.client.network.OnixWallet.coins()) + " more coins – keep playing!";
            Theme.small(ctx, line, x1 + 10, y1 + 56, Theme.TEXT_2);
            if (cosDialogError != null) Theme.small(ctx, Theme.ellipsize(cosDialogError, w - 20), x1 + 10, y1 + 67, Theme.RED);
            if (mine) {
                button(ctx, mx, my, bx, by, bw, 17, Cosmetics.isSelected(item) ? "Take Off" : "Wear", true, () -> {
                    Cosmetics.select(item.slot(), Cosmetics.isSelected(item) ? null : item.id());
                    closeCosDialog();
                });
            } else {
                boolean can = online && !cosBusy && dev.onix.client.network.OnixWallet.canAfford(item);
                String label = cosBusy ? "Buying…" : "Buy · " + item.price();
                if (can) {
                    button(ctx, mx, my, bx, by, bw, 17, label, true, () -> buyCosmetic(item));
                } else {
                    Theme.card(ctx, bx, by, bx + bw, by + 17, Theme.corner(6), Theme.SURFACE_2, Theme.BORDER);
                    Theme.textCentered(ctx, label, bx + bw / 2, by + 5, Theme.TEXT_3);
                }
            }
            if (item.price() > 0) button(ctx, mx, my, bx + bw + 10, by, bw, 17, "Gift…", false, () -> {
                cosGiftMode = true;
                cosDialogError = null;
                focusOnly(giftTo);
            });
            else button(ctx, mx, my, bx + bw + 10, by, bw, 17, "Close", false, this::closeCosDialog);
        } else {
            int fy = y1 + 54;
            for (Input in : List.of(giftTo, giftMessage)) {
                Theme.card(ctx, x1 + 10, fy, x1 + w - 10, fy + 17, 7, Theme.SURFACE_2, in.focused() ? Theme.withAlpha(Theme.accent(), 200) : Theme.BORDER_STRONG);
                in.render(ctx, x1 + 17, fy + 5, w - 34, Theme.TEXT, Theme.TEXT_3);
                Input target = in;
                hit(x1 + 10, fy, x1 + w - 10, fy + 17, () -> focusOnly(target), () -> target.setText(""));
                fy += 21;
            }
            String info = cosDialogError != null ? cosDialogError
                    : !online ? "Set an Onix server in Settings to send gifts."
                    : dev.onix.client.network.OnixWallet.canAfford(item) ? "You pay " + item.price() + " coins · they need to have played with Onix."
                    : "You need " + (item.price() - dev.onix.client.network.OnixWallet.coins()) + " more coins.";
            Theme.small(ctx, Theme.ellipsize(info, w - 20), x1 + 10, fy + 2, cosDialogError != null ? Theme.RED : Theme.TEXT_3);
            boolean can = online && !cosBusy && dev.onix.client.network.OnixWallet.canAfford(item) && !giftTo.text().isBlank();
            String label = cosBusy ? "Sending…" : "Send Gift";
            if (can) {
                button(ctx, mx, my, bx, by, bw, 17, label, true, () -> giftCosmetic(item));
            } else {
                Theme.card(ctx, bx, by, bx + bw, by + 17, Theme.corner(6), Theme.SURFACE_2, Theme.BORDER);
                Theme.textCentered(ctx, label, bx + bw / 2, by + 5, Theme.TEXT_3);
            }
            button(ctx, mx, my, bx + bw + 10, by, bw, 17, "Back", false, () -> {
                cosGiftMode = false;
                cosDialogError = null;
                giftTo.focus(false);
                giftMessage.focus(false);
            });
        }
        ctx.getMatrices().pop();
    }

    private void buyCosmetic(dev.onix.client.cosmetics.CosmeticCatalog.Item item) {
        cosBusy = true;
        cosDialogError = null;
        dev.onix.client.network.OnixWallet.buy(item, error -> {
            cosBusy = false;
            if (error != null) {
                cosDialogError = error;
                return;
            }
            Cosmetics.select(item.slot(), item.id());
            Notifications.show("Cosmetic bought", item.name() + " – you are wearing it now");
            if (cosDialog == item) closeCosDialog();
        });
    }

    private void giftCosmetic(dev.onix.client.cosmetics.CosmeticCatalog.Item item) {
        String to = giftTo.text().trim();
        if (to.isEmpty() || cosBusy) return;
        cosBusy = true;
        cosDialogError = null;
        dev.onix.client.network.OnixWallet.gift(item, to, giftMessage.text().trim(), error -> {
            cosBusy = false;
            if (error != null) {
                cosDialogError = error;
                return;
            }
            Notifications.show("Gift sent", item.name() + " to " + to);
            if (cosDialog == item) closeCosDialog();
        });
    }

    /** Items of the current slot, filtered by the search words and the colour. */
    private List<dev.onix.client.cosmetics.CosmeticCatalog.Item> cosmeticList() {
        String q = cosSearch.text().trim().toLowerCase(Locale.ROOT);
        String[] words = q.isEmpty() ? new String[0] : q.split("\\s+");
        List<dev.onix.client.cosmetics.CosmeticCatalog.Item> out = new ArrayList<>();
        for (var item : Cosmetics.of(cosSlot)) {
            if (cosPalette != null && !cosPalette.equals(item.palette())) continue;
            if (cosOwnedOnly && !dev.onix.client.network.OnixWallet.owns(item)) continue;
            String hay = (item.name() + " " + item.tier().name()).toLowerCase(Locale.ROOT);
            boolean ok = true;
            for (String w : words) {
                if (!hay.contains(w)) {
                    ok = false;
                    break;
                }
            }
            if (ok) out.add(item);
        }
        return out;
    }

    private static int tierColor(dev.onix.client.cosmetics.CosmeticCatalog.Tier tier) {
        return switch (tier) {
            case COMMON -> 0xFF9AA0AA;
            case RARE -> 0xFF4EA8FF;
            case EPIC -> 0xFFB46CFF;
            case LEGENDARY -> 0xFFFFC23D;
        };
    }

    private boolean tile(DrawContext ctx, int mx, int my, int x, int y, int w, int h, boolean selected, Object key) {
        boolean hov = hovered(mx, my, x, y, x + w, y + h);
        float ht = anim("tile:" + key, hov, 18f);
        int border = selected ? Theme.accent() : Theme.lerpColor(Theme.BORDER, Theme.BORDER_STRONG, ht);
        Theme.card(ctx, x, y, x + w, y + h, 6, Theme.lerpColor(Theme.SURFACE_2, Theme.SURFACE_3, ht), border);
        return hov;
    }

    // ---------- profiles

    private void renderProfiles(DrawContext ctx, int mx, int my, int[] r) {
        int x = r[0];
        int w = r[2] - r[0];
        List<String> profiles = OnixClient.PROFILES.list();
        int rowH = 32;
        int listTop = r[1] + 10;
        int listBottom = r[3] - 92;
        contentHeight = profiles.size() * rowH;
        clampScroll(listBottom - listTop);

        Theme.label(ctx, "Your Profiles", x + 2, r[1], Theme.TEXT_3);
        int visibleH = Math.min(contentHeight, listBottom - listTop);
        Theme.card(ctx, x, listTop, r[2], listTop + visibleH, 8, Theme.SURFACE_2, Theme.BORDER);
        beginClip(ctx, x, listTop + 1, r[2], listTop + visibleH - 1);
        int y = listTop - (int) Math.round(scroll);
        for (int i = 0; i < profiles.size(); i++) {
            String name = profiles.get(i);
            boolean active = name.equals(OnixClient.PROFILES.active());
            if (i > 0) ctx.fill(x + 12, y, r[2] - 12, y + 1, Theme.BORDER);
            boolean hov = hovered(mx, my, x, y, r[2], y + rowH);
            if (hov && !active) Theme.round(ctx, x + 2, y + 1, r[2] - 2, y + rowH - 1, 6, Theme.withAlpha(Theme.SURFACE_3, 0x90));
            // radio dot
            Theme.round(ctx, x + 12, y + 11, x + 22, y + 21, 5, active ? Theme.accent() : Theme.BORDER_STRONG);
            Theme.round(ctx, x + 13, y + 12, x + 21, y + 20, 4, active ? Theme.accent() : Theme.SURFACE_2);
            if (active) Theme.round(ctx, x + 15, y + 14, x + 19, y + 18, 2, Theme.onAccent());
            Theme.textBold(ctx, name, x + 30, y + 8, Theme.TEXT);
            List<String> servers = serversFor(name);
            String sub = active ? "Active" : servers.isEmpty() ? "Click to load" : "Linked to " + servers.get(0)
                    + (servers.size() > 1 ? " +" + (servers.size() - 1) : "");
            if (active && !servers.isEmpty()) sub = "Active · " + servers.get(0);
            Theme.small(ctx, sub, x + 30, y + 19, active ? Theme.accent() : Theme.TEXT_3);
            if (!active) {
                boolean dh = hovered(mx, my, r[2] - 28, y + 7, r[2] - 8, y + 25);
                if (dh) Theme.round(ctx, r[2] - 28, y + 7, r[2] - 8, y + 25, 5, Theme.withAlpha(Theme.RED, 40));
                if (hov) drawIcon(ctx, "x", r[2] - 22, y + 12, 8, dh ? Theme.RED : Theme.TEXT_3);
                hit(x, y, r[2] - 30, y + rowH, () -> {
                    click();
                    OnixClient.PROFILES.switchTo(name);
                    hsvCache.clear();
                    textInputs.clear();
                    Notifications.show("Profile loaded", name);
                }, null);
                hit(r[2] - 28, y + 7, r[2] - 8, y + 25, () -> {
                    if (OnixClient.PROFILES.delete(name)) {
                        OnixClient.CONFIG.serverProfiles.values().removeIf(v -> v.equals(name));
                        OnixClient.CONFIG.save();
                        profileMessage = "Deleted \"" + name + "\"";
                    }
                }, null);
                tooltipIf(dh, "Delete profile");
            }
            y += rowH;
        }
        endClip(ctx);
        scrollbar(ctx, r[2] + 4, listTop, listTop + visibleH);

        // new profile
        int fy = r[3] - 78;
        Theme.label(ctx, "New Profile", x + 2, fy - 2, Theme.TEXT_3);
        int bw = 84;
        int fx2 = r[2] - bw - 6;
        boolean focused = profileName.focused();
        Theme.card(ctx, x, fy + 8, fx2, fy + 30, 7, Theme.SURFACE_SUNKEN,
                focused ? Theme.withAlpha(Theme.accent(), 200) : Theme.BORDER);
        profileName.render(ctx, x + 10, fy + 15, fx2 - x - 18, Theme.TEXT, Theme.TEXT_3);
        hit(x, fy + 8, fx2, fy + 30, () -> focusOnly(profileName), null);
        button(ctx, mx, my, fx2 + 6, fy + 8, bw, 22, "Create", true, this::createProfile);
        String info = profileMessage.isEmpty() ? "Starts as a copy of the current profile." : profileMessage;
        Theme.small(ctx, info, x + 2, fy + 35, profileMessage.isEmpty() ? Theme.TEXT_3 : Theme.TEXT_2);

        // server link
        int sy = r[3] - 22;
        ServerInfo server = client.getCurrentServerEntry();
        if (server != null && !client.isInSingleplayer()) {
            String address = server.address.toLowerCase(Locale.ROOT);
            boolean linked = OnixClient.PROFILES.active().equals(OnixClient.CONFIG.serverProfiles.get(address));
            String label = linked ? "Unlink " + server.address + " from this profile" : "Link this profile to " + server.address;
            button(ctx, mx, my, x, sy, w, 22, Theme.ellipsize(label, w - 20), false, () -> {
                if (linked) OnixClient.CONFIG.serverProfiles.remove(address);
                else OnixClient.CONFIG.serverProfiles.put(address, OnixClient.PROFILES.active());
                OnixClient.CONFIG.save();
            });
        } else {
            Theme.small(ctx, "Join a server to link a profile to it. Linked profiles load automatically.", x + 2, sy + 8, Theme.TEXT_3);
        }
    }

    private List<String> serversFor(String profile) {
        List<String> list = new ArrayList<>();
        OnixClient.CONFIG.serverProfiles.forEach((server, name) -> {
            if (name.equals(profile)) list.add(server);
        });
        return list;
    }

    private void createProfile() {
        if (OnixClient.PROFILES.create(profileName.text())) {
            profileMessage = "Created and activated \"" + OnixClient.PROFILES.active() + "\"";
            Notifications.show("Profile created", OnixClient.PROFILES.active());
            profileName.setText("");
            profileName.focus(false);
        } else {
            profileMessage = "Enter a new, valid name.";
        }
    }

    // ================================================================== drawing helpers

    /** Simple word wrap measured in normal-size UI text. */
    private static List<String> wrap(String text, int maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (Theme.width(candidate) > maxWidth && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                    if (lines.size() == maxLines) break;
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            if (lines.size() < maxLines && !line.isEmpty()) lines.add(line.toString());
            if (lines.size() >= maxLines) break;
        }
        if (lines.size() == maxLines) {
            String last = lines.get(maxLines - 1);
            if (Theme.width(last) > maxWidth) lines.set(maxLines - 1, Theme.ellipsize(last, maxWidth));
        }
        return lines;
    }

    private float anim(Object key, boolean target, float speed) {
        return anims.computeIfAbsent(key, k -> new Anim(target ? 1 : 0)).to(target ? 1 : 0, speed);
    }

    private void hit(int x1, int y1, int x2, int y2, Runnable left, Runnable right) {
        if (clip != null) {
            x1 = Math.max(x1, clip[0]);
            y1 = Math.max(y1, clip[1]);
            x2 = Math.min(x2, clip[2]);
            y2 = Math.min(y2, clip[3]);
            if (x1 >= x2 || y1 >= y2) return;
        }
        hits.add(new Hit(x1, y1, x2, y2, left, right));
    }

    private void wheel(int x1, int y1, int x2, int y2, java.util.function.IntConsumer action) {
        if (clip != null) {
            x1 = Math.max(x1, clip[0]);
            y1 = Math.max(y1, clip[1]);
            x2 = Math.min(x2, clip[2]);
            y2 = Math.min(y2, clip[3]);
            if (x1 >= x2 || y1 >= y2) return;
        }
        wheels.add(new Wheel(x1, y1, x2, y2, action));
    }

    private void middle(int x1, int y1, int x2, int y2, Runnable action) {
        middleHits.add(new Hit(x1, y1, x2, y2, action, null));
    }

    /** Applies the number typed after clicking a slider's value; invalid input leaves the value as it was. */
    private void commitNumber() {
        if (editingNumber == null) return;
        String raw = numberInput.text().replace(',', '.').replaceAll("[^0-9.\\-]", "");
        try {
            if (!raw.isEmpty()) editingNumber.set(Double.parseDouble(raw));
        } catch (NumberFormatException ignored) {
            // keep the old value
        }
        editingNumber = null;
        numberInput.focus(false);
        Theme.refresh();
        OnixClient.save();
    }

    private void toggleModule(Module m) {
        m.toggle();
        pulses.put(m.getId(), System.currentTimeMillis());
        click();
        OnixClient.save();
    }

    private boolean hovered(int mx, int my, int x1, int y1, int x2, int y2) {
        if (clip != null && !Theme.inside(mx, my, clip[0], clip[1], clip[2], clip[3])) return false;
        // the open dropdown (rect from the previous frame) covers what lies beneath it
        if (dropdown != null && dropdownRect != null && Theme.inside(mx, my, dropdownRect[0], dropdownRect[1], dropdownRect[2], dropdownRect[3])) return false;
        return Theme.inside(mx, my, x1, y1, x2, y2);
    }

    private void tooltipIf(boolean condition, String text) {
        if (condition) tooltip = text;
    }

    private void beginClip(DrawContext ctx, int x1, int y1, int x2, int y2) {
        clip = new int[]{x1, y1, x2, y2};
        ctx.enableScissor(sx(x1), sy(y1), sx(x2), sy(y2));
    }

    private void endClip(DrawContext ctx) {
        ctx.disableScissor();
        clip = null;
    }

    private void drawTinted(DrawContext ctx, Identifier tex, int x, int y, int w, int h, int texSize, int color, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        ctx.setShaderColor(Theme.red(color), Theme.green(color), Theme.blue(color), alpha);
        ctx.drawTexture(tex, x, y, w, h, 0f, 0f, texSize, texSize, texSize, texSize);
        ctx.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private void drawIcon(DrawContext ctx, String name, int x, int y, int size, int color) {
        drawTinted(ctx, icon(name), x, y, size, size, 64, color, ((color >>> 24) & 0xFF) / 255f);
    }

    private int button(DrawContext ctx, int mx, int my, int x, int y, int w, int h, String label, boolean primary, Runnable action) {
        boolean hov = hovered(mx, my, x, y, x + w, y + h);
        float t = anim("btn:" + label, hov, 18f);
        int fill;
        int border;
        int text;
        if (primary) {
            fill = Theme.lerpColor(Theme.accent(), Theme.lerpColor(Theme.accent(), 0xFFFFFFFF, 0.18f), t);
            border = fill;
            text = Theme.onAccent();
        } else {
            fill = Theme.lerpColor(Theme.SURFACE_2, Theme.SURFACE_3, t);
            border = Theme.lerpColor(Theme.BORDER, Theme.BORDER_STRONG, t);
            text = Theme.TEXT;
        }
        Theme.card(ctx, x, y, x + w, y + h, Theme.corner(6), fill, border);
        Text label2 = Theme.bold(label);
        int tw = Theme.width(label2);
        ctx.drawText(textRenderer, label2, x + (w - tw) / 2, y + (h - 8) / 2 + 1, text, false);
        hit(x, y, x + w, y + h, () -> {
            click();
            action.run();
        }, null);
        return w;
    }

    private void scrollbar(DrawContext ctx, int x, int top, int bottom) {
        int view = bottom - top;
        if (contentHeight <= view) return;
        int bar = Math.max(18, view * view / contentHeight);
        int y = top + (int) ((view - bar) * (scroll / (contentHeight - view)));
        Theme.round(ctx, x, y, x + 2, y + bar, 1, Theme.withAlpha(Theme.TEXT, 50));
    }

    private void clampScroll(int view) {
        double max = Math.max(0, contentHeight - view);
        scrollTarget = Math.max(0, Math.min(scrollTarget, max));
        scroll = Math.max(0, Math.min(scroll, max));
    }

    // ================================================================== input

    @Override
    public boolean mouseClicked(double rawX, double rawY, int button) {
        double mouseX = vx(rawX);
        double mouseY = vy(rawY);
        // waiting for a key: the middle and side mouse buttons can be bound too
        if (listening != null && button >= 2) {
            listening.set(KeybindSetting.mouse(button));
            listening = null;
            OnixClient.save();
            return true;
        }
        focusIndex = -1;
        if (button == 2) {
            for (int i = middleHits.size() - 1; i >= 0; i--) {
                Hit h = middleHits.get(i);
                if (Theme.inside(mouseX, mouseY, h.x1(), h.y1(), h.x2(), h.y2())) {
                    h.left().run();
                    return true;
                }
            }
            return false;
        }
        boolean wasEditing = editingNumber != null;
        listening = null;
        search.focus(false);
        profileName.focus(false);
        cosSearch.focus(false);
        giftTo.focus(false);
        giftMessage.focus(false);
        unfocusTextInputs();
        // clicking anywhere but the number field confirms a typed number
        if (wasEditing && (numberField == null || !Theme.inside(mouseX, mouseY, numberField[0], numberField[1], numberField[2], numberField[3]))) {
            commitNumber();
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (Theme.inside(mouseX, mouseY, h.x1(), h.y1(), h.x2(), h.y2())) {
                Runnable action = button == 1 ? h.right() : button == 0 ? h.left() : null;
                if (action != null) {
                    action.run();
                    if (draggingSlider != null) updateSlider(mouseX);
                    if (colorDrag != 0) updateColorDrag(mouseX, mouseY);
                    return true;
                }
                // a hit without an action for this button still blocks what lies beneath it
                if (button == 0 || button == 1) return true;
            }
        }
        // click beside the window: Bloom goes back to the flower, Classic closes
        if (button == 0) {
            if (bloom() && view != View.HUB) back();
            else close();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double rawX, double rawY, int button, double deltaX, double deltaY) {
        if (draggingSlider != null) {
            updateSlider(vx(rawX));
            return true;
        }
        if (colorDrag != 0) {
            updateColorDrag(vx(rawX), vy(rawY));
            return true;
        }
        if (draggingPreview) {
            previewRotation += (float) deltaX * 2.5f;
            return true;
        }
        return super.mouseDragged(rawX, rawY, button, deltaX, deltaY);
    }

    private void updateSlider(double mouseX) {
        draggingSlider.setProgress((mouseX - sliderX1) / (double) (sliderX2 - sliderX1));
        Theme.refresh();
    }

    @Override
    public boolean mouseReleased(double rawX, double rawY, int button) {
        if (draggingSlider != null || colorDrag != 0) {
            draggingSlider = null;
            colorDrag = 0;
            OnixClient.save();
            return true;
        }
        draggingPreview = false;
        return super.mouseReleased(rawX, rawY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (dropdown != null && dropdownRect != null
                && Theme.inside(vx(mouseX), vy(mouseY), dropdownRect[0], dropdownRect[1], dropdownRect[2], dropdownRect[3])) {
            dropdownOffset -= (int) Math.signum(verticalAmount);
            return true;
        }
        dropdown = null;
        double vxm = vx(mouseX);
        double vym = vy(mouseY);
        for (int i = wheels.size() - 1; i >= 0; i--) {
            Wheel w = wheels.get(i);
            if (Theme.inside(vxm, vym, w.x1(), w.y1(), w.x2(), w.y2())) {
                w.action().accept((int) Math.signum(verticalAmount));
                return true;
            }
        }
        scrollTarget -= verticalAmount * 28;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening != null) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) listening.set(KeybindSetting.NONE);
            else if (keyCode != GLFW.GLFW_KEY_ESCAPE) listening.set(keyCode);
            listening = null;
            OnixClient.save();
            return true;
        }
        if (dropdown != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            dropdown = null;
            return true;
        }
        if (editingNumber != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                editingNumber = null;
                numberInput.focus(false);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_TAB) {
                commitNumber();
                return true;
            }
            if (numberInput.keyPressed(keyCode, modifiers)) return true;
        }
        if (view == View.MODULES && !anyInputFocused() && gridNavigation(keyCode, modifiers)) return true;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_F) {
            if (view == View.COSMETICS) {
                focusOnly(cosSearch);
                return true;
            }
            if (bloom() && view != View.HUB && view != View.MODULES) back();
            focusOnly(search);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && cosDialog != null && view == View.COSMETICS) {
            closeCosDialog();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (anyInputFocused()) {
                if (search.focused() && !search.text().isEmpty()) search.setText("");
                if (cosSearch.focused() && !cosSearch.text().isEmpty()) cosSearch.setText("");
                search.focus(false);
                profileName.focus(false);
                cosSearch.focus(false);
                unfocusTextInputs();
                return true;
            }
            if (!search.text().isEmpty()) {
                search.setText("");
                return true;
            }
            if (expandedColor != null) {
                expandedColor = null;
                return true;
            }
            if (view == View.MODULE || (bloom() && view != View.HUB)) {
                back();
                return true;
            }
            close();
            return true;
        }
        if (search.keyPressed(keyCode, modifiers) || profileName.keyPressed(keyCode, modifiers) || cosSearch.keyPressed(keyCode, modifiers)
                || giftTo.keyPressed(keyCode, modifiers) || giftMessage.keyPressed(keyCode, modifiers)) return true;
        for (Input in : textInputs.values()) if (in.keyPressed(keyCode, modifiers)) return true;
        if (OnixClient.MENU_KEY.matchesKey(keyCode, scanCode) && !anyInputFocused()) { // Right Shift also types capitals
            close();
            return true;
        }
        // typing a letter starts a search
        boolean searchable = view == View.MODULES || (bloom() && view == View.HUB);
        boolean letter = !ctrl && keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z;
        if (view == View.COSMETICS && letter && cosDialog == null && !anyInputFocused()) focusOnly(cosSearch);
        else if (searchable && letter) focusOnly(search);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Arrow keys move through the module cards, Enter toggles, Shift+Enter opens the settings. */
    private boolean gridNavigation(int keyCode, int modifiers) {
        int n = gridList.size();
        if (n == 0) return false;
        int move = switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> -1;
            case GLFW.GLFW_KEY_RIGHT -> 1;
            case GLFW.GLFW_KEY_UP -> -gridCols;
            case GLFW.GLFW_KEY_DOWN -> gridCols;
            default -> 0;
        };
        if (move != 0) {
            focusIndex = focusIndex < 0 ? 0 : Math.max(0, Math.min(n - 1, focusIndex + move));
            // keep the focused card in view
            int rowTop = (focusIndex / gridCols) * 78;
            if (rowTop < scrollTarget) scrollTarget = rowTop;
            if (rowTop + 70 > scrollTarget + gridViewHeight) scrollTarget = rowTop + 70 - gridViewHeight;
            return true;
        }
        if (focusIndex < 0 || focusIndex >= n) return false;
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_SPACE) {
            Module m = gridList.get(focusIndex).module();
            if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) openModule(m);
            else toggleModule(m);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (editingNumber != null && numberInput.charTyped(chr)) return true;
        if (search.charTyped(chr) || profileName.charTyped(chr) || cosSearch.charTyped(chr) || giftTo.charTyped(chr) || giftMessage.charTyped(chr)) return true;
        for (Input in : textInputs.values()) if (in.charTyped(chr)) return true;
        return super.charTyped(chr, modifiers);
    }
}
