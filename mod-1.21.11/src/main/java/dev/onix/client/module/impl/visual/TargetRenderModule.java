package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.render.FxDraw;
import dev.onix.client.render.WorldDraw;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import dev.onix.client.util.CombatTracker;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Target Render: a glowing ring around the entity you are fighting – it slides up and down the body with a
 * fading trail. Only you see it; it does not aim or attack for you.
 */
public class TargetRenderModule extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Ring", "Double Ring", "Pulse"));
    private final ColorSetting color = add(ColorSetting.accent("color", "Color"));
    private final BoolSetting rainbow = add(new BoolSetting("rainbow", "Rainbow", false));
    private final BoolSetting hurtColor = add(new BoolSetting("hurtColor", "Red When Hit", true)
            .describe("Turns red for a moment every time your hit lands."));
    private final NumberSetting speed = add(new NumberSetting("speed", "Speed", 1, 0.3, 3, 0.1).unit("x"));
    private final NumberSetting keep = add(new NumberSetting("keep", "Show After Hit", 3, 0.5, 10, 0.5).unit("s"));
    private final BoolSetting aimed = add(new BoolSetting("aimed", "Also While Aiming", false)
            .describe("Shows the ring on the entity under your crosshair, before you hit it."));
    private final BoolSetting onlyPlayers = add(new BoolSetting("players", "Only Players", false));

    /** 0..1 fade so the ring does not pop in and out. */
    private float shown;
    private Entity last;

    public TargetRenderModule() {
        super("targetrender", "Target Render", "A glowing ring around the player you are fighting.", Category.VISUAL, false);
        tags("target", "target esp", "target circle", "ring", "combat", "pvp");
    }

    private Entity target() {
        long since = System.currentTimeMillis() - CombatTracker.lastHitTime();
        Entity t = CombatTracker.target();
        if (t == null || since > keep.get() * 1000) t = null;
        if (t == null && aimed.isOn() && mc().hitResult instanceof EntityHitResult hit) t = hit.getEntity();
        if (!(t instanceof LivingEntity living) || !living.isAlive() || t.isRemoved() || t == mc().player) return null;
        if (onlyPlayers.isOn() && !(t instanceof Player)) return null;
        if (t.isInvisible() || mc().level == null || t.level() != mc().level) return null;
        return t;
    }

    @Override
    public void onTick() {
        Entity t = target();
        if (t != null) last = t;
        shown = Math.max(0f, Math.min(1f, shown + (t != null ? 0.25f : -0.15f)));
        if (shown <= 0) last = null;
    }

    @Override
    protected void onDisable() {
        shown = 0;
        last = null;
    }

    @Override
    public void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            Entity e = last;
            if (!isEnabled() || e == null || shown <= 0 || context.consumers() == null || context.matrices() == null) return;
            if (mc().options.getCameraType().isFirstPerson() && e == mc().getCameraEntity()) return;
            PoseStack matrices = context.matrices();
            Vec3 cam = WorldDraw.camera(context);
            float delta = WorldDraw.partialTick();
            Vec3 pos = e.getPosition(delta);
            double x = pos.x - cam.x, y = pos.y - cam.y, z = pos.z - cam.z;
            float h = e.getBbHeight();
            float r = e.getBbWidth() * 0.75f + 0.08f;
            float time = (e.tickCount + delta) * 0.06f * speed.getFloat();

            int base = rainbow.isOn() ? FxDraw.rainbow(time * 0.25f) : color.color();
            if (hurtColor.isOn() && e instanceof LivingEntity living && living.hurtTime > 0) {
                base = lerp(base, 0xFFFF3B3B, living.hurtTime / 10f);
            }
            VertexConsumer vc = context.consumers().getBuffer(RenderTypes.lightning());
            switch (style.get()) {
                case "Pulse" -> {
                    for (int i = 0; i < 3; i++) {
                        float p = (time * 0.6f + i / 3f) % 1f;
                        float rr = r * (0.4f + p * 0.9f);
                        int c = FxDraw.withAlpha(base, (1 - p) * 0.9f * shown);
                        FxDraw.ringBoth(matrices, vc, x, y + 0.04, z, Math.max(0, rr - 0.06f), rr, FxDraw.withAlpha(base, 0f), c, 40);
                    }
                }
                case "Double Ring" -> {
                    ring(matrices, vc, x, y, z, r, h, time, base);
                    ring(matrices, vc, x, y, z, r, h, time + (float) Math.PI, base);
                }
                default -> ring(matrices, vc, x, y, z, r, h, time, base);
            }
        });
    }

    /** A ring at a height that moves up and down the body, with a fading band trailing behind it. */
    private void ring(PoseStack m, VertexConsumer vc, double x, double y, double z, float r, float h, float time, int base) {
        float wave = (float) Math.sin(time * 2);
        float ry = (0.5f + 0.5f * wave) * h;
        float dir = (float) Math.cos(time * 2);              // > 0 going up: the trail hangs below
        float trail = -Math.signum(dir) * Math.min(0.45f, 0.12f + Math.abs(dir) * 0.35f);
        int solid = FxDraw.withAlpha(base, 0.85f * shown);
        int clear = FxDraw.withAlpha(base, 0f);
        FxDraw.band(m, vc, x, y + ry, z, r, trail, FxDraw.withAlpha(base, 0.45f * shown), clear, 40);
        FxDraw.ringBoth(m, vc, x, y + ry, z, r - 0.03f, r + 0.03f, solid, solid, 40);
    }

    private static int lerp(int a, int b, float f) {
        f = Math.max(0, Math.min(1, f));
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int r = Math.round(ar + (((b >> 16) & 255) - ar) * f);
        int g = Math.round(ag + (((b >> 8) & 255) - ag) * f);
        int bl = Math.round(ab + ((b & 255) - ab) * f);
        return (a & 0xFF000000) | r << 16 | g << 8 | bl;
    }
}
