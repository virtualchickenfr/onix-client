package dev.onix.client.util;

/** Records recent frame times for FPS statistics and the frame graph. Fed once per rendered frame. */
public final class FrameStats {
    private static final int SIZE = 2048;
    private static final float[] FRAME_MS = new float[SIZE];
    private static final long[] STAMP = new long[SIZE];
    private static int head;
    private static int count;
    private static long last = System.nanoTime();

    private FrameStats() {}

    public static void onFrame() {
        long now = System.nanoTime();
        float ms = (now - last) / 1_000_000f;
        last = now;
        if (ms <= 0 || ms > 1000) return;
        FRAME_MS[head] = ms;
        STAMP[head] = now;
        head = (head + 1) % SIZE;
        if (count < SIZE) count++;
    }

    /** {min fps, max fps, average fps} over the last {@code seconds}. */
    public static float[] fps(double seconds) {
        long cutoff = System.nanoTime() - (long) (seconds * 1_000_000_000L);
        float min = Float.MAX_VALUE;
        float max = 0;
        double sum = 0;
        int n = 0;
        for (int i = 0; i < count; i++) {
            int idx = Math.floorMod(head - 1 - i, SIZE);
            if (STAMP[idx] < cutoff) break;
            float fps = 1000f / FRAME_MS[idx];
            min = Math.min(min, fps);
            max = Math.max(max, fps);
            sum += FRAME_MS[idx];
            n++;
        }
        if (n == 0) return new float[]{0, 0, 0};
        return new float[]{min, max, (float) (1000.0 / (sum / n))};
    }

    /** The newest {@code n} frame times in ms, oldest first. */
    public static float[] recent(int n) {
        n = Math.min(n, count);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) out[i] = FRAME_MS[Math.floorMod(head - n + i, SIZE)];
        return out;
    }
}
