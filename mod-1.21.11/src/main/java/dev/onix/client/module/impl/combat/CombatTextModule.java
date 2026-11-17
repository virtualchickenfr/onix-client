package dev.onix.client.module.impl.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.onix.client.gui.Theme;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.module.OverlayRenderer;
import dev.onix.client.module.impl.pack.Events;
import dev.onix.client.module.impl.pack.Events.Event;
import dev.onix.client.render.WorldDraw;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Combat text: the damage of every hit pops up as a number above the one you hit (crits in gold), and kills in a row
 * are announced in the middle of the screen (DOUBLE KILL, TRIPLE KILL, …) with your streak. Only you see it.
 */
public class CombatTextModule extends Module implements OverlayRenderer {
    private static final Random RANDOM = new Random();
    private static final String[] STREAK = {"", "KILL", "DOUBLE KILL", "TRIPLE KILL", "QUAD KILL", "RAMPAGE", "UNSTOPPABLE", "LEGENDARY"};

    private final BoolSetting numbers = add(new BoolSetting("numbers", "Damage Numbers", true).describe("The damage of every hit pops up above the target."));
    private final BoolSetting hearts = add(new BoolSetting("hearts", "Show as Hearts", true).describe("1 heart = 2 damage."));
    private final NumberSetting size = add(new NumberSetting("size", "Number Size", 1.0, 0.5, 2.5, 0.1).unit("x"));
    private final ColorSetting hitColor = add(new ColorSetting("hitColor", "Hit Color", 0xFFFFFFFF));
    private final ColorSetting critColor = add(new ColorSetting("critColor", "Crit Color", 0xFFFFC23D));
    private final BoolSetting announcer = add(new BoolSetting("announcer", "Kill Streak Announcer", true).describe("DOUBLE KILL, TRIPLE KILL … in the middle of the screen."));
    private final NumberSetting window = add(new NumberSetting("window", "Streak Window", 10, 3, 30, 1).unit("s").describe("How long you have for the next kill of a streak."));

    private record Pop(Vec3 pos, String text, int color, long start, float driftX) {}

    private final List<Pop> pops = new ArrayList<>();
    /** Entities hit lately: id → health before the hit (and when it was hit). */
    private final Map<Integer, double[]> watched = new HashMap<>();
    private final Map<Integer, Boolean> critHit = new HashMap<>();
    private int streak;
    private long lastKill;
    private long shownAt;
    private String shown = "";

    public CombatTextModule() {
        super("combattext", "Combat Text", "Damage numbers above your targets and a kill streak announcer.", Category.COMBAT, false);
        tags("damage", "numbers", "kill streak", "double kill", "combo", "hit");
    }

    @Override
    public void init() {
        Events.on(Event.HIT, (e, target) -> remember(target, false));
        Events.on(Event.CRIT, (e, target) -> remember(target, true));
        Events.on(Event.KILL, (e, target) -> {
            if (!isEnabled()) return;
            long now = System.currentTimeMillis();
            streak = now - lastKill <= window.get() * 1000 ? streak + 1 : 1;
            lastKill = now;
            if (announcer.isOn()) {
                shown = STREAK[Math.min(streak, STREAK.length - 1)];
                shownAt = now;
            }
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled() || pops.isEmpty() || context.consumers() == null || context.matrices() == null) return;
            long now = System.currentTimeMillis();
            pops.removeIf(p -> now - p.start() > 1100);
            PoseStack m = context.matrices();
            Vec3 cam = WorldDraw.camera(context);
            Font font = mc().font;
            for (Pop p : pops) {
                float t = (now - p.start()) / 1100f;
                float rise = 0.35f + (float) (1 - Math.pow(1 - t, 3)) * 0.8f;
                float pop = t < 0.12f ? 0.6f + t / 0.12f * 0.6f : 1.2f - Math.min(0.2f, (t - 0.12f));
                int alpha = Math.round(255 * (t < 0.75f ? 1f : 1f - (t - 0.75f) / 0.25f));
                if (alpha < 8) continue;
                float s = 0.03f * size.getFloat() * pop;
                m.pushPose();
                m.translate(p.pos().x - cam.x + p.driftX() * t, p.pos().y - cam.y + rise, p.pos().z - cam.z);
                m.mulPose(mc().gameRenderer.getMainCamera().rotation());
                m.scale(s, -s, s);
                Component text = Theme.bold(p.text());
                float x = -font.width(text) / 2f;
                int col = (alpha << 24) | (p.color() & 0xFFFFFF);
                font.drawInBatch(text, x + 1, 1, (alpha << 24) | 0x202020, false, m.last().pose(), context.consumers(), Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                font.drawInBatch(text, x, 0, col, false, m.last().pose(), context.consumers(), Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
                m.popPose();
            }
        });
    }

    private void remember(Entity target, boolean crit) {
        if (!isEnabled() || !numbers.isOn() || !(target instanceof LivingEntity living)) return;
        watched.putIfAbsent(target.getId(), new double[]{living.getHealth() + living.getAbsorptionAmount(), System.currentTimeMillis()});
        if (crit) critHit.put(target.getId(), true);
    }

    @Override
    public void onTick() {
        if (mc().level == null || watched.isEmpty()) return;
        long now = System.currentTimeMillis();
        var it = watched.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            Entity entity = mc().level.getEntity(e.getKey());
            double[] before = e.getValue();
            if (!(entity instanceof LivingEntity living)) {
                it.remove();
                continue;
            }
            float health = living.isDeadOrDying() ? 0 : living.getHealth() + living.getAbsorptionAmount();
            float lost = (float) (before[0] - health);
            if (lost > 0.05f) {
                boolean crit = critHit.remove(e.getKey()) != null;
                String text = hearts.isOn() ? fmt(lost / 2f) + " ❤" : fmt(lost);
                Vec3 pos = entity.position().add(0, entity.getBbHeight(), 0);
                pops.add(new Pop(pos, crit ? text + "!" : text, crit ? critColor.color() : hitColor.color(), now, (RANDOM.nextFloat() - 0.5f) * 0.6f));
                if (pops.size() > 40) pops.remove(0);
                it.remove();
            } else if (now - before[1] > 1000) {
                critHit.remove(e.getKey());
                it.remove();
            }
        }
    }

    /** For the dev test: a few numbers above [at] and a TRIPLE KILL. */
    public void demo(Entity at) {
        long now = System.currentTimeMillis();
        Vec3 pos = at.position().add(0, at.getBbHeight(), 0);
        pops.add(new Pop(pos, "3.5 ❤", hitColor.color(), now - 150, -0.25f));
        pops.add(new Pop(pos, "6 ❤!", critColor.color(), now - 60, 0.25f));
        streak = 3;
        shown = STREAK[3];
        shownAt = now - 300;
    }

    private static String fmt(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    @Override
    protected void onDisable() {
        pops.clear();
        watched.clear();
        streak = 0;
    }

    @Override
    public void renderOverlay(GuiGraphics ctx) {
        if (shown.isEmpty()) return;
        float t = (System.currentTimeMillis() - shownAt) / 1600f;
        if (t >= 1f) {
            shown = "";
            return;
        }
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        float in = t < 0.12f ? t / 0.12f : 1f;
        float scale = (streak >= 5 ? 2.6f : 2.1f) * (t < 0.12f ? 1.4f - 0.4f * in : 1f);
        int alpha = Math.round(255 * (t < 0.8f ? in : 1f - (t - 0.8f) / 0.2f));
        if (alpha < 8) return;
        int base = streak >= 5 ? 0xFF4D5E : streak >= 3 ? 0xFFC23D : 0xFFFFFF;
        Component title = Theme.display(shown);
        int tw = Theme.width(title);
        ctx.pose().pushMatrix();
        ctx.pose().translate(w / 2f - tw * scale / 2f, h * 0.28f);
        ctx.pose().scale(scale, scale);
        ctx.drawString(Theme.font(), title, 0, 0, (alpha << 24) | base, true);
        ctx.pose().popMatrix();
        if (streak > 1) Theme.textCentered(ctx, streak + " in a row", w / 2, (int) (h * 0.28f + 12 * scale + 4), (Math.min(alpha, 200) << 24) | 0xFFFFFF);
    }
}
