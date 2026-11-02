package dev.onix.client.module.impl.pack;

import dev.onix.client.gui.SoftShapes;
import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.module.OverlayRenderer;
import dev.onix.client.module.TextHudModule;
import dev.onix.client.module.impl.pack.Events.Event;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.block.Blocks;

/**
 * Viral pack: the effects you see in TikTok PvP edits. Kill streak announcer, WASTED screen, combo counter, damage
 * numbers, impact frames, camera punch and shake, lightning and firework kills, kill feed, aura points and more.
 * Everything is client side only: it changes what you see and hear, never what happens on the server.
 */
public final class ViralPack {
    private ViralPack() {}

    private static final Random R = new Random();

    public static List<Module> modules() {
        List<Module> m = new ArrayList<>();
        m.add(new StreakAnnouncer());
        m.add(new Wasted());
        m.add(new ComboCounter());
        m.add(new DamageNumbers());
        m.add(new ImpactFrames());
        m.add(new KillZoom());
        m.add(new ScreenShake());
        m.add(new Heartbeat());
        m.add(new GlitchHit());
        m.add(new LightningKill());
        m.add(new FireworkKill());
        m.add(new KillExplosion());
        m.add(new BloodHits());
        m.add(new KillFeed());
        m.add(new PopFeed());
        m.add(new KillCam());
        m.add(new AuraPoints());
        m.add(new PhonkKill());
        m.add(new SkullRain());
        m.add(new HitSounds());
        m.add(new KillSounds());
        m.add(new Shockwave());
        return m;
    }

    // ================================================================== base

    abstract static class Viral extends Module implements OverlayRenderer {
        protected final NumberSetting strength = add(new NumberSetting("strength", "Strength", 1, 0.2, 2, 0.1).unit("x"));

        Viral(String id, String name, String description, Category category, String... tags) {
            super("vx_" + id, name, description, category, false);
            tags(tags);
            tags("viral", "tiktok", "edit", "effect");
        }

        /** Listens to an event while the module is on. */
        protected final void on(Event e, java.util.function.Consumer<Entity> action) {
            Events.on(e, (ev, entity) -> {
                if (isEnabled() && mc().player != null) action.accept(entity);
            });
        }

        protected final void play(SoundEvent sound, float pitch, float volume) {
            mc().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        }

        protected static float since(long start, long length) {
            return start < 0 ? 2f : (System.currentTimeMillis() - start) / (float) length;
        }

        protected static String nameOf(Entity e) {
            return e == null ? "?" : e.getName().getString();
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {}
    }

    // ================================================================== 1 kill streak announcer

    static final class StreakAnnouncer extends Viral {
        private final BoolSetting sound = add(new BoolSetting("sound", "Announcer sound", true));
        private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFFD34D));
        private int streak;
        private long start = -1;
        private String word = "";

        StreakAnnouncer() {
            super("streak", "Kill Streak Announcer", "DOUBLE KILL, TRIPLE KILL, RAMPAGE, GODLIKE: a big announcer for every kill in a row without dying.",
                    Category.VISUAL, "streak", "announcer", "kill", "double kill", "rampage");
            on(Event.KILL, e -> {
                streak++;
                String w = switch (streak) {
                    case 1 -> null;
                    case 2 -> "DOUBLE KILL";
                    case 3 -> "TRIPLE KILL";
                    case 4 -> "QUADRA KILL";
                    case 5 -> "PENTA KILL";
                    case 6, 7 -> "RAMPAGE";
                    case 8, 9 -> "UNSTOPPABLE";
                    case 10, 11, 12, 13, 14 -> "GODLIKE";
                    default -> "LEGENDARY";
                };
                if (w == null) return;
                word = w;
                start = System.currentTimeMillis();
                if (sound.isOn()) {
                    play(streak >= 5 ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.PLAYER_LEVELUP, 0.9f + Math.min(streak, 8) * 0.06f, 0.8f);
                }
                ViralFx.punch(0.06f * strength.getFloat(), 450);
            });
            on(Event.DEATH, e -> streak = 0);
        }

        @Override
        public void onTick() {
            if (mc().level == null) streak = 0;
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            float t = since(start, 1900);
            if (t >= 1) return;
            float w = ctx.guiWidth();
            float h = ctx.guiHeight();
            // slam in from big to normal, hold, then lift and fade
            float slam = 1 + 1.6f * (1 - ViralFx.easeOutCubic(t / 0.09f));
            float alpha = ViralFx.fade(t, 0.05f, 0.22f);
            float lift = t > 0.78f ? (t - 0.78f) / 0.22f * 10 : 0;
            float s = 2.6f * slam * Math.max(0.7f, strength.getFloat());
            int c = streak >= 10 ? ViralFx.lerpColor(color.color(), 0xFFFF4D6D, (float) (0.5 + 0.5 * Math.sin(t * 20))) : color.color();
            float y = h * 0.28f - lift;
            if (t < 0.12f) ctx.fill(0, 0, (int) w, (int) h, Theme.withAlpha(0xFFFFFFFF, Math.round(70 * (1 - t / 0.12f))));
            ViralFx.caption(ctx, word, w / 2, y, s, c, alpha, true);
            ViralFx.small(ctx, streak + " KILL STREAK", w / 2, y + 16 + 8 * s / 2.6f, 1.1f, 0xFFFFFFFF, alpha * 0.9f);
        }
    }

    // ================================================================== 2 wasted

    static final class Wasted extends Viral {
        private final ModeSetting word = add(new ModeSetting("word", "Text", "WASTED", "YOU DIED", "ELIMINATED", "SKILL ISSUE"));
        private long start = -1;

        Wasted() {
            super("wasted", "Wasted Screen", "GTA style: the screen turns grey and a big WASTED appears when you die.", Category.VISUAL,
                    "wasted", "gta", "death", "you died", "skill issue");
            on(Event.DEATH, e -> {
                start = System.currentTimeMillis();
                play(SoundEvents.BELL_RESONATE, 0.6f, 0.7f);
            });
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            if (start < 0) return;
            if (mc().player != null && !mc().player.isDeadOrDying() && System.currentTimeMillis() - start > 600) {
                start = -1;
                return;
            }
            float t = (System.currentTimeMillis() - start) / 1000f;
            int w = ctx.guiWidth();
            int h = ctx.guiHeight();
            float grey = ViralFx.easeOutCubic(t / 0.8f);
            ctx.fill(0, 0, w, h, Theme.withAlpha(0xFF1A1A1A, Math.round(150 * grey)));
            SoftShapes.vignette(ctx, -w / 8, -h / 8, w + w / 4, h + h / 4, Theme.withAlpha(0xFF000000, Math.round(220 * grey)));
            float in = ViralFx.clamp01((t - 0.35f) / 0.25f);
            if (in <= 0) return;
            float s = (3.4f + 0.8f * (1 - ViralFx.easeOutCubic(in))) * Math.max(0.7f, strength.getFloat());
            float band = 14 * s;
            int by = Math.round(h * 0.42f);
            ctx.fill(0, Math.round(by - band / 2), w, Math.round(by + band / 2), Theme.withAlpha(0xFF000000, Math.round(140 * in)));
            ViralFx.caption(ctx, word.get(), w / 2f, by, s, 0xFFE0322E, in, false);
        }
    }

    // ================================================================== 3 combo counter

    static final class ComboCounter extends Viral {
        private long bump = -1;

        ComboCounter() {
            super("combo", "Combo Counter", "Fighting game combo counter: a big bouncing hit number that heats up from white to red.", Category.COMBAT,
                    "combo", "hits", "counter", "fighting game");
            on(Event.HIT, e -> bump = System.currentTimeMillis());
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            int combo = dev.onix.client.util.CombatTracker.combo();
            if (combo < 2) return;
            long since = System.currentTimeMillis() - dev.onix.client.util.CombatTracker.lastHitTime();
            float left = 1 - ViralFx.clamp01(since / 3000f);
            float b = since(bump, 220);
            float pop = b < 1 ? 1 + 0.5f * (1 - ViralFx.easeOutCubic(b)) : 1;
            float heat = ViralFx.clamp01((combo - 2) / 12f);
            int c = heat < 0.5f ? ViralFx.lerpColor(0xFFFFFFFF, 0xFFFFC23D, heat * 2) : ViralFx.lerpColor(0xFFFFC23D, 0xFFFF3B5C, (heat - 0.5f) * 2);
            float x = ctx.guiWidth() * 0.78f;
            float y = ctx.guiHeight() * 0.42f;
            float k = Math.max(0.7f, strength.getFloat());
            float alpha = Math.min(1, left * 4);
            ViralFx.caption(ctx, String.valueOf(combo), x, y, 4.2f * pop * k, c, alpha, true);
            ViralFx.small(ctx, "HIT COMBO", x, y + 22 * k, 1.1f * k, 0xFFFFFFFF, alpha);
            int bw = Math.round(46 * k);
            int bx = Math.round(x - bw / 2f);
            int byy = Math.round(y + 30 * k);
            ctx.fill(bx, byy, bx + bw, byy + 2, Theme.withAlpha(0xFF000000, Math.round(120 * alpha)));
            ctx.fill(bx, byy, bx + Math.round(bw * left), byy + 2, Theme.withAlpha(c, Math.round(255 * alpha)));
        }
    }

    // ================================================================== 4 damage numbers

    static final class DamageNumbers extends Viral {
        private final BoolSetting hearts = add(new BoolSetting("hearts", "Show as hearts", true));
        private final List<float[]> pops = new ArrayList<>(); // x, y, value, start(ms offset), crit
        private final List<String> texts = new ArrayList<>();
        private LivingEntity watching;
        private float lastHp;
        private int watchTicks;
        private boolean critPending;
        private long base = System.currentTimeMillis();

        DamageNumbers() {
            super("dmgnumbers", "Damage Numbers", "Shows how much damage each hit did as a popping number, crits in gold.", Category.COMBAT,
                    "damage", "numbers", "indicator", "crit");
            on(Event.HIT, e -> {
                if (e instanceof LivingEntity l) {
                    if (watching != l) lastHp = l.getHealth() + l.getAbsorptionAmount();
                    watching = l;
                    watchTicks = 12;
                }
            });
            on(Event.CRIT, e -> critPending = true);
        }

        @Override
        public void onTick() {
            if (watching == null) return;
            float hp = watching.getHealth() + watching.getAbsorptionAmount();
            if (hp < lastHp - 0.05f) {
                float dmg = lastHp - hp;
                float x = (R.nextFloat() - 0.5f) * 60;
                float y = -14 - R.nextFloat() * 14;
                pops.add(new float[]{x, y, dmg, System.currentTimeMillis() - base, critPending ? 1 : 0});
                String v = hearts.isOn() ? String.format(Locale.ROOT, "-%.1f ❤", dmg / 2f) : String.format(Locale.ROOT, "-%.1f", dmg);
                texts.add(v);
                critPending = false;
                while (pops.size() > 14) {
                    pops.remove(0);
                    texts.remove(0);
                }
            }
            lastHp = hp;
            if (--watchTicks <= 0 || watching.isRemoved()) watching = null;
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            float cx = ctx.guiWidth() / 2f;
            float cy = ctx.guiHeight() / 2f;
            long now = System.currentTimeMillis() - base;
            Iterator<float[]> it = pops.iterator();
            Iterator<String> tt = texts.iterator();
            float k = strength.getFloat();
            while (it.hasNext()) {
                float[] p = it.next();
                String s = tt.next();
                float t = (now - p[3]) / 900f;
                if (t >= 1) {
                    it.remove();
                    tt.remove();
                    continue;
                }
                boolean crit = p[4] > 0;
                float pop = t < 0.15f ? 1 + 0.8f * (1 - t / 0.15f) : 1;
                float y = cy + p[1] - 22 * ViralFx.easeOutCubic(t);
                float alpha = ViralFx.fade(t, 0.02f, 0.35f);
                ViralFx.caption(ctx, s, cx + p[0], y, (crit ? 1.6f : 1.15f) * pop * k, crit ? 0xFFFFC23D : 0xFFFF5A5A, alpha, crit);
            }
        }
    }

    // ================================================================== 5 impact frames

    static final class ImpactFrames extends Viral {
        private final ModeSetting when = add(new ModeSetting("when", "When", "Kills", "Crits", "Both"));
        private long start = -1;

        ImpactFrames() {
            super("impact", "Impact Frames", "Anime impact frames: a split second of black and white when you land a kill or crit.", Category.VISUAL,
                    "impact", "anime", "frame", "edit");
            on(Event.KILL, e -> {
                if (!when.is("Crits")) start = System.currentTimeMillis();
            });
            on(Event.CRIT, e -> {
                if (!when.is("Kills")) start = System.currentTimeMillis();
            });
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            if (start < 0) return;
            long ms = System.currentTimeMillis() - start;
            int w = ctx.guiWidth();
            int h = ctx.guiHeight();
            float k = Math.min(1, strength.getFloat());
            if (ms < 45) {
                ctx.fill(0, 0, w, h, Theme.withAlpha(0xFF000000, Math.round(235 * k)));
                lines(ctx, w, h, 0xFFFFFFFF, k);
            } else if (ms < 95) {
                ctx.fill(0, 0, w, h, Theme.withAlpha(0xFFFFFFFF, Math.round(215 * k)));
                lines(ctx, w, h, 0xFF000000, k);
            } else if (ms < 260) {
                ctx.fill(0, 0, w, h, Theme.withAlpha(0xFFFFFFFF, Math.round(120 * k * (1 - (ms - 95) / 165f))));
            } else {
                start = -1;
            }
        }

        private void lines(GuiGraphics ctx, int w, int h, int color, float k) {
            float cx = w / 2f;
            float cy = h / 2f;
            float maxR = (float) Math.hypot(cx, cy);
            for (int i = 0; i < 36; i++) {
                float ang = i * 6.283f / 36 + R.nextFloat() * 0.08f;
                float r1 = maxR * (0.35f + R.nextFloat() * 0.35f);
                ctx.pose().pushMatrix();
                ctx.pose().translate(cx, cy);
                ctx.pose().rotate(ang);
                ctx.fill(Math.round(r1), 0, Math.round(maxR), 1 + R.nextInt(2), Theme.withAlpha(color, Math.round(230 * k)));
                ctx.pose().popMatrix();
            }
        }
    }

    // ================================================================== 6 kill zoom

    static final class KillZoom extends Viral {
        KillZoom() {
            super("killzoom", "Kill Zoom", "The camera punches in for a moment when you get a kill, like a slow-mo edit.", Category.CAMERA,
                    "zoom", "kill", "fov", "punch");
            on(Event.KILL, e -> ViralFx.punch(0.18f * strength.getFloat(), 700));
            on(Event.ENEMY_POP, e -> ViralFx.punch(0.07f * strength.getFloat(), 400));
        }
    }

    // ================================================================== 7 screen shake

    static final class ScreenShake extends Viral {
        private final BoolSetting onDamage = add(new BoolSetting("damage", "When you take damage", true));
        private final BoolSetting onCrit = add(new BoolSetting("crit", "On your crits", true));
        private final BoolSetting onKill = add(new BoolSetting("kill", "On kills", true));
        private final BoolSetting onPop = add(new BoolSetting("pop", "On totem pops", true));

        ScreenShake() {
            super("shake", "Screen Shake", "Your camera shakes on hits, crits, kills and totem pops. Only your view moves, not your aim.", Category.CAMERA,
                    "shake", "camera", "hit", "edit");
            on(Event.DAMAGE, e -> {
                if (onDamage.isOn()) ViralFx.shake(1.6f * strength.getFloat(), 260);
            });
            on(Event.CRIT, e -> {
                if (onCrit.isOn()) ViralFx.shake(1.1f * strength.getFloat(), 200);
            });
            on(Event.KILL, e -> {
                if (onKill.isOn()) ViralFx.shake(2.6f * strength.getFloat(), 450);
            });
            on(Event.SELF_POP, e -> {
                if (onPop.isOn()) ViralFx.shake(3f * strength.getFloat(), 500);
            });
            on(Event.ENEMY_POP, e -> {
                if (onPop.isOn()) ViralFx.shake(1.4f * strength.getFloat(), 260);
            });
        }
    }

    // ================================================================== 8 heartbeat

    static final class Heartbeat extends Viral {
        private final NumberSetting below = add(new NumberSetting("below", "Below health", 6, 2, 14, 1).unit(" hp"));
        private final BoolSetting sound = add(new BoolSetting("sound", "Heartbeat sound", true));
        private double phase;
        private long last = System.currentTimeMillis();
        private boolean beatPlayed;

        Heartbeat() {
            super("heartbeat", "Heartbeat", "Low health: the screen edges pulse red with your heartbeat, faster the lower you are.", Category.COMBAT,
                    "heartbeat", "low health", "vignette", "pulse");
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            long now = System.currentTimeMillis();
            float dt = Math.min(0.1f, (now - last) / 1000f);
            last = now;
            float hp = mc().player.getHealth();
            if (hp > below.getFloat() || mc().player.isDeadOrDying()) {
                phase = 0;
                return;
            }
            float danger = 1 - ViralFx.clamp01(hp / below.getFloat());
            float bpm = 70 + 90 * danger;
            phase += dt * bpm / 60.0;
            if (phase >= 1) {
                phase -= 1;
                beatPlayed = false;
            }
            if (!beatPlayed && sound.isOn()) {
                beatPlayed = true;
                play(SoundEvents.WARDEN_HEARTBEAT, 1.1f, 0.5f + 0.4f * danger);
            }
            // double beat: lub-dub
            float p = (float) phase;
            float pulse = (float) (Math.exp(-Math.pow((p - 0.05) / 0.06, 2)) + 0.7 * Math.exp(-Math.pow((p - 0.28) / 0.06, 2)));
            int w = ctx.guiWidth();
            int h = ctx.guiHeight();
            int a = Math.round(Math.min(255, (70 + 150 * pulse) * (0.5f + danger * 0.5f) * strength.getFloat()));
            SoftShapes.vignette(ctx, -w / 10, -h / 10, w + w / 5, h + h / 5, Theme.withAlpha(0xFFD0182B, a));
        }
    }

    // ================================================================== 9 glitch hit

    static final class GlitchHit extends Viral {
        private long start = -1;
        private final float[][] slices = new float[10][3];

        GlitchHit() {
            super("glitch", "Glitch Hit", "Taking damage glitches the screen: RGB split edges and shifted slices.", Category.VISUAL,
                    "glitch", "rgb", "chromatic", "damage");
            on(Event.DAMAGE, e -> {
                start = System.currentTimeMillis();
                for (float[] s : slices) {
                    s[0] = R.nextFloat();
                    s[1] = 0.01f + R.nextFloat() * 0.05f;
                    s[2] = (R.nextFloat() - 0.5f) * 30;
                }
            });
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            float t = since(start, 340);
            if (t >= 1) return;
            int w = ctx.guiWidth();
            int h = ctx.guiHeight();
            float k = strength.getFloat();
            float a = (1 - t) * Math.min(1.5f, k);
            int off = Math.round(6 * k * (1 - t));
            SoftShapes.vignette(ctx, -w / 10 - off, -h / 10, w + w / 5, h + h / 5, Theme.withAlpha(0xFFFF2050, Math.round(130 * a)));
            SoftShapes.vignette(ctx, -w / 10 + off, -h / 10, w + w / 5, h + h / 5, Theme.withAlpha(0xFF20E0FF, Math.round(110 * a)));
            for (float[] s : slices) {
                if (R.nextFloat() < 0.35f) continue;
                int y = Math.round(s[0] * h);
                int sh = Math.max(1, Math.round(s[1] * h));
                int dx = Math.round(s[2] * (1 - t));
                ctx.fill(dx, y, w + dx, y + sh, Theme.withAlpha(R.nextBoolean() ? 0xFFFF2050 : 0xFF20E0FF, Math.round(45 * a)));
            }
        }
    }

    // ================================================================== 10 lightning kill

    static final class LightningKill extends Viral {
        private static int nextId = -2_000_000;

        LightningKill() {
            super("lightning", "Lightning Kill", "Lightning strikes where your opponent dies. Only you see and hear it.", Category.VISUAL,
                    "lightning", "kill effect", "thunder");
            on(Event.KILL, e -> {
                if (e == null || mc().level == null) return;
                LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, mc().level);
                bolt.setPos(e.getX(), e.getY(), e.getZ());
                bolt.setVisualOnly(true);
                bolt.setId(nextId--);
                mc().level.addEntity(bolt);
            });
        }
    }

    // ================================================================== 11 firework kill

    static final class FireworkKill extends Viral {
        private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFF8B7CFF));
        private final BoolSetting rainbow = add(new BoolSetting("rainbow", "Rainbow", true));

        FireworkKill() {
            super("firework", "Firework Kill", "A firework bursts where your opponent dies.", Category.VISUAL, "firework", "kill effect", "celebrate");
            on(Event.KILL, e -> {
                if (e == null || mc().level == null) return;
                IntList colors = rainbow.isOn()
                        ? IntList.of(0xFF3B5C, 0xFFC23D, 0x5AFF8C, 0x3DC8FF, 0xB07CFF)
                        : IntList.of(color.color() & 0xFFFFFF);
                FireworkExplosion boom = new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL, colors, IntList.of(0xFFFFFF), true, true);
                mc().level.createFireworks(e.getX(), e.getY() + e.getBbHeight() * 0.8, e.getZ(), 0, 0, 0, List.of(boom));
            });
        }
    }

    // ================================================================== 12 kill explosion

    static final class KillExplosion extends Viral {
        KillExplosion() {
            super("explosion", "Kill Explosion", "An explosion goes off where your opponent dies (only visual, nothing breaks).", Category.VISUAL,
                    "explosion", "boom", "kill effect");
            on(Event.KILL, e -> {
                if (e == null || mc().level == null) return;
                mc().level.addParticle(ParticleTypes.EXPLOSION_EMITTER, e.getX(), e.getY() + 0.5, e.getZ(), 0, 0, 0);
                mc().level.playLocalSound(e.getX(), e.getY(), e.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.7f, 1f, false);
            });
        }
    }

    // ================================================================== 13 blood hits

    static final class BloodHits extends Viral {
        BloodHits() {
            super("blood", "Blood Hits", "Red bursts on every hit and a big one on kills.", Category.VISUAL, "blood", "hit", "particles", "kill effect");
            on(Event.HIT, e -> burst(e, 10));
            on(Event.KILL, e -> burst(e, 45));
        }

        private void burst(Entity e, int n) {
            if (e == null || mc().level == null) return;
            BlockParticleOption blood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
            int count = Math.round(n * strength.getFloat());
            for (int i = 0; i < count; i++) {
                mc().level.addParticle(blood, e.getX() + (R.nextDouble() - 0.5) * e.getBbWidth(), e.getY() + R.nextDouble() * e.getBbHeight(),
                        e.getZ() + (R.nextDouble() - 0.5) * e.getBbWidth(), (R.nextDouble() - 0.5) * 0.3, R.nextDouble() * 0.3, (R.nextDouble() - 0.5) * 0.3);
            }
        }
    }

    // ================================================================== 14 kill feed

    static final class FeedEntry {
        final String left;
        final String right;
        final ItemStack icon;
        final int color;
        final long start = System.currentTimeMillis();

        FeedEntry(String left, String right, ItemStack icon, int color) {
            this.left = left;
            this.right = right;
            this.icon = icon;
            this.color = color;
        }
    }

    abstract static class Feed extends Viral {
        protected final List<FeedEntry> entries = new ArrayList<>();

        Feed(String id, String name, String description, String... tags) {
            super(id, name, description, Category.HUD, tags);
        }

        protected void push(FeedEntry e) {
            entries.add(0, e);
            while (entries.size() > 5) entries.remove(entries.size() - 1);
        }

        protected abstract float top(int h);

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            var font = mc().font;
            int w = ctx.guiWidth();
            float y = top(ctx.guiHeight());
            long now = System.currentTimeMillis();
            entries.removeIf(e -> now - e.start > 5500);
            for (FeedEntry e : entries) {
                float t = (now - e.start) / 5500f;
                float in = ViralFx.easeOutCubic((now - e.start) / 260f);
                float alpha = ViralFx.fade(t, 0, 0.08f);
                int lw = font.width(e.left);
                int rw = font.width(e.right);
                int width = lw + rw + 16 + 18;
                float x = w - 6 - width + (1 - in) * (width + 10);
                int ix = Math.round(x);
                int iy = Math.round(y);
                gg.carbon.client.ui.R2D.rrect(ctx, ix, iy, width, 18, 6, Theme.withAlpha(0xFF0E0D14, Math.round(190 * alpha)));
                ctx.fill(ix, iy + 4, ix + 2, iy + 14, Theme.withAlpha(e.color, Math.round(255 * alpha)));
                ctx.drawString(font, e.left, ix + 7, iy + 5, Theme.withAlpha(0xFFFFFFFF, Math.round(255 * alpha)), false);
                if (alpha > 0.5f) {
                    ctx.pose().pushMatrix();
                    ctx.pose().translate(ix + 7 + lw + 3, iy + 2);
                    ctx.pose().scale(0.875f, 0.875f);
                    ctx.renderItem(e.icon, 0, 0);
                    ctx.pose().popMatrix();
                }
                ctx.drawString(font, e.right, ix + 7 + lw + 19, iy + 5, Theme.withAlpha(e.color, Math.round(255 * alpha)), false);
                y += 21 * in;
            }
        }
    }

    static final class KillFeed extends Feed {
        KillFeed() {
            super("killfeed", "Kill Feed", "A shooter style kill feed in the top right: who you killed and with what.", "kill feed", "killfeed", "valorant");
            on(Event.KILL, e -> {
                ItemStack held = mc().player.getMainHandItem().copy();
                push(new FeedEntry(mc().player.getName().getString(), nameOf(e), held.isEmpty() ? new ItemStack(Items.IRON_SWORD) : held, 0xFFFF4D6D));
            });
            on(Event.DEATH, e -> push(new FeedEntry("☠", "You died", new ItemStack(Items.SKELETON_SKULL), 0xFF9A96B0)));
        }

        @Override
        protected float top(int h) {
            return 6;
        }
    }

    static final class PopFeed extends Feed {
        private final Map<String, Integer> pops = new HashMap<>();

        PopFeed() {
            super("popfeed", "Pop Feed", "Every totem pop around you in a feed, with how many each player has popped.", "totem", "pop", "crystal pvp");
            on(Event.ENEMY_POP, e -> add(nameOf(e), 0xFFFFC23D));
            on(Event.SELF_POP, e -> add("You", 0xFF5AFF8C));
            on(Event.DEATH, e -> pops.clear());
        }

        private void add(String name, int color) {
            int n = pops.merge(name, 1, Integer::sum);
            push(new FeedEntry(name, "popped " + n + (n == 1 ? " totem" : " totems"), new ItemStack(Items.TOTEM_OF_UNDYING), color));
        }

        @Override
        public void onTick() {
            if (mc().level == null) pops.clear();
        }

        @Override
        protected float top(int h) {
            return h * 0.3f;
        }
    }

    // ================================================================== 16 kill cam

    static final class KillCam extends Viral {
        private long start = -1;
        private String name = "";

        KillCam() {
            super("killcam", "Kill Cam", "Cinema bars slide in on a kill with the player's name, like the end of a movie scene.", Category.VISUAL,
                    "kill cam", "cinematic", "bars", "eliminated");
            on(Event.KILL, e -> {
                start = System.currentTimeMillis();
                name = nameOf(e);
            });
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            float t = since(start, 1800);
            if (t >= 1) return;
            int w = ctx.guiWidth();
            int h = ctx.guiHeight();
            float in = t < 0.15f ? ViralFx.easeOutCubic(t / 0.15f) : t > 0.82f ? 1 - ViralFx.easeInOutCubic((t - 0.82f) / 0.18f) : 1;
            int bar = Math.round(h * 0.11f * in * Math.min(1.3f, strength.getFloat()));
            ctx.fill(0, 0, w, bar, 0xF0000000);
            ctx.fill(0, h - bar, w, h, 0xF0000000);
            float textAlpha = ViralFx.fade(t, 0.12f, 0.2f);
            String spaced = spaced("ELIMINATED");
            ViralFx.small(ctx, spaced, w / 2f, h - bar / 2f - 5, 0.9f, 0xFF9A96B0, textAlpha);
            ViralFx.caption(ctx, name, w / 2f, h - bar / 2f + 6, 1.4f, 0xFFFFFFFF, textAlpha, false);
        }

        private static String spaced(String s) {
            StringBuilder b = new StringBuilder();
            for (char c : s.toCharArray()) b.append(c).append(' ');
            return b.toString().trim();
        }
    }

    // ================================================================== 17 aura points

    static final class AuraPoints extends TextHudModule implements OverlayRenderer {
        private long aura;
        private final List<long[]> pops = new ArrayList<>(); // amount, start
        private final BoolSetting popups = add(new BoolSetting("popups", "Popups", true));

        AuraPoints() {
            super("vx_aura", "Aura Points", "+1000 aura for a kill, -2000 for dying. Your aura count for the session with popping +aura numbers.",
                    Category.HUD, false, Anchor.TOP_LEFT, 4, 30);
            tags("aura", "aura points", "sigma", "viral", "tiktok", "meme");
            give(Event.KILL, 1000);
            give(Event.TRIPLE_KILL, 2500);
            give(Event.CRIT, 50);
            give(Event.COMBO5, 250);
            give(Event.COMBO10, 1000);
            give(Event.ENEMY_POP, 500);
            give(Event.DEATH, -2000);
            give(Event.SELF_POP, -300);
        }

        private void give(Event e, int amount) {
            Events.on(e, (ev, entity) -> {
                if (!isEnabled()) return;
                aura += amount;
                pops.add(new long[]{amount, System.currentTimeMillis()});
                while (pops.size() > 6) pops.remove(0);
            });
        }

        @Override
        protected void lines(List<Line> out, boolean preview) {
            long v = preview && aura == 0 ? 12500 : aura;
            out.add(new Line("Aura", (v >= 0 ? "+" : "") + String.format(Locale.ROOT, "%,d", v), v >= 0 ? 0xFFB9A8FF : 0xFFFF5A5A));
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            if (!popups.isOn()) return;
            float cx = ctx.guiWidth() / 2f;
            float cy = ctx.guiHeight() / 2f + 30;
            long now = System.currentTimeMillis();
            pops.removeIf(p -> now - p[1] > 1300);
            int i = 0;
            for (long[] p : pops) {
                float t = (now - p[1]) / 1300f;
                float pop = t < 0.12f ? 1 + 0.6f * (1 - t / 0.12f) : 1;
                boolean plus = p[0] >= 0;
                String s = (plus ? "+" : "") + String.format(Locale.ROOT, "%,d", p[0]) + " AURA";
                ViralFx.caption(ctx, s, cx, cy + i * 4 - 26 * ViralFx.easeOutCubic(t), 1.5f * pop, plus ? 0xFFB9A8FF : 0xFFFF5A5A,
                        ViralFx.fade(t, 0.03f, 0.3f), plus && p[0] >= 1000);
                i++;
            }
        }
    }

    // ================================================================== 18 phonk kill

    static final class PhonkKill extends Viral {
        private final BoolSetting sound = add(new BoolSetting("sound", "Bass drop", true));
        private long start = -1;

        PhonkKill() {
            super("phonk", "Phonk Kill", "Phonk edit on every kill: the screen goes dark, a skull slams in and the bass drops.", Category.VISUAL,
                    "phonk", "skull", "edit", "kill effect", "sigma");
            on(Event.KILL, e -> {
                start = System.currentTimeMillis();
                if (sound.isOn()) play(SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 0.6f);
                ViralFx.punch(0.1f * strength.getFloat(), 900);
            });
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            float t = since(start, 1400);
            if (t >= 1) return;
            int w = ctx.guiWidth();
            int h = ctx.guiHeight();
            float dark = ViralFx.fade(t, 0.04f, 0.45f);
            ctx.fill(0, 0, w, h, Theme.withAlpha(0xFF0A0A0A, Math.round(150 * dark * Math.min(1.3f, strength.getFloat()))));
            SoftShapes.vignette(ctx, -w / 10, -h / 10, w + w / 5, h + h / 5, Theme.withAlpha(0xFF000000, Math.round(230 * dark)));
            float slam = 1 + 2.4f * (1 - ViralFx.easeOutCubic(t / 0.1f));
            float shakeX = t < 0.35f ? (float) Math.sin(t * 160) * 3 * (1 - t / 0.35f) : 0;
            ViralFx.caption(ctx, "☠", w / 2f + shakeX, h * 0.42f, 5f * slam, 0xFFFFFFFF, ViralFx.fade(t, 0.03f, 0.3f), true);
        }
    }

    // ================================================================== 19 skull rain

    static final class SkullRain extends Viral {
        private final ModeSetting symbol = add(new ModeSetting("symbol", "Symbol", "☠", "★", "❤", "✦", "$"));
        private final float[][] drops = new float[28][4];
        private long start = -1;

        SkullRain() {
            super("skullrain", "Skull Rain", "Skulls rain down your screen after a kill.", Category.VISUAL, "skull", "rain", "kill effect", "emoji");
            on(Event.KILL, e -> {
                start = System.currentTimeMillis();
                for (float[] d : drops) {
                    d[0] = R.nextFloat();
                    d[1] = 0.6f + R.nextFloat() * 0.7f;
                    d[2] = R.nextFloat() * 0.35f;
                    d[3] = 0.9f + R.nextFloat() * 1.4f;
                }
            });
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            float t = since(start, 2200);
            if (t >= 1) return;
            int w = ctx.guiWidth();
            int h = ctx.guiHeight();
            int n = Math.round(Math.min(28, 18 * strength.getFloat()));
            for (int i = 0; i < n; i++) {
                float[] d = drops[i];
                float lt = (t - d[2]) / (1 - d[2]);
                if (lt <= 0 || lt >= 1) continue;
                float y = -10 + lt * lt * h * 1.15f * d[1] + lt * 30;
                float x = d[0] * w + (float) Math.sin(lt * 6 + i) * 6;
                ViralFx.small(ctx, symbol.get(), x, y, d[3], 0xFFFFFFFF, ViralFx.fade(lt, 0.05f, 0.25f));
            }
        }
    }

    // ================================================================== 20 hit sounds

    static final class HitSounds extends Viral {
        private final ModeSetting kind = add(new ModeSetting("sound", "Sound", "Osu", "Bell", "XP", "Amethyst", "Bass", "Arrow"));

        HitSounds() {
            super("hitsounds", "Hit Sounds", "A satisfying sound on every hit you land, higher on crits.", Category.COMBAT, "hit sound", "osu", "sound");
            on(Event.HIT, e -> sound(1f));
            on(Event.CRIT, e -> sound(1.35f));
        }

        private void sound(float pitch) {
            float v = Math.min(1f, 0.7f * strength.getFloat());
            switch (kind.get()) {
                case "Bell" -> play(SoundEvents.NOTE_BLOCK_BELL.value(), pitch, v);
                case "XP" -> play(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.9f * pitch, v);
                case "Amethyst" -> play(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, v);
                case "Bass" -> play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f * pitch, v);
                case "Arrow" -> play(SoundEvents.ARROW_HIT_PLAYER, pitch, v);
                default -> play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.6f * pitch, v);
            }
        }
    }

    // ================================================================== 21 kill sounds

    static final class KillSounds extends Viral {
        private final ModeSetting kind = add(new ModeSetting("sound", "Sound", "Ding", "Level Up", "Challenge", "Dragon", "Thunder", "Anvil", "Wither"));

        KillSounds() {
            super("killsounds", "Kill Sounds", "A sound of your choice when you get a kill.", Category.COMBAT, "kill sound", "sound", "ding");
            on(Event.KILL, e -> {
                float v = Math.min(1f, 0.8f * strength.getFloat());
                switch (kind.get()) {
                    case "Level Up" -> play(SoundEvents.PLAYER_LEVELUP, 1f, v);
                    case "Challenge" -> play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, v);
                    case "Dragon" -> play(SoundEvents.ENDER_DRAGON_GROWL, 1.2f, v * 0.6f);
                    case "Thunder" -> play(SoundEvents.LIGHTNING_BOLT_THUNDER, 1f, v * 0.6f);
                    case "Anvil" -> play(SoundEvents.ANVIL_LAND, 1.2f, v);
                    case "Wither" -> play(SoundEvents.WITHER_SPAWN, 1.4f, v * 0.5f);
                    default -> play(SoundEvents.BELL_BLOCK, 1.5f, v);
                }
            });
        }
    }

    // ================================================================== 22 shockwave

    static final class Shockwave extends Viral {
        private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFF8B7CFF));
        private long start = -1;

        Shockwave() {
            super("shockwave", "Shockwave", "Rings blast out from your crosshair with a flash when you get a kill.", Category.VISUAL,
                    "shockwave", "rings", "kill effect", "flash");
            on(Event.KILL, e -> start = System.currentTimeMillis());
        }

        @Override
        public void renderOverlay(GuiGraphics ctx) {
            float t = since(start, 750);
            if (t >= 1) return;
            float cx = ctx.guiWidth() / 2f;
            float cy = ctx.guiHeight() / 2f;
            float k = strength.getFloat();
            int c = color.color();
            if (t < 0.1f) ctx.fill(0, 0, ctx.guiWidth(), ctx.guiHeight(), Theme.withAlpha(0xFFFFFFFF, Math.round(110 * (1 - t / 0.1f))));
            SoftShapes.glow(ctx, cx, cy, 60 * k, 60 * k, Theme.withAlpha(c, Math.round(140 * (1 - t))));
            for (int i = 0; i < 3; i++) {
                float lt = ViralFx.clamp01((t - i * 0.12f) / 0.7f);
                if (lt <= 0 || lt >= 1) continue;
                float r = 6 + 120 * k * ViralFx.easeOutCubic(lt);
                float th = Mth.lerp(lt, 3.5f, 0.6f);
                gg.carbon.client.ui.R2D.circleOutline(ctx, cx, cy, r, th, Theme.withAlpha(i == 1 ? 0xFFFFFFFF : c, Math.round(230 * (1 - lt))));
            }
        }
    }
}
