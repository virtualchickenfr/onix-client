package dev.onix.client.module.impl.pack;

import dev.onix.client.gui.Notifications;
import dev.onix.client.gui.SoftShapes;
import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.ItemCounterModule;
import dev.onix.client.module.Module;
import dev.onix.client.module.OverlayRenderer;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.module.impl.pack.Events.Event;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.InventoryUtil;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The building blocks of the module packs: counters, info lines, alerts, event stats and screen effects. */
public final class Kinds {
    private Kinds() {}

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    // ================================================================== item counter

    public static final class Counter extends ItemCounterModule {
        private final Item icon;
        private final Predicate<ItemStack> match;

        public Counter(String id, String name, String description, Item icon, Predicate<ItemStack> match, int slot, String... tags) {
            super(id, name, description, Anchor.BOTTOM_RIGHT, -4 - (slot / 8 % 6) * 34, -120 - (slot % 8) * 14);
            this.icon = icon;
            this.match = match;
            tags(tags);
        }

        @Override
        protected Item icon() {
            return icon;
        }

        @Override
        protected int count() {
            return mc().player == null ? 0 : InventoryUtil.count(match);
        }
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

    // ================================================================== statistics of an event

    public enum StatKind { SESSION, PER_MINUTE, SINCE }

    public static final class Stat extends Info {
        public Stat(String id, String name, String description, Category category, String label, Event event, StatKind kind, int slot, String... tags) {
            super(id, name, description, category, label, () -> switch (kind) {
                case SESSION -> String.valueOf(Events.session(event));
                case PER_MINUTE -> Events.perMinute(event) + "/min";
                case SINCE -> {
                    long ms = Events.since(event);
                    yield ms < 0 ? "–" : ms < 60_000 ? (ms / 1000) + "s" : (ms / 60_000) + "m " + (ms / 1000 % 60) + "s";
                }
            }, slot, tags);
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

    // ================================================================== screen effects

    public enum Style { FLASH, VIGNETTE, TEXT, HITMARKER, RING, CONFETTI, SPEEDLINES, SYMBOLS, PARTICLES, SOUND, SHAKE_TEXT, BORDER }

    /** Something you see (and maybe hear) when an event happens: flashes, words, hit markers, confetti, particles … */
    public static final class Effect extends Module implements OverlayRenderer {
        private static final Random RANDOM = new Random();
        private final Event event;
        private final Style style;
        private final String text;
        private final SimpleParticleType particle;
        private final SoundEvent soundEvent;
        private final ColorSetting color;
        private final NumberSetting strength = add(new NumberSetting("strength", "Strength", 1, 0.2, 2, 0.1).unit("x"));
        private final BoolSetting sound;
        private long start = -1;
        private Entity at;
        private final float[][] pieces = new float[60][5];

        public Effect(String id, String name, String description, Category category, Event event, Style style, int argb, String text,
                      SimpleParticleType particle, SoundEvent soundEvent, String... tags) {
            super(id, name, description, category, false);
            this.event = event;
            this.style = style;
            this.text = text;
            this.particle = particle;
            this.soundEvent = soundEvent;
            this.color = add(new ColorSetting("color", "Color", argb));
            this.sound = soundEvent == null ? null : add(new BoolSetting("sound", "Sound", true));
            tags(tags);
            tags("effect", "tiktok", "visual", event.label.toLowerCase(Locale.ROOT));
            Events.on(event, (e, entity) -> {
                if (isEnabled()) trigger(entity);
            });
        }

        private void trigger(Entity entity) {
            start = System.currentTimeMillis();
            at = entity;
            for (float[] p : pieces) {
                p[0] = RANDOM.nextFloat();                 // x 0..1
                p[1] = 0.4f + RANDOM.nextFloat() * 0.8f;   // fall speed
                p[2] = (RANDOM.nextFloat() - 0.5f) * 0.3f; // drift
                p[3] = RANDOM.nextFloat();                 // hue
                p[4] = RANDOM.nextFloat() * 6.28f;         // phase
            }
            if (sound != null && sound.isOn()) mc().getSoundManager().play(SimpleSoundInstance.forUI(soundEvent, 1f, 0.9f));
            if (style == Style.PARTICLES && particle != null && mc().level != null) {
                Entity e = entity != null ? entity : mc().player;
                int n = Math.round(14 * strength.getFloat());
                for (int i = 0; i < n; i++) {
                    mc().level.addParticle(particle, e.getX() + (RANDOM.nextDouble() - 0.5) * e.getBbWidth() * 1.6,
                            e.getY() + RANDOM.nextDouble() * e.getBbHeight(), e.getZ() + (RANDOM.nextDouble() - 0.5) * e.getBbWidth() * 1.6,
                            (RANDOM.nextDouble() - 0.5) * 0.2, RANDOM.nextDouble() * 0.2, (RANDOM.nextDouble() - 0.5) * 0.2);
                }
            }
        }

        private long duration() {
            return switch (style) {
                case FLASH, HITMARKER -> 260;
                case RING, SPEEDLINES -> 450;
                case VIGNETTE, BORDER -> 650;
                case TEXT, SHAKE_TEXT -> 950;
                case SYMBOLS -> 1300;
                case CONFETTI -> 1800;
                default -> 0;
            };
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            if (start < 0 || duration() == 0) return;
            float t = (System.currentTimeMillis() - start) / (float) duration();
            if (t >= 1f) {
                start = -1;
                return;
            }
            int w = ctx.guiWidth();
            int h = ctx.guiHeight();
            float k = strength.getFloat();
            int c = color.color();
            float cx = w / 2f;
            float cy = h / 2f;
            Font f = mc().font;
            switch (style) {
                case FLASH -> ctx.fill(0, 0, w, h, Theme.withAlpha(c, Math.round(Math.min(200, 90 * k) * (1 - ViralFx.easeOutCubic(t)))));
                case VIGNETTE -> SoftShapes.vignette(ctx, -w / 10, -h / 10, w + w / 5, h + h / 5, Theme.withAlpha(c, Math.round(Math.min(255, 220 * k) * (1 - t))));
                case BORDER -> {
                    int a = Math.round(Math.min(255, 200 * k) * (1 - t));
                    int b = Math.max(2, Math.round(6 * k));
                    int col = Theme.withAlpha(c, a);
                    ctx.fill(0, 0, w, b, col);
                    ctx.fill(0, h - b, w, h, col);
                    ctx.fill(0, b, b, h - b, col);
                    ctx.fill(w - b, b, w, h - b, col);
                }
                case TEXT, SHAKE_TEXT -> {
                    float pop = 1 + 0.9f * (1 - ViralFx.easeOutBack(t / 0.2f));
                    float alpha = ViralFx.fade(t, 0.04f, 0.3f);
                    float s = 2.4f * Math.max(0.3f, pop) * Math.max(0.6f, k);
                    float dx = style == Style.SHAKE_TEXT ? (float) Math.sin(t * 80) * 3 * (1 - t) : 0;
                    float lift = t > 0.7f ? (t - 0.7f) / 0.3f * 8 : 0;
                    ViralFx.caption(ctx, text, cx + dx, h * 0.3f - lift, s, c, alpha, true);
                }
                case HITMARKER -> {
                    float alpha = 1 - ViralFx.easeInOutCubic(t);
                    int col = Theme.withAlpha(c, Math.round(255 * alpha));
                    float gap = 3 + 3 * ViralFx.easeOutCubic(t);
                    float len = 5 * Math.max(0.6f, k);
                    for (int i = 0; i < 4; i++) {
                        ctx.pose().pushMatrix();
                        ctx.pose().translate(cx, cy);
                        ctx.pose().rotate((float) (Math.PI / 4 + i * Math.PI / 2));
                        gg.carbon.client.ui.R2D.rrect(ctx, gap, -0.75f, len, 1.5f, 0.75f, col);
                        ctx.pose().popMatrix();
                    }
                }
                case RING -> {
                    float e = ViralFx.easeOutCubic(t);
                    float r = 4 + 30 * k * e;
                    SoftShapes.glow(ctx, cx, cy, r * 1.3f, r * 1.3f, Theme.withAlpha(c, Math.round(70 * (1 - t))));
                    gg.carbon.client.ui.R2D.circleOutline(ctx, cx, cy, r, 2.4f * (1 - t) + 0.6f, Theme.withAlpha(c, Math.round(235 * (1 - t))));
                }
                case SPEEDLINES -> {
                    int a = Math.round(160 * (1 - t) * Math.min(1.5f, k));
                    float maxR = (float) Math.hypot(cx, cy);
                    for (int i = 0; i < 28; i++) {
                        float ang = i * 6.283f / 28 + (i % 3) * 0.07f;
                        float r1 = maxR * (0.55f + 0.3f * ((i * 37) % 10) / 10f) - t * 20;
                        ctx.pose().pushMatrix();
                        ctx.pose().translate(cx, cy);
                        ctx.pose().rotate(ang);
                        ctx.fill(Math.round(r1), 0, Math.round(maxR), 1, Theme.withAlpha(c, a));
                        ctx.pose().popMatrix();
                    }
                }
                case CONFETTI -> {
                    float alpha = t > 0.75f ? (1 - t) / 0.25f : 1f;
                    int n = Math.round(Math.min(60, 40 * k));
                    for (int i = 0; i < n; i++) {
                        float[] p = pieces[i];
                        float x = p[0] * w + (float) Math.sin(t * 8 + p[4]) * 6 + p[2] * t * w;
                        float y = -6 + p[1] * t * h * 1.2f + 40 * t * t;
                        int col = net.minecraft.util.Mth.hsvToRgb(p[3], 0.7f, 1f);
                        int s = (i % 3) + 2;
                        ctx.fill(Math.round(x), Math.round(y), Math.round(x) + s, Math.round(y) + s + 1, Theme.withAlpha(0xFF000000 | col, Math.round(255 * alpha)));
                    }
                }
                case SYMBOLS -> {
                    float alpha = 1 - t;
                    for (int i = 0; i < 6; i++) {
                        float[] p = pieces[i];
                        float x = cx + (p[0] - 0.5f) * 60 + (float) Math.sin(t * 6 + p[4]) * 4;
                        float y = cy - 10 - t * 50 * p[1];
                        ctx.pose().pushMatrix();
                        ctx.pose().translate(x, y);
                        ctx.pose().scale(1.5f, 1.5f);
                        ctx.drawString(f, text, -f.width(text) / 2, 0, Theme.withAlpha(c, Math.round(255 * alpha)), true);
                        ctx.pose().popMatrix();
                    }
                }
                default -> {
                }
            }
        }
    }
}
