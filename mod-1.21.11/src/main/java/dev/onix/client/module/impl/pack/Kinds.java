package dev.onix.client.module.impl.pack;

import dev.onix.client.gui.Notifications;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.module.impl.pack.Events.Event;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** The building blocks of the module packs: info lines and alerts. */
public final class Kinds {
    private Kinds() {}

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    // ================================================================== one text line

    public static class Info extends TextHudModule {
        private final String label;
        private final Supplier<String> value;

        public Info(String id, String name, String description, Category category, String label, Supplier<String> value, int slot, String... tags) {
            super(id, name, description, category, false, Anchor.TOP_LEFT, 4 + (slot / 12 % 4) * 96, 64 + (slot % 12) * 13);
            this.label = label;
            this.value = value;
            tags(tags);
        }

        private String safe() {
            if (mc().player == null || mc().level == null) return null;
            try {
                return value.get();
            } catch (RuntimeException e) {
                return null;
            }
        }

        @Override
        public boolean hasContent() {
            return safe() != null;
        }

        @Override
        protected void lines(List<Line> out, boolean preview) {
            String v = safe();
            out.add(new Line(label, v == null ? (preview ? "–" : "") : v));
        }
    }

    // ================================================================== alert

    public static final class Alert extends Module {
        private final BooleanSupplier condition;
        private final Supplier<String> text;
        private final BoolSetting sound = add(new BoolSetting("sound", "Sound", true));
        private final NumberSetting cooldown = add(new NumberSetting("cooldown", "Not Again For", 10, 0, 120, 5).unit("s")
                .describe("Waits this long before the same alert can show again."));
        private boolean was;
        private long last;

        public Alert(String id, String name, String description, Category category, BooleanSupplier condition, Supplier<String> text, String... tags) {
            super(id, name, description, category, false);
            this.condition = condition;
            this.text = text;
            tags(tags);
        }

        /** An alert for an event (fires each time it happens). */
        public static Alert of(String id, String name, String description, Category category, Event event, String text, String... tags) {
            boolean[] flag = new boolean[1];
            Events.on(event, (e, en) -> flag[0] = true);
            return new Alert(id, name, description, category, () -> {
                boolean f = flag[0];
                flag[0] = false;
                return f;
            }, () -> text, tags);
        }

        @Override
        public void onTick() {
            if (mc().player == null || mc().level == null) {
                was = false;
                return;
            }
            boolean now;
            try {
                now = condition.getAsBoolean();
            } catch (RuntimeException e) {
                now = false;
            }
            long t = System.currentTimeMillis();
            if (now && !was && t - last > cooldown.get() * 1000) {
                last = t;
                Notifications.show(getName(), text.get());
                if (sound.isOn()) mc().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.4f, 0.8f));
            }
            was = now;
        }
    }
}
