package dev.onix.client.cosmetics;

/**
 * The shape of a 3D cape: a grid of points that bends away from the back, flutters and has soft folds.
 * Pure maths, the same as capeMesh() in launcher/src/js/cosrender.js, so previews match the game.
 *
 * Cape space (model pixels): x from +5 (left, seen from behind) to -5, y down from the shoulders,
 * z backwards. The vanilla swing (walking, falling, sneaking) is applied outside, as a rotation.
 */
public final class CapeMesh {
    public static final int NU = 8;
    public static final int NV = 12;
    public static final float WIDTH = 10f;
    public static final float HEIGHT = 16f;
    public static final float THICK = 1f;

    /** inner[j][i] / outer[j][i] = {x, y, z}; j = row from the top, i = column from x = +5. */
    public final float[][][] inner = new float[NV + 1][NU + 1][3];
    public final float[][][] outer = new float[NV + 1][NU + 1][3];

    private CapeMesh() {}

    private static double bend(double v, double t, double swing) {
        return v * v * (6 + 26 * swing) + Math.sin(t * 0.09 + v * 3.4) * (1.2 + 5 * swing) * v;
    }

    /** t = time in ticks, swing = 0 (standing) … 1 (running, falling). */
    public static CapeMesh build(double t, double swing) {
        CapeMesh m = new CapeMesh();
        double seg = HEIGHT / NV;
        double y = 0;
        double z = 0;
        for (int j = 0; j <= NV; j++) {
            double v = j / (double) NV;
            if (j > 0) {
                double a = Math.toRadians(bend((j - 0.5) / NV, t, swing));
                y += seg * Math.cos(a);
                z += seg * Math.sin(a);
            }
            double an = Math.toRadians(bend(v, t, swing));
            double ny = -Math.sin(an);
            double nz = Math.cos(an);
            for (int i = 0; i <= NU; i++) {
                double u = i / (double) NU;
                double d = v * (0.38 * Math.sin(u * Math.PI * 3 + 0.5) + Math.sin(t * 0.13 + u * 4.7 + v * 5.3) * (0.1 + 0.45 * swing));
                float[] in = m.inner[j][i];
                in[0] = (float) (WIDTH / 2 - WIDTH * u);
                in[1] = (float) (y + ny * d);
                in[2] = (float) (z + nz * d);
                float[] out = m.outer[j][i];
                out[0] = in[0];
                out[1] = (float) (in[1] + ny * THICK);
                out[2] = (float) (in[2] + nz * THICK);
            }
        }
        return m;
    }
}
