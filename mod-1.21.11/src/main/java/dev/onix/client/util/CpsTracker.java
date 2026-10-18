package dev.onix.client.util;

import java.util.ArrayDeque;
import java.util.Deque;

/** Counts clicks within the last second. Fed by MouseMixin. */
public final class CpsTracker {
    private static final Deque<Long> LEFT = new ArrayDeque<>();
    private static final Deque<Long> RIGHT = new ArrayDeque<>();

    private CpsTracker() {}

    public static synchronized void click(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) LEFT.addLast(now);
        else if (button == 1) RIGHT.addLast(now);
    }

    public static synchronized int left() {
        return count(LEFT);
    }

    public static synchronized int right() {
        return count(RIGHT);
    }

    private static int count(Deque<Long> clicks) {
        long cutoff = System.currentTimeMillis() - 1000L;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
            clicks.pollFirst();
        }
        return clicks.size();
    }
}
