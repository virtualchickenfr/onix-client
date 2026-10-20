package dev.onix.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.onix.client.OnixClient;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Onix title screen with several layouts ("designs"). Switch with the arrow keys, by dragging
 * sideways, with the dots at the bottom, or under Design in the Onix menu.
 */
public class OnixTitleScreen extends Screen {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("onix", "textures/gui/logo.png");

    // the title screen sits on the background picture, so its own text stays light
    private static final int WHITE = 0xFFF4F4F6;
    private static final int GREY = 0xFFA1A1AA;
    private static final int DIM = 0xFF6B6B76;

    private record Item(String label, String icon, Runnable action, boolean primary) {}

    private record Area(int x1, int y1, int x2, int y2, Runnable action) {}

    private final List<Item> items = new ArrayList<>();
    private final List<Area> areas = new ArrayList<>();
    private final Map<String, Anim> hover = new HashMap<>();
    private final MenuParticles particles = new MenuParticles();
    private final Anim intro = new Anim(0);
    private final Anim switchAnim = new Anim(1);
    private long startNanos;
    private int direction = 1;
    private double dragStartX = Double.NaN;
    private boolean dragged;

    public OnixTitleScreen() {
        super(Component.literal(OnixClient.NAME));
    }

    @Override
    protected void init() {
        if (startNanos == 0) startNanos = System.nanoTime();
        items.clear();
        items.add(new Item("Singleplayer", "world", () -> minecraft.setScreen(new SelectWorldScreen(this)), true));
        items.add(new Item("Multiplayer", "server", () -> minecraft.setScreen(new JoinMultiplayerScreen(this)), true));
        items.add(new Item("Modules", "all", () -> minecraft.setScreen(new OnixMenuScreen(this)), false));
        items.add(new Item("Cosmetics", "cosmetics", () -> minecraft.setScreen(new OnixMenuScreen(this, OnixMenuScreen.View.COSMETICS)), false));
        items.add(new Item("Profiles", "profile", () -> minecraft.setScreen(new OnixMenuScreen(this, OnixMenuScreen.View.PROFILES)), false));
        items.add(new Item("Options", "settings", () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)), false));
        items.add(new Item("Quit Game", "power", () -> minecraft.stop(), false));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private static String[] designs() {
        return OnixClient.CONFIG.titleDesign.modes();
    }

    private static String design() {
        return OnixClient.CONFIG == null ? "Onix" : OnixClient.CONFIG.titleDesign.get();
    }

    private void switchDesign(int dir) {
        if (OnixClient.CONFIG == null) return;
        OnixClient.CONFIG.titleDesign.cycle(dir);
        OnixClient.CONFIG.save();
        direction = dir;
        switchAnim.set(OnixClient.CONFIG.menuAnimations.isOn() ? 0 : 1);
        click();
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.4f));
    }

    // ================================================================== background

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        Theme.refresh();
        float time = (System.nanoTime() - startNanos) / 1_000_000_000f;
        TitleBackgrounds.draw(ctx, width, height, mouseX, mouseY, time);
        ctx.fillGradient(0, 0, width, height, Theme.withAlpha(Theme.current().sky(), 26), Theme.withAlpha(Theme.current().sky(), 0));
        switch (design()) {
            case "Onix", "Split" -> {
                // darken the left side for readability
                int fade = Math.max(300, width / 2);
                for (int i = 0; i < 24; i++) {
                    float t = i / 24f;
                    ctx.fill(fade * i / 24, 0, fade * (i + 1) / 24, height, Theme.withAlpha(0xFF050507, (int) (190 * (1 - t) * (1 - t))));
                }
            }
            default -> ctx.fill(0, 0, width, height, 0x55050507);
        }
        ctx.fillGradient(0, height - 70, width, height, 0x00000000, 0x90000000);
        if (OnixClient.CONFIG == null || OnixClient.CONFIG.menuParticles.isOn()) particles.render(ctx, width, height, 0.6f);
    }

    // ================================================================== render

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        if (!OnixClient.CONFIG.menuDesign.is("Onix")) {
            // another main menu was picked (Carbon designs or vanilla): MinecraftClientMixin opens it
            minecraft.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
            return;
        }
        super.render(ctx, mouseX, mouseY, delta);
        areas.clear();
        float t = Anim.easeOut(intro.to(1f, 4f));
        float s = Anim.easeOut(switchAnim.to(1f, 7f));
        float alpha = t * s;
        int slide = Math.round((1 - t) * 10);
        int dx = Math.round((1 - s) * 24 * direction);

        ctx.pose().pushMatrix();
        ctx.pose().translate(dx, slide);
        switch (design()) {
            case "Centered" -> renderCentered(ctx, mouseX - dx, mouseY - slide, alpha);
            case "Split" -> renderSplit(ctx, mouseX - dx, mouseY - slide, alpha);
            case "Minimal" -> renderMinimal(ctx, mouseX - dx, mouseY - slide, alpha);
            case "Cards" -> renderCards(ctx, mouseX - dx, mouseY - slide, alpha);
            default -> renderOnix(ctx, mouseX - dx, mouseY - slide, alpha);
        }
        ctx.pose().popMatrix();
        // areas were registered in shifted coordinates
        if (dx != 0 || slide != 0) {
            List<Area> shifted = new ArrayList<>();
            for (Area a : areas) shifted.add(new Area(a.x1() + dx, a.y1() + slide, a.x2() + dx, a.y2() + slide, a.action()));
            areas.clear();
            areas.addAll(shifted);
        }

        renderFooter(ctx, mouseX, mouseY, t);
    }

    private float hoverAnim(String key, boolean hovered) {
        return hover.computeIfAbsent(key, k -> new Anim(0)).to(hovered ? 1 : 0, 16f);
    }

    private int a(int color, float alpha) {
        return Theme.withAlpha(color, (int) (((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, alpha))));
    }

    private void drawIcon(GuiGraphics ctx, String name, int x, int y, int size, int color) {
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, Identifier.fromNamespaceAndPath("onix", "textures/gui/icon_" + name + ".png"), x, y, 0f, 0f, size, size, 64, 64, 64, 64, net.minecraft.util.ARGB.colorFromFloat(((color >>> 24) & 0xFF) / 255f, Theme.red(color), Theme.green(color), Theme.blue(color)));
    }

    private void wordmark(GuiGraphics ctx, int x, int y, float scale, float alpha, boolean centered) {
        int logo = Math.round(11 * scale);
        int textW = Math.round(Theme.width(Theme.display("ONIX")) * scale);
        int total = logo + 8 + textW;
        int x0 = centered ? x - total / 2 : x;
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO, x0, y + 1, 0f, 0f, logo, logo, 128, 128, 128, 128, net.minecraft.util.ARGB.colorFromFloat(alpha, 1f, 1f, 1f));
        Theme.scaled(ctx, Theme.display("ONIX"), x0 + logo + 8, y + 1, scale, a(WHITE, alpha));
    }

    private String subtitle() {
        return "Client  ·  Minecraft " + SharedConstants.getCurrentVersion().name();
    }

    // ---------- "Onix": text list on the left

    private void renderOnix(GuiGraphics ctx, int mx, int my, float alpha) {
        int x = Math.max(28, Math.round(width * 0.075f));
        // keep the list clear of the profile row and the footer at the bottom
        // rows get tighter on small windows (or large GUI scales) so nothing runs into the footer
        int step = Math.max(12, Math.min(18, (height - 50 - 84 - 14) / Math.max(1, items.size())));
        int gap = step >= 16 ? 7 : 4;
        int top = Math.max(84, Math.min(height / 2 - 18, height - 50 - items.size() * step - 2 * gap));
        wordmark(ctx, x, top - 74, 2.4f, alpha, false);
        Theme.label(ctx, subtitle(), x + 1, top - 36, a(DIM, alpha));
        int y = top;
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (i == 2 || i == 5) {
                y += gap;
                ctx.fill(x, y - 4, x + 120, y - 3, Theme.withAlpha(0xFFFFFFFF, (int) (18 * alpha)));
            }
            int w = Math.max(140, Theme.boldWidth(item.label()) + 12);
            boolean hov = Theme.inside(mx, my, x - 8, y - (step - 10) / 2, x + w, y + step - (step - 10) / 2);
            float h = hoverAnim("onix:" + item.label(), hov);
            if (h > 0.01f) Theme.round(ctx, x - 8, y + 1, x - 6, y + 9, 1, Theme.fade(Theme.themeAccent(), h * alpha));
            ctx.drawString(font, Theme.bold(item.label()), x + Math.round(h * 4), y + 1, a(Theme.lerpColor(GREY, WHITE, h), alpha), false);
            areas.add(new Area(x - 8, y - (step - 10) / 2, x + w, y + step - (step - 10) / 2, item.action()));
            y += step;
        }
    }

    // ---------- "Centered": wordmark and buttons in the middle

    private void renderCentered(GuiGraphics ctx, int mx, int my, float alpha) {
        int cx = width / 2;
        int bw = 200;
        int top = height / 2 - 30;
        wordmark(ctx, cx, top - 78, 2.8f, alpha, true);
        String sub = subtitle();
        Theme.small(ctx, sub, cx - Theme.smallWidth(sub) / 2, top - 36, a(GREY, alpha));
        int y = top;
        button(ctx, mx, my, items.get(0), cx - bw / 2, y, bw, 22, alpha, true);
        button(ctx, mx, my, items.get(1), cx - bw / 2, y + 26, bw, 22, alpha, true);
        int third = (bw - 8) / 3;
        for (int i = 0; i < 3; i++) button(ctx, mx, my, items.get(2 + i), cx - bw / 2 + i * (third + 4), y + 56, third, 20, alpha, false);
        int half = (bw - 4) / 2;
        button(ctx, mx, my, items.get(5), cx - bw / 2, y + 80, half, 20, alpha, false);
        button(ctx, mx, my, items.get(6), cx - bw / 2 + half + 4, y + 80, half, 20, alpha, false);
    }

    // ---------- "Split": glass panel on the left, big wordmark on the right

    private void renderSplit(GuiGraphics ctx, int mx, int my, float alpha) {
        int pw = Math.min(230, width / 3);
        int px1 = 16, py1 = 16, px2 = 16 + pw, py2 = height - 40;
        card(ctx, px1, py1, px2, py2, 12, alpha);
        wordmark(ctx, px1 + 18, py1 + 20, 1.6f, alpha, false);
        Theme.small(ctx, "Minecraft " + SharedConstants.getCurrentVersion().name(), px1 + 19, py1 + 46, a(textOnCard(GREY), alpha));
        int y = py1 + 70;
        for (int i = 0; i < items.size(); i++) {
            if (i == 2 || i == 5) y += 8;
            Item item = items.get(i);
            int bx1 = px1 + 12, bx2 = px2 - 12;
            boolean hov = Theme.inside(mx, my, bx1, y, bx2, y + 22);
            float h = hoverAnim("split:" + item.label(), hov);
            if (h > 0.01f) Theme.round(ctx, bx1, y, bx2, y + 22, Theme.corner(6), a(Theme.withAlpha(cardHover(), 255), h * alpha));
            drawIcon(ctx, item.icon(), bx1 + 8, y + 6, 10, a(textOnCard(h > 0.5f ? WHITE : GREY), alpha));
            ctx.drawString(font, Theme.bold(item.label()), bx1 + 26, y + 7, a(textOnCard(Theme.lerpColor(GREY, WHITE, h)), alpha), false);
            areas.add(new Area(bx1, y, bx2, y + 22, item.action()));
            y += 24;
        }
        int rx = px2 + (width - px2) / 2;
        Theme.scaled(ctx, Theme.display("ONIX"), rx - Math.round(Theme.width(Theme.display("ONIX")) * 6f) / 2, height / 2 - 40, 6f,
                a(Theme.withAlpha(0xFFFFFFFF, 28), alpha));
        String tag = "Built for PvP. Tuned for you.";
        Theme.text(ctx, tag, rx - Theme.width(tag) / 2, height / 2 + 18, a(GREY, alpha));
    }

    // ---------- "Minimal": a single row along the bottom

    private void renderMinimal(GuiGraphics ctx, int mx, int my, float alpha) {
        wordmark(ctx, 24, 22, 1.4f, alpha, false);
        int y = height - 66;
        int total = 0;
        for (Item item : items) total += Theme.boldWidth(item.label()) + 26;
        int x = width / 2 - total / 2;
        for (Item item : items) {
            int w = Theme.boldWidth(item.label()) + 26;
            boolean hov = Theme.inside(mx, my, x, y - 6, x + w, y + 16);
            float h = hoverAnim("min:" + item.label(), hov);
            ctx.drawString(font, Theme.bold(item.label()), x + 13, y, a(Theme.lerpColor(GREY, WHITE, h), alpha), false);
            int lw = Math.round((w - 26) * h);
            if (lw > 0) ctx.fill(x + 13 + (w - 26 - lw) / 2, y + 12, x + 13 + (w - 26 + lw) / 2, y + 13, a(Theme.themeAccent(), alpha));
            areas.add(new Area(x, y - 6, x + w, y + 16, item.action()));
            x += w;
        }
    }

    // ---------- "Cards": large tiles in the middle

    private void renderCards(GuiGraphics ctx, int mx, int my, float alpha) {
        int cx = width / 2;
        int cw = Math.min(96, (width - 80) / 4);
        int ch = Math.round(cw * 1.05f);
        int gap = 10;
        int total = cw * 4 + gap * 3;
        int top = height / 2 - ch / 2 - 6;
        wordmark(ctx, cx, top - 52, 2.2f, alpha, true);
        for (int i = 0; i < 4; i++) {
            Item item = items.get(i);
            int x1 = cx - total / 2 + i * (cw + gap);
            boolean hov = Theme.inside(mx, my, x1, top, x1 + cw, top + ch);
            float h = hoverAnim("card:" + item.label(), hov);
            int lift = Math.round(h * 3);
            card(ctx, x1, top - lift, x1 + cw, top + ch - lift, Theme.corner(10), alpha);
            if (h > 0.01f) Theme.outline(ctx, x1, top - lift, x1 + cw, top + ch - lift, a(Theme.themeAccent(), h * alpha));
            int is = cw / 3;
            drawIcon(ctx, item.icon(), x1 + (cw - is) / 2, top - lift + ch / 2 - is + 4, is, a(textOnCard(Theme.lerpColor(GREY, WHITE, h)), alpha));
            String label = Theme.ellipsize(item.label(), cw - 10);
            int lw = Theme.boldWidth(label);
            ctx.drawString(font, Theme.bold(label), x1 + (cw - lw) / 2, top - lift + ch - 22, a(textOnCard(WHITE), alpha), false);
            areas.add(new Area(x1, top - lift, x1 + cw, top + ch - lift, item.action()));
        }
        // secondary links
        int y = top + ch + 16;
        int lx = cx - 120;
        int[] widths = new int[3];
        int sum = 0;
        for (int i = 0; i < 3; i++) {
            widths[i] = Theme.width(items.get(4 + i).label()) + 20;
            sum += widths[i];
        }
        lx = cx - sum / 2;
        for (int i = 0; i < 3; i++) {
            Item item = items.get(4 + i);
            boolean hov = Theme.inside(mx, my, lx, y - 4, lx + widths[i], y + 12);
            Theme.text(ctx, item.label(), lx + 10, y, a(hov ? WHITE : GREY, alpha));
            areas.add(new Area(lx, y - 4, lx + widths[i], y + 12, item.action()));
            lx += widths[i];
        }
    }

    // ---------- shared pieces

    private boolean lightCards() {
        return Theme.isLight();
    }

    private int textOnCard(int color) {
        if (!lightCards()) return color;
        return color == WHITE ? 0xFF111114 : color == GREY ? 0xFF52525C : Theme.lerpColor(0xFF52525C, 0xFF111114, 0.5f);
    }

    private int cardHover() {
        return lightCards() ? 0x14000000 : 0x18FFFFFF;
    }

    private void card(GuiGraphics ctx, int x1, int y1, int x2, int y2, int radius, float alpha) {
        int fill = lightCards() ? 0xE8F6F6F8 : 0xB80D0D11;
        int border = lightCards() ? 0x30000000 : 0x26FFFFFF;
        Theme.round(ctx, x1, y1, x2, y2, radius, a(border, alpha));
        Theme.round(ctx, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, radius - 1), a(fill, alpha));
    }

    private void button(GuiGraphics ctx, int mx, int my, Item item, int x, int y, int w, int h, float alpha, boolean primary) {
        boolean hov = Theme.inside(mx, my, x, y, x + w, y + h);
        float t = hoverAnim("btn:" + item.label(), hov);
        int radius = Theme.corner(6);
        if (primary) {
            int fill = Theme.lerpColor(Theme.themeAccent(), Theme.lerpColor(Theme.themeAccent(), 0xFFFFFFFF, 0.18f), t);
            Theme.round(ctx, x, y, x + w, y + h, radius, a(fill, alpha));
            float lum = 0.299f * Theme.red(fill) + 0.587f * Theme.green(fill) + 0.114f * Theme.blue(fill);
            int tc = lum > 0.6f ? 0xFF0B0B0E : 0xFFFFFFFF;
            int tw = Theme.boldWidth(item.label());
            ctx.drawString(font, Theme.bold(item.label()), x + (w - tw) / 2, y + (h - 8) / 2 + 1, a(tc, alpha), false);
        } else {
            card(ctx, x, y, x + w, y + h, radius, alpha);
            if (t > 0.01f) Theme.round(ctx, x + 1, y + 1, x + w - 1, y + h - 1, Math.max(0, radius - 1), a(cardHover(), t * alpha));
            String label = Theme.ellipsize(item.label(), w - 8);
            int tw = Theme.boldWidth(label);
            ctx.drawString(font, Theme.bold(label), x + (w - tw) / 2, y + (h - 8) / 2 + 1, a(textOnCard(Theme.lerpColor(GREY, WHITE, t)), alpha), false);
        }
        areas.add(new Area(x, y, x + w, y + h, item.action()));
    }

    private void renderFooter(GuiGraphics ctx, int mouseX, int mouseY, float t) {
        int x = 16;
        int by = height - 24;
        PlayerSkin skin = minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false).get();
        PlayerFaceRenderer.draw(ctx, skin, x, by - 2, 12);
        Theme.text(ctx, minecraft.getUser().getName(), x + 18, by, a(GREY, t));
        String profile = OnixClient.PROFILES.active();
        Theme.small(ctx, "Profile " + profile + "  ·  " + Theme.current().name(), x + 24 + Theme.width(minecraft.getUser().getName()), by + 2, a(DIM, t));

        String version = OnixClient.NAME + " " + OnixClient.VERSION;
        Theme.small(ctx, version, width - 10 - Theme.smallWidth(version), height - 22, a(DIM, t));
        String copy = "Copyright Mojang AB. Do not distribute!";
        Theme.small(ctx, copy, width - 10 - Theme.smallWidth(copy), height - 13, a(DIM, t));
        int rightEdge = width - 10 - Math.max(Theme.smallWidth(version), Theme.smallWidth(copy)) - 12;
        int leftEdge = x + 24 + Theme.width(minecraft.getUser().getName())
                + Theme.smallWidth("Profile " + profile + "  ·  " + Theme.current().name()) + 12;

        // design switcher: centered, but never on top of the name on the left or the version on the right
        String[] names = designs();
        int current = OnixClient.CONFIG.titleDesign.index();
        int dotsW = names.length * 10;
        int groupW = dotsW + 8 + Theme.smallWidth("More designs");
        int dx = width / 2 - groupW / 2;
        int dy = height - 16;
        if (dx + groupW > rightEdge) dx = rightEdge - groupW;
        if (dx < leftEdge) {
            // not enough room in the footer row: one row higher, centered
            dx = width / 2 - groupW / 2;
            dy = height - 40;
        }
        for (int i = 0; i < names.length; i++) {
            int cx = dx + i * 10 + 5;
            boolean sel = i == current;
            boolean hov = Theme.inside(mouseX, mouseY, cx - 5, dy - 6, cx + 5, dy + 6);
            int r = sel ? 3 : 2;
            Theme.round(ctx, cx - r, dy - r, cx + r, dy + r, r, a(sel ? WHITE : hov ? GREY : DIM, t));
            int index = i;
            areas.add(new Area(cx - 5, dy - 6, cx + 5, dy + 6, () -> {
                int diff = index - OnixClient.CONFIG.titleDesign.index();
                if (diff != 0) {
                    OnixClient.CONFIG.titleDesign.set(names[index]);
                    OnixClient.CONFIG.save();
                    direction = diff > 0 ? 1 : -1;
                    switchAnim.set(OnixClient.CONFIG.menuAnimations.isOn() ? 0 : 1);
                }
            }));
            if (hov) {
                String label = names[i];
                Theme.small(ctx, label, cx - Theme.smallWidth(label) / 2, dy - 14, a(GREY, t));
            }
        }

        // Carbon's menu designs (Carbon, Voxel, Portal, Classic) in their picker
        String more = "More designs";
        int mx0 = dx + dotsW + 8;
        boolean moreHover = Theme.inside(mouseX, mouseY, mx0 - 2, dy - 6, mx0 + Theme.smallWidth(more) + 2, dy + 6);
        Theme.small(ctx, more, mx0, dy - 3, a(moreHover ? WHITE : DIM, t));
        areas.add(new Area(mx0 - 2, dy - 6, mx0 + Theme.smallWidth(more) + 2, dy + 6,
                () -> minecraft.setScreen(new gg.carbon.client.screen.ThemePicker(this))));
    }

    // ================================================================== input

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent onix$e, boolean onix$dbl) {
        double mouseX = onix$e.x(); double mouseY = onix$e.y(); int button = onix$e.button();
        if (button == 0) {
            dragStartX = mouseX;
            dragged = false;
        }
        return super.mouseClicked(onix$e, onix$dbl);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent onix$e, double onix$dx, double onix$dy) {
        double mouseX = onix$e.x(); double mouseY = onix$e.y(); int button = onix$e.button(); double deltaX = onix$dx; double deltaY = onix$dy;
        if (button == 0 && !Double.isNaN(dragStartX) && !dragged && Math.abs(mouseX - dragStartX) > 60) {
            dragged = true;
            switchDesign(mouseX < dragStartX ? 1 : -1);
            return true;
        }
        return super.mouseDragged(onix$e, onix$dx, onix$dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent onix$e) {
        double mouseX = onix$e.x(); double mouseY = onix$e.y(); int button = onix$e.button();
        boolean wasDrag = dragged;
        dragStartX = Double.NaN;
        dragged = false;
        if (button == 0 && !wasDrag) {
            for (int i = areas.size() - 1; i >= 0; i--) {
                Area a = areas.get(i);
                if (Theme.inside(mouseX, mouseY, a.x1(), a.y1(), a.x2(), a.y2())) {
                    click();
                    a.action().run();
                    return true;
                }
            }
        }
        return super.mouseReleased(onix$e);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent onix$k) {
        int keyCode = onix$k.key(); int scanCode = onix$k.scancode(); int modifiers = onix$k.modifiers();
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            switchDesign(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            switchDesign(1);
            return true;
        }
        if (OnixClient.MENU_KEY != null && OnixClient.MENU_KEY.matches(onix$k)) {
            minecraft.setScreen(new OnixMenuScreen(this));
            return true;
        }
        return super.keyPressed(onix$k);
    }
}
