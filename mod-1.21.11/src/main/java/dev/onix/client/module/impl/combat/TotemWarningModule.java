package dev.onix.client.module.impl.combat;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.setting.TextSetting;
import dev.onix.client.util.InventoryUtil;
import java.util.List;
import net.minecraft.world.item.Items;

/** Warns when you have totems but none in your off hand. */
public class TotemWarningModule extends TextHudModule {
    private final TextSetting message = add(new TextSetting("message", "Message", "No totem in off hand!", 40));
    private final BoolSetting onlyWithTotems = add(new BoolSetting("onlyWith", "Only If You Carry Totems", true));
    private final NumberSetting belowHealth = add(new NumberSetting("belowHealth", "Only Below Health", 20, 1, 20, 1).unit("HP")
            .describe("20 = always warn."));
    private final BoolSetting blink = add(new BoolSetting("blink", "Blink", true));
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFACC15));

    public TotemWarningModule() {
        super("totemwarning", "Totem Warning", "Reminds you to put a totem in your off hand.", Category.COMBAT, false, Anchor.CENTER, 0, 20);
        tags("totem", "offhand", "crystal pvp");
        labelStyle.withDefault("Value Only");
        background.withDefault(false);
    }

    @Override
    public boolean hasContent() {
        if (mc().player == null) return false;
        if (mc().player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return false;
        if (onlyWithTotems.isOn() && InventoryUtil.count(Items.TOTEM_OF_UNDYING) == 0) return false;
        return mc().player.getHealth() <= belowHealth.get();
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        if (!preview && blink.isOn() && (System.currentTimeMillis() / 400) % 2 == 0) {
            out.add(new Line("Totem", " "));
            return;
        }
        out.add(new Line("Totem", message.get(), color.color()));
    }
}
