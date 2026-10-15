package dev.onix.client.util;

import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every chat line passes through here before it is shown (see ChatHudMixin).
 * Processors may change a message or hide it by returning null.
 */
public final class ChatPipeline {
    public interface Processor {
        /** Lower runs first. Filters ~0, decoration ~50, timestamps ~100. */
        int priority();

        /** @return the (possibly changed) message, or null to hide it. */
        Text process(Text message);
    }

    private static final List<Processor> PROCESSORS = new ArrayList<>();

    private ChatPipeline() {}

    public static void register(Processor processor) {
        PROCESSORS.add(processor);
        PROCESSORS.sort(Comparator.comparingInt(Processor::priority));
    }

    public static Text process(Text message) {
        Text current = message;
        for (Processor p : PROCESSORS) {
            try {
                current = p.process(current);
            } catch (Exception e) {
                // never lose a chat line because of a module
            }
            if (current == null) return null;
        }
        return current;
    }
}
