package dev.onix.client.module.impl.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.onix.client.OnixClient;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.TextSetting;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

public class WatermarkModule extends HudModule {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("onix", "textures/gui/logo.png");

    private final TextSetting text = add(new TextSetting("text", "Text", "Onix", 24));
    private final BoolSetting logo = add(new BoolSetting("logo", "Show Logo", true));
    private final BoolSetting version = add(new BoolSetting("version", "Show Version", false));
    private final BoolSetting fps = add(new BoolSetting("fps", "Show FPS", true));
    private final BoolSetting time = add(new BoolSetting("time", "Show Time", false));
    private final ColorSetting titleColor = add(new ColorSetting("titleColor", "Title Color", 0xFFFFFFFF));
    private final ColorSetting infoColor = add(new ColorSetting("infoColor", "Info Color", 0xFFA1A1AA));
    private final ColorSetting accentLine = add(ColorSetting.accent("accentLine", "Accent Line"));
    private final BoolSetting showLine = add(new BoolSetting("line", "Accent Line", true));

    public WatermarkModule() {
        super("watermark", "Watermark", "Client name with optional FPS and time.", Category.HUD, false, Anchor.TOP_LEFT, 4, 4);
        tags("logo", "branding");
    }

    private String info() {
        StringBuilder sb = new StringBuilder();
        if (version.isOn()) sb.append(" ").append(OnixClient.VERSION);
        if (fps.isOn()) sb.append("  ").append(mc().getFps()).append(" fps");
        if (time.isOn()) sb.append("  ").append(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
        return sb.toString();
    }

    @Override
    public int getWidth() {
        return pad() * 2 + (logo.isOn() ? 12 : 0) + textWidth(text.get()) + textWidth(info());
    }

    @Override
    public int getHeight() {
        return pad() * 2 + 9 + (showLine.isOn() ? 2 : 0);
    }

    @Override
    public void render(GuiGraphics ctx, boolean preview) {
        int w = getWidth();
        int h = getHeight();
        drawBackground(ctx, w, h);
        if (showLine.isOn()) ctx.fill(cornerRadius.getInt(), 0, w - cornerRadius.getInt(), 1, accentLine.color());
        int x = pad();
        int y = pad() + 1 + (showLine.isOn() ? 1 : 0);
        if (logo.isOn()) {
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO, x, y - 1, 0f, 0f, 9, 9, 128, 128, 128, 128);
            x += 12;
        }
        x = drawText(ctx, text.get(), x, y, titleColor.color());
        drawText(ctx, info(), x, y, infoColor.color());
    }
}
