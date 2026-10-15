package dev.onix.client.module.impl.hud;

import dev.onix.client.module.Category;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;

import java.util.List;
import java.util.Locale;

public class FallDistanceModule extends TextHudModule {
    private final BoolSetting onlyFalling = add(new BoolSetting("onlyFalling", "Only While Falling", true));
    private final NumberSetting minimum = add(new NumberSetting("min", "Show From", 2, 0, 20, 0.5).unit("blocks").visibleIf(onlyFalling::isOn));
    private final NumberSetting danger = add(new NumberSetting("danger", "Damage From", 3, 1, 40, 0.5).unit("blocks")
            .describe("Fall damage starts after 3 blocks."));
    private final ColorSetting dangerColor = add(new ColorSetting("dangerColor", "Danger Color", 0xFFFF5A5F));

    public FallDistanceModule() {
        super("falldistance", "Fall Distance", "How far you have fallen – turns red when it will hurt.", Category.HUD, false, Anchor.CENTER, 0, 30);
        tags("fall damage", "mlg");
    }

    private float distance() {
        return mc().player == null ? 0 : mc().player.fallDistance;
    }

    @Override
    public boolean hasContent() {
        return !onlyFalling.isOn() || distance() >= minimum.get();
    }

    @Override
    protected void lines(List<Line> out, boolean preview) {
        float d = preview && distance() == 0 ? 7.5f : distance();
        out.add(new Line("Fall", String.format(Locale.ROOT, "%.1f", d), d > danger.get() ? dangerColor.color() : 0));
    }

    @Override
    protected String sampleValue() {
        return "00.0";
    }
}
