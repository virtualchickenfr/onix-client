package dev.onix.client.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Chat messages you send pass through here before they go to the server (prefix, suffix, grammar, emoticons,
 * calculator). Commands (starting with /) are never changed. A processor may return null to keep a message
 * on your side only (the calculator shows its result locally).
 */
public final class OutgoingChat {
    private OutgoingChat() {}

    private record Entry(int priority, UnaryOperator<String> fn) {}

    private static final List<Entry> PROCESSORS = new ArrayList<>();

    public static void register(int priority, UnaryOperator<String> fn) {
        PROCESSORS.add(new Entry(priority, fn));
        PROCESSORS.sort(Comparator.comparingInt(Entry::priority));
    }

    public static String process(String message) {
        if (message == null || message.startsWith("/")) return message;
        String current = message;
        for (Entry e : PROCESSORS) {
            try {
                current = e.fn().apply(current);
            } catch (RuntimeException ignored) {
                // a broken module must never eat a message
            }
            if (current == null) return null;
        }
        return current;
    }
}
