package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Locale;

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
        return isEnabled() && ownTag.isOn() && entity == mc().player && !mc().options.getPerspective().isFirstPerson();
    }

    public Text decorate(Entity entity, Text text) {
        if (!isEnabled() || !health.isOn() || !(entity instanceof LivingEntity living)) return text;
        if (onlyPlayers.isOn() && !(entity instanceof PlayerEntity)) return text;
        float hp = living.getHealth() + living.getAbsorptionAmount();
        String value = switch (healthFormat.get()) {
            case "HP" -> String.format(Locale.ROOT, "%.0f HP", hp);
            case "Percent" -> Math.round(100 * living.getHealth() / living.getMaxHealth()) + "%";
            default -> String.format(Locale.ROOT, "%.1f ❤", hp / 2f);
        };
        float ratio = living.getHealth() / living.getMaxHealth();
        Formatting color = !colorHealth.isOn() ? Formatting.WHITE : ratio > 0.6f ? Formatting.GREEN : ratio > 0.3f ? Formatting.GOLD : Formatting.RED;
        MutableText copy = text.copy();
        return copy.append(Text.literal(" " + value).formatted(color));
    }
}
