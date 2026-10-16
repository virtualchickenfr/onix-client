package dev.onix.client.module.impl.combat;

import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.module.OverlayRenderer;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** A red vignette (and optional heartbeat) when your health is low. */
public class LowHealthWarningModule extends Module implements OverlayRenderer {
    private final NumberSetting threshold = add(new NumberSetting("threshold", "Below Health", 6, 1, 19, 1).unit("HP"));
    private final ColorSetting color = add(new ColorSetting("color", "Vignette Color", 0xFFFF2A2A));
    private final NumberSetting intensity = add(new NumberSetting("intensity", "Intensity", 60, 10, 100, 5).unit("%"));
    private final NumberSetting thickness = add(new NumberSetting("thickness", "Edge Size", 30, 10, 60, 5).unit("%"));
    private final BoolSetting pulse = add(new BoolSetting("pulse", "Pulse", true));
    private final BoolSetting heartbeat = add(new BoolSetting("heartbeat", "Heartbeat Sound", false));
    private final NumberSetting volume = add(new NumberSetting("volume", "Sound Volume", 50, 0, 100, 5).unit("%").visibleIf(heartbeat::isOn));

    private int beatTimer;

    public LowHealthWarningModule() {
        super("lowhealth", "Low Health Warning", "Red screen edges when you are about to die.", Category.COMBAT, false);
        tags("vignette", "danger", "hp");
    }

    private float danger() {
        if (mc().player == null || mc().player.isDeadOrDying()) return 0;
        float hp = mc().player.getHealth();
        if (hp > threshold.get()) return 0;
        return 1f - (hp - 1) / Math.max(1f, threshold.getFloat());
    }

    @Override
    public void onTick() {
        float d = danger();
        if (d <= 0 || !heartbeat.isOn()) return;
        if (--beatTimer <= 0) {
            beatTimer = Math.max(10, Math.round(30 - d * 18));
            mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 0.7f, volume.getFloat() / 100f));
        }
    }

    @Override
    public void renderOverlay(GuiGraphics ctx) {
        float d = danger();
        if (d <= 0) return;
        float a = intensity.getFloat() / 100f * (0.5f + 0.5f * d);
        if (pulse.isOn()) a *= 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 180.0);
        Theme.vignette(ctx, color.color(), a, thickness.getFloat() / 100f);
    }
}
