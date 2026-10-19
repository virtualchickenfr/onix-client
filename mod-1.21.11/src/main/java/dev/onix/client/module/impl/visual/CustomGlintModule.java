package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.onix.client.OnixClient;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.render.FxDraw;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Recolours the enchantment glint on items and armour. */
public class CustomGlintModule extends Module {
    private final ColorSetting color = add(ColorSetting.accent("color", "Color"));
    private final BoolSetting rainbow = add(new BoolSetting("rainbow", "Rainbow", false).describe("Cycles through all colours."));
    private final NumberSetting brightness = add(new NumberSetting("brightness", "Brightness", 1.0, 0.3, 2.0, 0.05).unit("x"));
    private final BoolSetting armor = add(new BoolSetting("armor", "Armor Too", true));

    private static final Identifier[] IDS = {ItemRenderer.ENCHANTED_GLINT_ITEM, ItemRenderer.ENCHANTED_GLINT_ARMOR};
    private final NativeImage[] originals = new NativeImage[2];
    private int applied = 0;
    private int tick;

    public CustomGlintModule() {
        super("customglint", "Custom Glint", "Give the enchantment glint your own colour.", Category.VISUAL, false);
        tags("glint", "enchant", "color", "cosmetic");
    }

    /** Our tinted copy of a glint texture (so it can be told apart from the vanilla one). */
    private static final class Tinted extends DynamicTexture {
        Tinted(NativeImage image) {
            super(() -> "onix glint", image);
            this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
        }
    }

    private int target() {
        int c = rainbow.isOn() ? FxDraw.rainbow((System.currentTimeMillis() % 8000) / 8000f) : color.color();
        int b = (int) Math.round(brightness.get() * 100);
        return (c & 0xFFFFFF) ^ (b << 24);
    }

    @Override
    public void onTick() {
        // rainbow refreshes a few times a second; otherwise only when the colour changed or a resource reload dropped it
        if (++tick % (rainbow.isOn() ? 4 : 20) != 0) return;
        int want = target();
        boolean ours = Minecraft.getInstance().getTextureManager().getTexture(IDS[0]) instanceof Tinted;
        if (want != applied || !ours) apply();
    }

    @Override
    protected void onDisable() {
        restore();
    }

    private NativeImage original(int i) throws java.io.IOException {
        if (originals[i] == null) {
            var res = Minecraft.getInstance().getResourceManager().getResource(IDS[i]);
            if (res.isEmpty()) return null;
            try (InputStream in = res.get().open()) {
                originals[i] = NativeImage.read(in);
            }
        }
        return originals[i];
    }

    private void apply() {
        int c = rainbow.isOn() ? FxDraw.rainbow((System.currentTimeMillis() % 8000) / 8000f) : color.color();
        float r = ((c >> 16) & 0xFF) / 255f, g = ((c >> 8) & 0xFF) / 255f, b = (c & 0xFF) / 255f;
        float k = brightness.getFloat();
        try {
            for (int i = 0; i < IDS.length; i++) {
                if (i == 1 && !armor.isOn()) {
                    Minecraft.getInstance().getTextureManager().release(IDS[1]);
                    continue;
                }
                NativeImage src = original(i);
                if (src == null) continue;
                NativeImage out = new NativeImage(src.getWidth(), src.getHeight(), true);
                for (int y = 0; y < src.getHeight(); y++) {
                    for (int x = 0; x < src.getWidth(); x++) {
                        int p = src.getPixel(x, y); // ARGB
                        int sr = (p >> 16) & 0xFF, sg = (p >> 8) & 0xFF, sb = p & 0xFF;
                        float lum = Math.min(1f, (Math.max(sr, Math.max(sg, sb)) / 255f) * k);
                        int nr = Math.min(255, Math.round(lum * r * 255));
                        int ng = Math.min(255, Math.round(lum * g * 255));
                        int nb = Math.min(255, Math.round(lum * b * 255));
                        out.setPixel(x, y, (p & 0xFF000000) | nr << 16 | ng << 8 | nb);
                    }
                }
                Minecraft.getInstance().getTextureManager().register(IDS[i], new Tinted(out));
            }
            applied = target();
        } catch (Exception e) {
            OnixClient.LOGGER.warn("Custom Glint could not tint the glint texture", e);
        }
    }

    private void restore() {
        for (Identifier id : IDS) {
            AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(id);
            if (t instanceof Tinted) Minecraft.getInstance().getTextureManager().release(id);
        }
        applied = 0;
    }
}
