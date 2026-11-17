package dev.onix.client.module.impl.combat;

import com.mojang.blaze3d.platform.NativeImage;
import dev.onix.client.mixin.OverlayTextureAccessor;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.ColorSetting;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Hit Color: the colour players and mobs flash when they get hit (vanilla: red). Repaints the hurt half of the
 * game's entity overlay texture, so it costs nothing while playing.
 */
public class HitColorModule extends Module {
    private static final int VANILLA = 0xB2FF0000;

    private final ColorSetting color = add(new ColorSetting("color", "Hit Color", 0x99FF4FD8).describe("The alpha sets how strong the tint is."));
    private int applied = VANILLA;

    public HitColorModule() {
        super("hitcolor", "Hit Color", "Pick the colour entities flash when they get hit.", Category.COMBAT, false);
        tags("hurt color", "damage tint", "red", "hit");
    }

    @Override
    public void onTick() {
        apply(color.color());
    }

    @Override
    protected void onDisable() {
        apply(VANILLA);
    }

    private void apply(int argb) {
        if (argb == applied || mc().gameRenderer == null) return;
        DynamicTexture texture = ((OverlayTextureAccessor) mc().gameRenderer.overlayTexture()).onix$texture();
        NativeImage pixels = texture.getPixels();
        if (pixels == null) return;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) pixels.setPixel(x, y, argb);
        }
        texture.upload();
        applied = argb;
    }
}
