package dev.onix.client.cosmetics;

/**
 * Turns a cape pattern (20 x 32 palette indices) into the detailed 80 x 128 face: woven cloth, light from
 * above, raised edges between colours and, from Rare up, a stitched seam. Integer maths only, the same
 * as capeFinish() in launcher/src/js/cosrender.js.
 */
public final class CapeFinish {
    public static final int S = 4;
    public static final int W = CapePattern.W * S;
    public static final int H = CapePattern.H * S;

    private CapeFinish() {}

    /** ARGB pixels, W * H. tier: 0 common … 3 legendary. */
    public static int[] face(int[] px, int[] palette, int tier) {
        int pw = CapePattern.W;
        int ph = CapePattern.H;
        int[] out = new int[W * H];
        int stitch = palette[Math.min(tier >= 2 ? 8 : 2, palette.length - 1)];
        for (int Y = 0; Y < H; Y++) {
            for (int X = 0; X < W; X++) {
                int x = X / S;
                int y = Y / S;
                int idx = px[y * pw + x];
                int c = palette[idx];
                int k = 1000;
                if (((X + (Y >> 1)) & 3) == 0) k -= 40;          // twill weave
                else if (((X - Y) & 7) == 0) k += 30;
                k -= Y * 80 / (H - 1);                            // light from above
                if (X % S == S - 1 && x < pw - 1 && px[y * pw + x + 1] != idx) k -= 140;   // raised edges
                if (Y % S == S - 1 && y < ph - 1 && px[(y + 1) * pw + x] != idx) k -= 140;
                if (X % S == 0 && x > 0 && px[y * pw + x - 1] != idx) k += 90;
                if (Y % S == 0 && y > 0 && px[(y - 1) * pw + x] != idx) k += 90;
                int r = Math.min(255, ((c >> 16) & 0xFF) * k / 1000);
                int g = Math.min(255, ((c >> 8) & 0xFF) * k / 1000);
                int b = Math.min(255, (c & 0xFF) * k / 1000);
                if (tier >= 1) {
                    boolean side = (X == 3 || X == W - 4) && Y >= 3 && Y <= H - 4;
                    boolean across = (Y == 3 || Y == H - 4) && X >= 3 && X <= W - 4;
                    int pos = side ? Y : X;
                    if ((side || across) && ((pos >> 1) & 1) == 0) {
                        r = (r + ((stitch >> 16) & 0xFF)) >> 1;
                        g = (g + ((stitch >> 8) & 0xFF)) >> 1;
                        b = (b + (stitch & 0xFF)) >> 1;
                    }
                }
                out[Y * W + X] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return out;
    }
}
