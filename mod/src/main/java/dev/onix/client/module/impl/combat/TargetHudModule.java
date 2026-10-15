package dev.onix.client.module.impl.combat;

import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.HudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.CombatTracker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.Locale;

/** Shows who you are fighting: head, name, health bar, armor and distance. */
public class TargetHudModule extends HudModule {
    private final NumberSetting timeout = add(new NumberSetting("timeout", "Hide After", 4, 1, 20, 1).unit("s"));
    private final BoolSetting head = add(new BoolSetting("head", "Player Head", true));
    private final BoolSetting healthText = add(new BoolSetting("healthText", "Health Number", true));
    private final ModeSetting healthUnit = add(new ModeSetting("healthUnit", "Health Unit", "HP", "Hearts").visibleIf(healthText::isOn));
    private final BoolSetting armor = add(new BoolSetting("armor", "Armor Points", true));
    private final BoolSetting distance = add(new BoolSetting("distance", "Distance", true));
    private final BoolSetting animate = add(new BoolSetting("animate", "Animate Health Bar", true));

    {
        group("Colors");
    }

    private final ModeSetting barColorMode = add(new ModeSetting("barMode", "Bar Color", "By Health", "Custom"));
    private final ColorSetting barColor = add(ColorSetting.accent("barColor", "Custom Bar Color").visibleIf(() -> barColorMode.is("Custom")));
    private final ColorSetting barBackground = add(new ColorSetting("barBg", "Bar Background", 0x40FFFFFF));
    private final ColorSetting nameColor = add(new ColorSetting("nameColor", "Name Color", 0xFFFFFFFF));
    private final ColorSetting infoColor = add(new ColorSetting("infoColor", "Info Color", 0xFFA1A1AA));

    private float shownHealth = -1;

    public TargetHudModule() {
        super("targethud", "Target HUD", "Health and info about the player you are fighting.", Category.COMBAT, false, Anchor.CENTER, 30, 30);
        restricted("Showing your opponent's exact health is not allowed on some servers.");
        tags("enemy", "opponent", "target info");
    }

    private LivingEntity target() {
        Entity e = CombatTracker.target();
        if (!(e instanceof LivingEntity living) || living.isRemoved()) return null;
        if (System.currentTimeMillis() - CombatTracker.lastHitTime() > timeout.get() * 1000) return null;
        return living;
    }

    @Override
    public boolean hasContent() {
        return target() != null;
    }

    @Override
    public int getWidth() {
        return 130 + pad() * 2;
    }

    @Override
    public int getHeight() {
        return 32 + pad() * 2;
    }

    @Override
    public void render(DrawContext ctx, boolean preview) {
        LivingEntity t = target();
        if (t == null && !preview) return;
        int w = getWidth();
        int h = getHeight();
        drawBackground(ctx, w, h);
        String name = t == null ? "Opponent" : t.getName().getString();
        float hp = t == null ? 14 : t.getHealth() + t.getAbsorptionAmount();
        float max = t == null ? 20 : t.getMaxHealth();
        if (shownHealth < 0 || !animate.isOn()) shownHealth = hp;
        shownHealth += (hp - shownHealth) * 0.2f;

        int x = pad();
        int y = pad();
        if (head.isOn()) {
            if (t instanceof AbstractClientPlayerEntity player) {
                PlayerSkinDrawer.draw(ctx, player.getSkinTextures(), x, y, 32);
            } else {
                Theme.round(ctx, x, y, x + 32, y + 32, 4, 0x30FFFFFF);
                drawText(ctx, name.substring(0, 1).toUpperCase(Locale.ROOT), x + 13, y + 12, nameColor.color());
            }
            x += 37;
        }
        int right = w - pad();
        drawText(ctx, name, x, y + 1, nameColor.color());
        StringBuilder info = new StringBuilder();
        if (healthText.isOn()) {
            float div = healthUnit.is("Hearts") ? 2 : 1;
            info.append(String.format(Locale.ROOT, "%.1f", hp / div)).append(healthUnit.is("Hearts") ? " ❤" : " HP");
        }
        if (armor.isOn() && t != null) info.append(info.length() > 0 ? " · " : "").append(t.getArmor()).append(" armor");
        if (distance.isOn() && t != null && mc().player != null) {
            info.append(info.length() > 0 ? " · " : "").append(String.format(Locale.ROOT, "%.1fm", mc().player.distanceTo(t)));
        }
        drawText(ctx, info.toString(), x, y + 12, infoColor.color());

        int barY = y + 25;
        Theme.round(ctx, x, barY, right, barY + 5, 2, barBackground.color());
        float ratio = Math.max(0, Math.min(1, shownHealth / max));
        int bar = barColorMode.is("Custom") ? barColor.color()
                : ratio > 0.6f ? 0xFF34D399 : ratio > 0.3f ? 0xFFFACC15 : 0xFFFF5A5F;
        Theme.round(ctx, x, barY, x + Math.max(4, Math.round((right - x) * ratio)), barY + 5, 2, bar);
    }
}
