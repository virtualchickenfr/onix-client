package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Better name tags: your own name in third person and health next to player names. */
public class NametagsModule extends Module {
    private final BoolSetting ownTag = add(new BoolSetting("own", "Own Name in Third Person", true));
    private final BoolSetting health = add(new BoolSetting("health", "Show Health", true));
    private final ModeSetting healthFormat = add(new ModeSetting("format", "Health Format", "Hearts", "HP", "Percent").visibleIf(health::isOn));
    private final BoolSetting colorHealth = add(new BoolSetting("color", "Color by Health", true).visibleIf(health::isOn));
    private final BoolSetting onlyPlayers = add(new BoolSetting("onlyPlayers", "Health Only on Players", true).visibleIf(health::isOn));

    public NametagsModule() {
        super("nametags", "Nametags", "Your own nametag in F5 and health next to names.", Category.VISUAL, false);
        restricted("Health next to names is not allowed on some servers.");
        tags("name tag", "health indicator");
    }

    public boolean showOwn(Entity entity) {
        return isEnabled() && ownTag.isOn() && entity == mc().player && !mc().options.getCameraType().isFirstPerson();
    }

    public Component decorate(Entity entity, Component text) {
        if (!isEnabled() || !health.isOn() || !(entity instanceof LivingEntity living)) return text;
        if (onlyPlayers.isOn() && !(entity instanceof Player)) return text;
        float hp = living.getHealth() + living.getAbsorptionAmount();
        String value = switch (healthFormat.get()) {
            case "HP" -> String.format(Locale.ROOT, "%.0f HP", hp);
            case "Percent" -> Math.round(100 * living.getHealth() / living.getMaxHealth()) + "%";
            default -> String.format(Locale.ROOT, "%.1f ❤", hp / 2f);
        };
        float ratio = living.getHealth() / living.getMaxHealth();
        ChatFormatting color = !colorHealth.isOn() ? ChatFormatting.WHITE : ratio > 0.6f ? ChatFormatting.GREEN : ratio > 0.3f ? ChatFormatting.GOLD : ChatFormatting.RED;
        MutableComponent copy = text.copy();
        return copy.append(Component.literal(" " + value).withStyle(color));
    }
}
