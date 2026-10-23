package dev.onix.client.mixin;

import dev.onix.client.render.SmallCaps;
import java.util.List;
import net.minecraft.client.gui.font.FontSet;
import com.mojang.blaze3d.font.GlyphProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Small Caps module: every glyph lookup (drawing and measuring) goes through here. */
@Mixin(FontSet.class)
public abstract class FontSetMixin {
    @Shadow
    private List<GlyphProvider> activeProviders;

    /** Which small capitals this font set has (see SmallCaps.map); cleared when the fonts reload. */
    @Unique
    private final byte[] onix$smallCapsKnown = new byte[26];

    @ModifyVariable(method = "getGlyph(I)Lnet/minecraft/client/gui/font/FontSet$SelectedGlyphs;", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int onix$smallCaps(int codepoint) {
        return SmallCaps.mode == 0 ? codepoint : SmallCaps.map(codepoint, activeProviders, onix$smallCapsKnown);
    }

    @Inject(method = "reload(Ljava/util/Set;)V", at = @At("TAIL"))
    private void onix$fontsReloaded(CallbackInfo ci) {
        java.util.Arrays.fill(onix$smallCapsKnown, (byte) 0);
    }
}
