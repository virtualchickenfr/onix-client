package dev.onix.client.util;

/** Mouse movement for Mousestrokes, fed by MouseMixin (onMove). Values decay quickly once the mouse stops. */
public final class MouseMotion {
    private static double lastX = Double.NaN, lastY = Double.NaN;
    private static double dx, dy;
    private static long lastMove;

    private MouseMotion() {}

    public static void onMove(double x, double y) {
        if (!Double.isNaN(lastX)) {
            dx = x - lastX;
            dy = y - lastY;
            lastMove = System.nanoTime();
        }
        lastX = x;
        lastY = y;
    }

    private static double fade() {
        double age = (System.nanoTime() - lastMove) / 1e9;
        return age > 0.08 ? 0 : 1;
    }

    public static double dx() {
        return dx * fade();
    }

    public static double dy() {
        return dy * fade();
    }
}
