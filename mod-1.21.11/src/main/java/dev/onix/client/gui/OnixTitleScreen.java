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

    // ---- Cinematic design
    /** The "press any key" splash shows once per game start. */
    private static boolean entered;
    private static long enteredAt;
    private final List<Item> cineItems = new ArrayList<>();
    private net.minecraft.client.gui.components.PlayerSkinWidget skinWidget;
    private long shownAt;
    private Runnable pending;
    private long pendingAt;
    private float hoverY = Float.NaN;
    private float hoverW;
    private final List<float[]> sparks = new ArrayList<>();
    private final java.util.Random random = new java.util.Random();
    private boolean dragOnSkin;
    private long lastFrame;

    public OnixTitleScreen() {
        super(Component.literal(OnixClient.NAME));
    }

    @Override
    protected void init() {
        if (startNanos == 0) startNanos = System.nanoTime();
        items.clear();
        items.add(new Item("Singleplayer", "world", () -> minecraft.setScreen(new SelectWorldScreen(this)), true));
        items.add(new Item("Multiplayer", "server", () -> minecraft.setScreen(new JoinMultiplayerScreen(this)), true));
        items.add(new Item("Modules", "all", () -> minecraft.setScreen(OnixClient.CONFIG.menuStyle.is("Clean") ? new gg.carbon.client.screen.ClickGui(this) : new OnixMenuScreen(this)), false));
        items.add(new Item("Cosmetics", "cosmetics", () -> minecraft.setScreen(new OnixMenuScreen(this, OnixMenuScreen.View.COSMETICS)), false));
        items.add(new Item("Profiles", "profile", () -> minecraft.setScreen(new OnixMenuScreen(this, OnixMenuScreen.View.PROFILES)), false));
        items.add(new Item("Options", "settings", () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)), false));
        items.add(new Item("Quit Game", "power", () -> minecraft.stop(), false));
        cineItems.clear();
        cineItems.add(new Item("Singleplayer", "world", () -> minecraft.setScreen(new SelectWorldScreen(this)), true));
        cineItems.add(new Item("Multiplayer", "server", () -> minecraft.setScreen(new JoinMultiplayerScreen(this)), true));
        cineItems.add(new Item("Minecraft Settings", "settings", () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)), false));
        cineItems.add(new Item("Client", "all", () -> minecraft.setScreen(new OnixMenuScreen(this)), false));
        cineItems.add(new Item("Quit Game", "power", () -> minecraft.stop(), false));

        // 1.1.6: everyone gets the cinematic title screen and the glass menus once
        if (OnixClient.CONFIG != null && !OnixClient.CONFIG.cinematicSet.isOn()) {
            OnixClient.CONFIG.cinematicSet.set(true);
            OnixClient.CONFIG.titleDesign.set("Cinematic");
            OnixClient.CONFIG.menuGlass.set(true);
            OnixClient.CONFIG.save();
        }
        // the player in the menu (drag to turn); clicking under it opens the wardrobe
        skinWidget = new net.minecraft.client.gui.components.PlayerSkinWidget(80, 150, minecraft.getEntityModels(),
                minecraft.getSkinManager().createLookup(minecraft.getGameProfile(), false));
        skinWidget.visible = false;
        addRenderableWidget(skinWidget);
        shownAt = System.currentTimeMillis();
        pending = null;
    }

    private boolean cinematic() {
        return "Cinematic".equals(design());
    }

    /** Fades to black, then runs the action (cinematic cut). */
    private void go(Runnable action) {
        if (pending != null) return;
        if (OnixClient.CONFIG != null && !OnixClient.CONFIG.menuAnimations.isOn()) {
            action.run();
            return;
        }
        pending = action;
        pendingAt = System.currentTimeMillis();
    }

    private void enter() {
        if (entered) return;
        entered = true;
        enteredAt = System.currentTimeMillis();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.0f));
        for (int i = 0; i < 70; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            float sp = 40 + random.nextFloat() * 160;
            sparks.add(new float[]{width / 2f, height / 2f - 10, (float) Math.cos(ang) * sp, (float) Math.sin(ang) * sp, 0.6f + random.nextFloat() * 0.8f, 0});
        }
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
        if (cinematic()) {
            renderCinematicBackground(ctx, mouseX, mouseY, time);
            return;
        }
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
        if (cinematic()) {
            renderCinematic(ctx, mouseX, mouseY, delta);
            return;
        }
        if (skinWidget != null) skinWidget.visible = false;
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


    // ================================================================== Cinematic

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }

    private static final Identifier CINE_BG = Identifier.fromNamespaceAndPath("onix", "textures/gui/cinematic_bg.png");
    private static final Identifier CINE_LIGHT = Identifier.fromNamespaceAndPath("onix", "textures/gui/cinematic_light.png");
    private static final int BG_W = 1600;
    private static final int BG_H = 900;
    private float parX;
    private float parY;

    /**
     * A clean studio picture (tools/cinematic_bg.py) with light falling in from the top right. Interactive: the picture
     * and the light beams follow the mouse at different depths and drift slowly on their own.
     */
    private void renderCinematicBackground(GuiGraphics ctx, int mouseX, int mouseY, float time) {
        // the mouse offset eases in, so the picture glides instead of jumping
        float tx = (mouseX - width / 2f) / Math.max(1, width / 2f);
        float ty = (mouseY - height / 2f) / Math.max(1, height / 2f);
        parX += (tx - parX) * 0.06f;
        parY += (ty - parY) * 0.06f;
        float cover = Math.max(width / (float) BG_W, height / (float) BG_H);
        // base picture: slow breathing zoom, small parallax
        float zoom = cover * (1.07f + 0.012f * (float) Math.sin(time * 0.08f));
        int w = Math.round(BG_W * zoom);
        int h = Math.round(BG_H * zoom);
        int x = Math.round((width - w) / 2f - parX * width * 0.012f);
        int y = Math.round((height - h) / 2f - parY * height * 0.012f);
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CINE_BG, x, y, 0f, 0f, w, h, BG_W, BG_H, BG_W, BG_H, 0xFFFFFFFF);
        // light beams: closer to the camera, so they move more; they sway and breathe a little
        float lz = cover * 1.12f;
        int lw = Math.round(BG_W * lz);
        int lh = Math.round(BG_H * lz);
        int lx = Math.round((width - lw) / 2f - parX * width * 0.035f + (float) Math.sin(time * 0.21f) * width * 0.006f);
        int ly = Math.round((height - lh) / 2f - parY * height * 0.03f);
        float breathe = 0.82f + 0.18f * (float) Math.sin(time * 0.5f);
        int la = Math.round(255 * breathe * (entered ? 1f : 0.45f));
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, CINE_LIGHT, lx, ly, 0f, 0f, lw, lh, BG_W, BG_H, BG_W, BG_H, (la << 24) | 0xFFFFFF);
        if (!entered) ctx.fill(0, 0, width, height, 0x99000000);
        // dust drifting through the light
        if (OnixClient.CONFIG == null || OnixClient.CONFIG.menuParticles.isOn()) particles.render(ctx, width, height, 0.3f * (entered ? 1f : 0.4f));
    }

    private void light(GuiGraphics ctx, float cx, float cy, float radius, int color, float strength) {
        SoftShapes.glow(ctx, cx, cy, radius, radius, Theme.withAlpha(color, Math.min(255, Math.round(strength * 18))));
    }

    private void vignette(GuiGraphics ctx) {
        // a bit bigger than the screen so the middle stays clear and the corners go dark
        SoftShapes.vignette(ctx, -width / 8, -height / 8, width + width / 4, height + height / 4, 0xD0000000);
    }

    private void logo(GuiGraphics ctx, float cx, float cy, float size, float alpha, float spin) {
        if (alpha <= 0.01f) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx, cy);
        ctx.pose().rotate(spin);
        int s = Math.round(size);
        SoftShapes.glow(ctx, 0, 0, size * 1.1f, size * 1.1f, Theme.withAlpha(0xFFFFFFFF, Math.round(40 * alpha)));
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO, -s / 2, -s / 2, 0f, 0f, s, s, 128, 128, 128, 128,
                net.minecraft.util.ARGB.colorFromFloat(alpha, 1f, 1f, 1f));
        ctx.pose().popMatrix();
    }

    private void centered(GuiGraphics ctx, Component c, float cx, int y, float scale, int color) {
        int w = Math.round(font.width(c) * scale);
        Theme.scaled(ctx, c, Math.round(cx - w / 2f), y, scale, color);
    }

    private void renderCinematic(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        long now = System.currentTimeMillis();
        float time = (now % 1_000_000L) / 1000f;
        long frame = System.nanoTime();
        float dt = lastFrame == 0 ? 0.016f : Math.min(0.05f, Math.max(0.0005f, (frame - lastFrame) / 1_000_000_000f));
        lastFrame = frame;
        areas.clear();
        float cx = width / 2f;

        if (!entered) {
            // ---- splash: logo breathing in the dark, "press any key"
            float in = smooth((now - shownAt) / 1400f);
            float breathe = 1 + 0.03f * (float) Math.sin(time * 2.2f);
            logo(ctx, cx, height / 2f - 14, 46 * breathe, in, (float) Math.sin(time * 0.6f) * 0.05f);
            centered(ctx, Theme.display("Onix Client"), cx, height / 2 + 22, 1.3f, a(WHITE, in));
            float blink = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(time * 3f));
            centered(ctx, Theme.ui("Press any key to enter"), cx, height / 2 + 42, 1f, a(GREY, in * blink));
            if (skinWidget != null) skinWidget.visible = false;
            renderFade(ctx, now);
            return;
        }

        float since = (now - enteredAt) / 1000f;
        // ---- burst of sparks when entering
        if (!sparks.isEmpty()) {
            for (int i = sparks.size() - 1; i >= 0; i--) {
                float[] p = sparks.get(i);
                p[5] += dt;
                if (p[5] > p[4]) {
                    sparks.remove(i);
                    continue;
                }
                p[0] += p[2] * dt;
                p[1] += p[3] * dt;
                p[2] *= 0.94f;
                p[3] *= 0.94f;
                float life = 1 - p[5] / p[4];
                int sz = life > 0.5f ? 2 : 1;
                ctx.fill(Math.round(p[0]), Math.round(p[1]), Math.round(p[0]) + sz, Math.round(p[1]) + sz, a(WHITE, life));
            }
        }

        boolean skinOn = width >= 420;
        float menuX = skinOn ? width * 0.42f : cx;
        // ---- logo flies from the centre to its place above the menu
        float move = smooth(since / 0.9f);
        float logoY = height / 2f - 14 + (height / 2f - 86 - (height / 2f - 14)) * move;
        float logoX = cx + (menuX - cx) * move;
        float logoSize = 46 + (30 - 46) * move;
        float spin = (1 - move) * (float) Math.PI * 2 + (float) Math.sin(time * 0.5f) * 0.04f;
        logo(ctx, logoX, logoY, logoSize, 1f, spin);
        float titleA = smooth((since - 0.5f) / 0.5f);
        centered(ctx, Theme.display("Onix Client"), menuX, Math.round(logoY + 20), 1.15f, a(WHITE, titleA));

        // ---- menu items: plain text, a soft glass bar glides to the hovered one
        int step = 18;
        int top = Math.round(height / 2f - 40);
        int hovered = -1;
        for (int i = 0; i < cineItems.size(); i++) {
            int y = top + i * step + (i >= 2 ? 6 : 0);
            int w = Math.max(110, Theme.boldWidth(cineItems.get(i).label()) + 30);
            if (Theme.inside(mouseX, mouseY, Math.round(menuX - w / 2f), y - 4, Math.round(menuX + w / 2f), y + 12)) hovered = i;
        }
        if (hovered >= 0) {
            int y = top + hovered * step + (hovered >= 2 ? 6 : 0);
            float w = Math.max(110, Theme.boldWidth(cineItems.get(hovered).label()) + 30);
            if (Float.isNaN(hoverY)) {
                hoverY = y;
                hoverW = w;
            }
            hoverY += (y - hoverY) * Math.min(1f, dt * 16f);
            hoverW += (w - hoverW) * Math.min(1f, dt * 16f);
        } else {
            hoverY = Float.NaN;
        }
        if (!Float.isNaN(hoverY)) {
            int hy = Math.round(hoverY);
            int hw = Math.round(hoverW);
            gg.carbon.client.ui.R2D.rrect(ctx, menuX - hw / 2f, hy - 4, hw, 16, 8, 0x1CFFFFFF);
            gg.carbon.client.ui.R2D.outline(ctx, menuX - hw / 2f, hy - 4, hw, 16, 8, 0.75f, 0x22FFFFFF);
        }
        for (int i = 0; i < cineItems.size(); i++) {
            Item item = cineItems.get(i);
            float ia = smooth((since - 0.55f - i * 0.07f) / 0.4f);
            if (ia <= 0.01f) continue;
            int y = top + i * step + (i >= 2 ? 6 : 0) + Math.round((1 - ia) * 8);
            boolean hov = i == hovered;
            float h = hoverAnim("cine:" + item.label(), hov);
            int color = Theme.lerpColor(0xFFB4B4BC, WHITE, h);
            int tw = Theme.width(item.label());
            ctx.drawString(font, Theme.ui(item.label()), Math.round(menuX - tw / 2f), y, a(color, ia), false);
            int w = Math.max(110, tw + 30);
            Runnable action = item.action();
            areas.add(new Area(Math.round(menuX - w / 2f), y - 4, Math.round(menuX + w / 2f), y + 12, () -> go(action)));
        }

        // ---- the player walks in from the right, turns to you; the wardrobe pill under it
        float walkT = (since - 0.7f) / 1.7f;                 // 0..1 while walking in
        float walk = Math.max(0f, Math.min(1f, walkT));
        float arrive = 1 - (1 - walk) * (1 - walk);           // slows down at the end
        float skinA = smooth((since - 0.7f) / 0.35f);
        if (skinWidget != null) {
            int sh = Math.max(110, Math.min(240, Math.round(height * 0.52f)));
            int sw = Math.round(sh * 0.55f);
            float sxC = width * 0.76f;
            float startX = width + sw;                          // just outside the right edge
            float walkingX = startX + (sxC - startX) * arrive;
            boolean walking = walkT < 1f;
            float stride = walking ? 1f - smooth((walkT - 0.8f) / 0.2f) : 0f;   // legs settle as they arrive
            float phase = since * 9f;
            float bob = walking ? Math.abs((float) Math.sin(phase)) * -3f * stride : (float) Math.sin(time * 1.4f) * 2.5f;
            int sx = Math.round(walkingX - sw / 2f);
            int sy = Math.round(height / 2f - sh / 2f - 6 + bob);
            skinWidget.setRectangle(sw, sh, sx, sy);
            skinWidget.visible = skinOn && skinA > 0.02f;
            poseSkin(walking, walkT, stride, phase, time);
            if (skinWidget.visible) {
                float here = smooth((walkT - 0.6f) / 0.4f);      // name, light and pill appear as they arrive
                SoftShapes.glow(ctx, sxC, height / 2f, sw * 1.6f, sh * 0.75f, Theme.withAlpha(Theme.themeAccent(), Math.round(35 * here)));
                // soft shadow on the "floor" under the feet, moving with them
                SoftShapes.glow(ctx, walkingX, sy + sh - 1, sw * 0.45f, 5, Theme.withAlpha(0xFF000000, Math.round(160 * skinA)));
                skinA = here;
                String name = minecraft.getUser().getName();
                centered(ctx, Theme.bold(name), sxC, sy - 14, 1f, a(WHITE, skinA));
                // wardrobe pill
                String label = "Wardrobe";
                int pw = Theme.boldWidth(label) + 30;
                int px = Math.round(sxC - pw / 2f);
                int py = sy + sh + 10;
                boolean hov = Theme.inside(mouseX, mouseY, px, py, px + pw, py + 18);
                float h = hoverAnim("cine:wardrobe", hov);
                glassPill(ctx, px, py, px + pw, py + 18, skinA, h);
                drawIcon(ctx, "cosmetics", px + 8, py + 5, 8, a(WHITE, skinA));
                ctx.drawString(font, Theme.bold(label), px + 20, py + 5, a(Theme.lerpColor(0xFFDADAE0, WHITE, h), skinA), false);
                areas.add(new Area(px, py, px + pw, py + 18,
                        () -> go(() -> minecraft.setScreen(new OnixMenuScreen(this, OnixMenuScreen.View.COSMETICS)))));
            }
        }
        super.render(ctx, mouseX, mouseY, delta); // the skin widget (Screen.render draws the background first)

        // ---- Essential-style bar top right: friends, wardrobe, McReal, screenshots, games, settings
        renderToolbar(ctx, mouseX, mouseY, smooth((since - 1f) / 0.6f));
        // tiny version line, nothing else in the corners
        float fa = smooth((since - 1.2f) / 0.6f);
        String version = OnixClient.NAME + " " + OnixClient.VERSION;
        Theme.small(ctx, version, width - 8 - Theme.smallWidth(version), height - 12, a(0xFF55555E, fa));
        renderFade(ctx, now);
    }

    private record Tool(String icon, String label, Runnable action) {}

    private void renderToolbar(GuiGraphics ctx, int mouseX, int mouseY, float alpha) {
        if (alpha <= 0.01f) return;
        List<dev.onix.client.social.Social.Friend> friends = dev.onix.client.social.Social.friends;
        int online = 0;
        int unread = 0;
        for (var f : friends) {
            if (f.online()) online++;
            unread += f.unread();
        }
        List<Tool> tools = List.of(
                new Tool("profile", online > 0 ? "Friends · " + online + " online" : "Friends",
                        () -> go(() -> minecraft.setScreen(new OnixMenuScreen(this, OnixMenuScreen.View.FRIENDS)))),
                new Tool("cosmetics", "Wardrobe", () -> go(() -> minecraft.setScreen(new OnixMenuScreen(this, OnixMenuScreen.View.COSMETICS)))),
                new Tool("camera", "McReal", () -> go(() -> minecraft.setScreen(new OnixMenuScreen(this, OnixMenuScreen.View.MCREAL)))),
                new Tool("visual", "Screenshots", () -> net.minecraft.util.Util.getPlatform().openFile(
                        new java.io.File(minecraft.gameDirectory, "screenshots"))),
                new Tool("star", "Mini Games", () -> go(() -> minecraft.setScreen(new OnixMenuScreen(this, OnixMenuScreen.View.GAMES)))),
                new Tool("settings", "Onix Settings", () -> go(() -> minecraft.setScreen(new OnixMenuScreen(this, OnixMenuScreen.View.SETTINGS)))));
        int size = 15;
        int gap = 1;
        int w = tools.size() * size + (tools.size() - 1) * gap + 8;
        int x0 = width - w - 8;
        int y0 = 8 - Math.round((1 - alpha) * 8);
        glassPill(ctx, x0, y0, x0 + w, y0 + size + 8, alpha, 0);
        String tip = null;
        int tipX = 0;
        for (int i = 0; i < tools.size(); i++) {
            Tool t = tools.get(i);
            int bx = x0 + 4 + i * (size + gap);
            int by = y0 + 4;
            boolean hov = Theme.inside(mouseX, mouseY, bx, by, bx + size, by + size);
            float h = hoverAnim("tool:" + t.icon(), hov);
            if (h > 0.01f) gg.carbon.client.ui.R2D.rrect(ctx, bx, by, size, size, size / 2f, a(0x26FFFFFF, h * alpha));
            drawIcon(ctx, t.icon(), bx + 4, by + 4, 7, a(Theme.lerpColor(0xFFB4B4BC, WHITE, h), alpha));
            if (i == 0 && unread > 0) {
                Theme.round(ctx, bx + size - 5, by + 1, bx + size - 1, by + 5, 2, a(0xFFFF4D5E, alpha));
            } else if (i == 0 && online > 0) {
                Theme.round(ctx, bx + size - 4, by + 2, bx + size - 1, by + 5, 1, a(0xFF34D399, alpha));
            }
            Runnable action = t.action();
            areas.add(new Area(bx, by, bx + size, by + size, action));
            if (hov) {
                tip = t.label();
                tipX = bx + size / 2;
            }
        }
        if (tip != null) {
            int tw = Theme.width(tip) + 12;
            int tx = Math.min(width - 6 - tw, tipX - tw / 2);
            int ty = y0 + size + 12;
            Theme.round(ctx, tx, ty, tx + tw, ty + 14, 7, a(0xE00B0B0E, alpha));
            Theme.text(ctx, tip, tx + 6, ty + 3, a(WHITE, alpha));
        }
    }

    private boolean skinTurned;

    /** Walk cycle while walking in (side view), then a smooth turn to the front and a calm idle. */
    private void poseSkin(boolean walking, float walkT, float stride, float phase, float time) {
        var acc = (dev.onix.client.mixin.PlayerSkinWidgetAccessor) skinWidget;
        float swing = (float) Math.cos(phase) * 0.9f * stride;
        float armIdle = walking ? 0f : (float) Math.sin(time * 1.2f) * 0.04f;
        for (var model : new net.minecraft.client.model.player.PlayerModel[]{acc.onix$wide(), acc.onix$slim()}) {
            model.rightLeg.xRot = swing;
            model.leftLeg.xRot = -swing;
            model.rightArm.xRot = -swing * 0.8f;
            model.leftArm.xRot = swing * 0.8f;
            model.rightArm.zRot = 0.05f + armIdle;
            model.leftArm.zRot = -0.05f - armIdle;
            model.head.xRot = 0f;
        }
        if (walking) {
            // facing the way they walk (to the left), turning towards you over the last steps
            float turn = smooth((walkT - 0.7f) / 0.3f);
            acc.onix$setRotationY(-90f + (30f + 90f) * turn);
            acc.onix$setRotationX(-5f);
            skinTurned = false;
        } else if (!skinTurned) {
            acc.onix$setRotationY(30f);
            skinTurned = true;
        }
    }

    /** Dark liquid glass: see-through black, light top edge, a little shine when hovered. */
    private void glassPill(GuiGraphics ctx, int x1, int y1, int x2, int y2, float alpha, float hover) {
        // drawn at the screen's real pixel size, so the round ends stay smooth
        float r = (y2 - y1) / 2f;
        gg.carbon.client.ui.R2D.rrect(ctx, x1, y1, x2 - x1, y2 - y1, r, a(Theme.lerpColor(0xA00C0C10, 0xC01E1E24, hover), alpha));
        gg.carbon.client.ui.R2D.outline(ctx, x1, y1, x2 - x1, y2 - y1, r, 0.75f, a(0x2EFFFFFF, alpha * (0.7f + 0.3f * hover)));
    }

    /** Black fade: in when the screen opens, out before a cut to the next screen. */
    private void renderFade(GuiGraphics ctx, long now) {
        float fade = 1 - smooth((now - shownAt) / 450f);
        if (pending != null) {
            float out = smooth((now - pendingAt) / 260f);
            fade = Math.max(fade, out);
            if (out >= 1f) {
                Runnable action = pending;
                pending = null;
                action.run();
            }
        }
        if (fade > 0.01f) ctx.fill(0, 0, width, height, Theme.withAlpha(0xFF000000, Math.round(255 * fade)));
    }

    // ================================================================== input

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent onix$e, boolean onix$dbl) {
        double mouseX = onix$e.x(); double mouseY = onix$e.y(); int button = onix$e.button();
        if (cinematic() && !entered) {
            enter();
            dragStartX = Double.NaN;
            return true;
        }
        if (button == 0) {
            dragOnSkin = skinWidget != null && skinWidget.visible && skinWidget.isMouseOver(mouseX, mouseY);
            dragStartX = dragOnSkin ? Double.NaN : mouseX;
            dragged = dragOnSkin;
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
        if (cinematic() && !entered) {
            enter();
            return true;
        }
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
