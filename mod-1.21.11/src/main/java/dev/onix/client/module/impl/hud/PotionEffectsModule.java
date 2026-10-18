package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class PotionEffectsModule extends HudModule {
    private static final String[] ROMAN = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

    private final BoolSetting icons = add(new BoolSetting("icons", "Show Icons", true));
    private final BoolSetting names = add(new BoolSetting("names", "Show Names", true));
    private final BoolSetting level = add(new BoolSetting("level", "Show Level", true).visibleIf(names::isOn));
    private final BoolSetting duration = add(new BoolSetting("duration", "Show Duration", true));
    private final ModeSetting sort = add(new ModeSetting("sort", "Sort By", "Duration", "Name", "None"));
    private final NumberSetting maxShown = add(new NumberSetting("max", "Max Shown", 8, 1, 20, 1));
    private final BoolSetting hideAmbient = add(new BoolSetting("hideAmbient", "Hide Beacon Effects", false));
    private final BoolSetting hideInfinite = add(new BoolSetting("hideInfinite", "Hide Infinite Effects", false));
    private final BoolSetting blink = add(new BoolSetting("blink", "Blink Before Ending", true));
    private final NumberSetting blinkSeconds = add(new NumberSetting("blinkSeconds", "Blink From", 10, 3, 60, 1).unit("s").visibleIf(blink::isOn));

    {
        group("Colors");
    }

    private final ModeSetting nameColorMode = add(new ModeSetting("nameColorMode", "Name Color", "Effect Color", "Custom"));
    private final ColorSetting nameColor = add(new ColorSetting("nameColor", "Custom Name Color", 0xFFFFFFFF).visibleIf(() -> nameColorMode.is("Custom")));
    private final ColorSetting durationColor = add(new ColorSetting("durationColor", "Duration Color", 0xFFA1A1AA));
    private final ColorSetting endingColor = add(new ColorSetting("endingColor", "Ending Color", 0xFFFF5A5F));

    public PotionEffectsModule() {
        super("effects", "Potion Effects", "Active effects with level and time left.", Category.HUD, true, Anchor.MIDDLE_RIGHT, -4, 60);
        tags("potions", "status effects", "buffs");
    }

    private List<MobEffectInstance> effects(boolean preview) {
        List<MobEffectInstance> list = new ArrayList<>();
        if (mc().player != null) {
            for (MobEffectInstance e : mc().player.getActiveEffects()) {
                if (hideAmbient.isOn() && e.isAmbient()) continue;
                if (hideInfinite.isOn() && e.isInfiniteDuration()) continue;
                list.add(e);
            }
        }
        if (list.isEmpty() && preview) {
            list.add(new MobEffectInstance(MobEffects.SPEED, 20 * 83, 1));
            list.add(new MobEffectInstance(MobEffects.STRENGTH, 20 * 9, 0));
        }
        switch (sort.get()) {
            case "Duration" -> list.sort(Comparator.comparingInt(e -> e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration()));
            case "Name" -> list.sort(Comparator.comparing(e -> e.getEffect().value().getDisplayName().getString()));
            default -> {}
        }
        return list.size() > maxShown.getInt() ? list.subList(0, maxShown.getInt()) : list;
    }

    private String name(MobEffectInstance e) {
        int amp = e.getAmplifier();
        String base = e.getEffect().value().getDisplayName().getString();
        if (!level.isOn() || amp == 0) return base;
        return base + " " + (amp < ROMAN.length ? ROMAN[amp] : String.valueOf(amp + 1));
    }

    private static String time(MobEffectInstance e) {
        if (e.isInfiniteDuration()) return "∞";
        int s = e.getDuration() / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    private int rowHeight() {
        return icons.isOn() ? 20 : (names.isOn() && duration.isOn() ? 20 : 11);
    }

    @Override
    public boolean hasContent() {
        return !effects(false).isEmpty();
    }

    @Override
    public int getWidth() {
        int text = 0;
        for (MobEffectInstance e : effects(true)) {
            int w = 0;
            if (names.isOn()) w = textWidth(name(e));
            if (duration.isOn()) w = Math.max(w, textWidth(time(e)));
            text = Math.max(text, w);
        }
        return pad() * 2 + (icons.isOn() ? 21 : 0) + Math.max(text, 20);
    }

    @Override
    public int getHeight() {
        return pad() * 2 + Math.max(1, effects(true).size()) * rowHeight();
    }

    @Override
    public void render(GuiGraphics ctx, boolean preview) {
        List<MobEffectInstance> list = effects(preview);
        drawBackground(ctx, getWidth(), getHeight());
        int y = pad();
        for (MobEffectInstance e : list) {
            boolean ending = !e.isInfiniteDuration() && e.getDuration() < blinkSeconds.get() * 20;
            if (blink.isOn() && ending && (System.currentTimeMillis() / 350) % 2 == 0) {
                y += rowHeight();
                continue;
            }
            int x = pad();
            if (icons.isOn()) {
                ctx.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, net.minecraft.client.gui.Gui.getMobEffectSprite(e.getEffect()), x, y + 1, 18, 18);
                x += 21;
            }
            int textY = y + (rowHeight() == 20 && !(names.isOn() && duration.isOn()) ? 6 : 1);
            if (names.isOn()) {
                int c = nameColorMode.is("Custom") ? nameColor.color() : 0xFF000000 | e.getEffect().value().getColor();
                drawText(ctx, name(e), x, textY, c);
                textY += 10;
            }
            if (duration.isOn()) drawText(ctx, time(e), x, textY, ending ? endingColor.color() : durationColor.color());
            y += rowHeight();
        }
    }
}
