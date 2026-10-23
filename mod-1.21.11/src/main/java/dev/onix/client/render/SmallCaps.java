package dev.onix.client.render;

import java.util.List;
import com.mojang.blaze3d.font.GlyphProvider;

/**
 * Small caps for every text: letters are swapped for their small capital forms (ᴀʙᴄ…) right where the font looks
 * glyphs up, so widths, wrapping and rendering all agree. Set by {@link dev.onix.client.module.impl.visual.SmallCapsModule}.
 */
public final class SmallCaps {
    /** a–z → small capitals; x has none and stays. */
    private static final int[] SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ".codePoints().toArray();

    /** 0 = off, 1 = small letters only, 2 = capitals too. */
    public static volatile int mode;

    private SmallCaps() {}

    /**
     * The codepoint to look up instead of cp, if this font set can draw small caps. known caches per font set and
     * letter whether it can (0 = not checked yet, 1 = yes, 2 = no), so the providers are asked once.
     */
    public static int map(int cp, List<GlyphProvider> providers, byte[] known) {
        int m = mode;
        if (m == 0) return cp;
        int index;
        if (cp >= 'a' && cp <= 'z') index = cp - 'a';
        else if (m == 2 && cp >= 'A' && cp <= 'Z') index = cp - 'A';
        else return cp;
        int to = SMALL[index];
        if (to == cp) return cp;
        // fonts without these glyphs (the enchanting table font, custom pack fonts) keep their letters
        if (known[index] == 0) {
            known[index] = 2;
            for (GlyphProvider p : providers) {
                if (p.getGlyph(to) != null) {
                    known[index] = 1;
                    break;
                }
            }
        }
        return known[index] == 1 ? to : cp;
    }
}
