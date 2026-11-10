package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.module.OverlayRenderer;
import dev.onix.client.module.impl.pack.Events;
import dev.onix.client.module.impl.pack.Events.Event;
import dev.onix.client.render.FxDraw;
import dev.onix.client.render.WorldDraw;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Glowing hit feedback: a ring that bursts out where your hit lands, a splash of light on the ground with sparks,
 * stars on crits, a pillar of light on kills and energy veins at the screen edges. Only you see it.
 */
public class ImpactFxModule extends Module implements OverlayRenderer {
    private static final Random RANDOM = new Random();

    private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFFC23D));
    private final NumberSetting size = add(new NumberSetting("size", "Size", 1.0, 0.5, 2.0, 0.1).unit("x"));
    private final BoolSetting ring = add(new BoolSetting("ring", "Hit Ring", true)
            .describe("A glowing ring bursts out of the spot your hit lands."));
    private final BoolSetting splash = add(new BoolSetting("splash", "Ground Splash", true)
            .describe("A splash of light spreads on the ground under the player you hit."));
    private final BoolSetting sparks = add(new BoolSetting("sparks", "Sparks", true)
            .describe("Sparks fly off every hit."));
    private final BoolSetting stars = add(new BoolSetting("stars", "Crit Stars", true)
            .describe("Critical hits throw out little stars."));
    private final BoolSetting pillar = add(new BoolSetting("pillar", "Kill Pillar", true)
            .describe("A pillar of light rises where a player dies."));
    private final ModeSetting veins = add(new ModeSetting("veins", "Screen Veins", "Kills", "Hits", "Off")
            .describe("Energy veins flash along the screen edges."));

    private record Burst(Vec3 pos, long start, int kind, float scale) {}

    private static final int RING = 0, SPLASH = 1, STAR = 2, PILLAR = 3;

    private final List<Burst> bursts = new ArrayList<>();
    private final List<float[][]> veinPaths = new ArrayList<>();
    private long veinStart;
    private float veinStrength;

    public ImpactFxModule() {
        super("impactfx", "Impact FX", "Glowing rings, ground splashes, crit stars and a light pillar on kills.", Category.VISUAL, false);
        tags("hit", "kill", "crit", "effect", "glow", "gold", "splash", "ripple");
    }

    @Override
    public void init() {
        Events.on(Event.HIT, (e, target) -> {
            if (isEnabled()) onHit(target, false);
        });
        Events.on(Event.CRIT, (e, target) -> {
            if (isEnabled()) onHit(target, true);
        });
        Events.on(Event.KILL, (e, target) -> {
            if (isEnabled()) onKill(target);
        });
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled() || bursts.isEmpty() || context.consumers() == null || context.matrices() == null) return;
            render(context.matrices(), context.consumers().getBuffer(RenderTypes.lightning()), WorldDraw.camera(context));
        });
    }

    @Override
    protected void onDisable() {
        bursts.clear();
        veinPaths.clear();
    }

    // ------------------------------------------------------------------ events

    private void onHit(Entity target, boolean crit) {
        if (target == null) return;
        long now = System.currentTimeMillis();
        float s = size.getFloat();
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6, 0);
        if (crit) {
            if (stars.isOn()) {
                add(new Burst(chest, now, STAR, s));
                particles(chest, ParticleTypes.END_ROD, 8, 0.25);
            }
            return; // the normal hit event already drew the ring and splash
        }
        if (ring.isOn()) add(new Burst(chest, now, RING, s));
        if (splash.isOn()) add(new Burst(target.position(), now, SPLASH, s));
        if (sparks.isOn()) particles(chest, ParticleTypes.WAX_ON, 6, 0.35);
        if (veins.is("Hits")) startVeins(0.55f);
    }

    private void onKill(Entity target) {
        if (target == null) return;
        long now = System.currentTimeMillis();
        float s = size.getFloat();
        if (pillar.isOn()) add(new Burst(target.position(), now, PILLAR, s));
        if (splash.isOn()) add(new Burst(target.position(), now, SPLASH, s * 1.8f));
        if (sparks.isOn()) particles(target.position().add(0, 1, 0), ParticleTypes.END_ROD, 20, 0.6);
        if (!veins.is("Off")) startVeins(1f);
    }

    private void add(Burst b) {
        bursts.add(b);
        if (bursts.size() > 48) bursts.remove(0);
    }

    private void particles(Vec3 at, net.minecraft.core.particles.SimpleParticleType type, int count, double speed) {
        if (mc().level == null) return;
        for (int i = 0; i < count; i++) {
            mc().level.addParticle(type, at.x, at.y, at.z,
                    (RANDOM.nextDouble() - 0.5) * speed, RANDOM.nextDouble() * speed * 0.6, (RANDOM.nextDouble() - 0.5) * speed);
        }
    }

    // ------------------------------------------------------------------ world

    private static float life(int kind) {
        return switch (kind) {
            case RING -> 450f;
            case SPLASH -> 700f;
            case STAR -> 600f;
            default -> 1300f;
        };
    }

    private void render(PoseStack m, VertexConsumer vc, Vec3 cam) {
        long now = System.currentTimeMillis();
        bursts.removeIf(b -> now - b.start() > life(b.kind()));
        int c = color.color();
        var camera = mc().gameRenderer.getMainCamera();
        for (Burst b : bursts) {
            float t = (now - b.start()) / life(b.kind());
            float ease = 1f - (1f - t) * (1f - t) * (1f - t);
            float fade = 1f - t;
            double x = b.pos().x - cam.x, y = b.pos().y - cam.y, z = b.pos().z - cam.z;
            switch (b.kind()) {
                case RING -> {
                    m.pushPose();
                    m.translate(x, y, z);
                    m.mulPose(camera.rotation());
                    float r = (0.25f + 0.75f * ease) * b.scale();
                    float w = 0.09f * b.scale() * fade + 0.02f;
                    flatRing(m.last().pose(), vc, r - w, r, FxDraw.withAlpha(c, 0f), FxDraw.withAlpha(c, fade));
                    flatRing(m.last().pose(), vc, r, r + w * 0.7f, FxDraw.withAlpha(0xFFFFFFFF, fade * 0.9f), FxDraw.withAlpha(c, 0f));
                    glowBoth(m.last().pose(), vc, 0.5f * b.scale() * (1f - ease * 0.5f), FxDraw.withAlpha(0xFFFFFFFF, fade * 0.8f), FxDraw.withAlpha(c, 0f));
                    m.popPose();
                }
                case SPLASH -> {
                    float r = (0.4f + 1.4f * ease) * b.scale();
                    double gy = y + 0.03;
                    FxDraw.ringBoth(m, vc, x, gy, z, 0f, r * 0.7f, FxDraw.withAlpha(0xFFFFFFFF, fade * 0.55f), FxDraw.withAlpha(c, fade * 0.45f), 40);
                    FxDraw.ringBoth(m, vc, x, gy, z, r * 0.7f, r, FxDraw.withAlpha(c, fade * 0.45f), FxDraw.withAlpha(c, 0f), 40);
                    FxDraw.ringBoth(m, vc, x, gy + 0.002, z, r * 0.92f, r, FxDraw.withAlpha(c, 0f), FxDraw.withAlpha(c, fade * 0.8f), 40);
                }
                case STAR -> {
                    m.pushPose();
                    m.translate(x, y, z);
                    m.mulPose(camera.rotation());
                    Matrix4f pose = m.last().pose();
                    long seed = b.start();
                    Random r = new Random(seed);
                    for (int i = 0; i < 6; i++) {
                        double a = r.nextDouble() * Math.PI * 2;
                        float dist = (0.3f + 0.9f * ease) * b.scale() * (0.7f + r.nextFloat() * 0.6f);
                        float sx = (float) Math.cos(a) * dist, sy = (float) Math.sin(a) * dist - t * t * 0.3f;
                        star(pose, vc, sx, sy, 0.16f * b.scale() * fade, FxDraw.withAlpha(0xFFFFFFFF, fade), FxDraw.withAlpha(c, 0f));
                    }
                    m.popPose();
                }
                default -> {
                    // pillar: rises quickly, stays a moment and fades out
                    float grow = Math.min(1f, t * 4f);
                    float out = t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.4f;
                    float h = 9f * grow * b.scale();
                    float r = 0.35f * b.scale() * (0.6f + 0.4f * out);
                    FxDraw.band(m, vc, x, y, z, r, h, FxDraw.withAlpha(0xFFFFFFFF, out * 0.9f), FxDraw.withAlpha(c, 0f), 24);
                    FxDraw.band(m, vc, x, y, z, r * 2.2f, h * 0.6f, FxDraw.withAlpha(c, out * 0.45f), FxDraw.withAlpha(c, 0f), 24);
                    FxDraw.ringBoth(m, vc, x, y + 0.03, z, 0f, r * 4f, FxDraw.withAlpha(c, out * 0.6f), FxDraw.withAlpha(c, 0f), 32);
                }
            }
        }
    }

    /** A ring in the x/y plane of the current pose (facing the camera after its rotation), both sides. */
    private static void flatRing(Matrix4f m, VertexConsumer vc, float inner, float outer, int innerColor, int outerColor) {
        int seg = 40;
        inner = Math.max(0f, inner);
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            vc.addVertex(m, c0 * inner, s0 * inner, 0).setColor(innerColor);
            vc.addVertex(m, c0 * outer, s0 * outer, 0).setColor(outerColor);
            vc.addVertex(m, c1 * outer, s1 * outer, 0).setColor(outerColor);
            vc.addVertex(m, c1 * inner, s1 * inner, 0).setColor(innerColor);
            vc.addVertex(m, c0 * inner, s0 * inner, 0).setColor(innerColor);
            vc.addVertex(m, c1 * inner, s1 * inner, 0).setColor(innerColor);
            vc.addVertex(m, c1 * outer, s1 * outer, 0).setColor(outerColor);
            vc.addVertex(m, c0 * outer, s0 * outer, 0).setColor(outerColor);
        }
    }

    /** A round glow in the x/y plane, both sides. */
    private static void glowBoth(Matrix4f m, VertexConsumer vc, float radius, int center, int edge) {
        flatRing(m, vc, 0f, radius, center, edge);
    }

    /** A four pointed star in the x/y plane, both sides. */
    private static void star(Matrix4f m, VertexConsumer vc, float x, float y, float s, int center, int edge) {
        float[][] tips = {{0, s}, {s, 0}, {0, -s}, {-s, 0}};
        float k = s * 0.22f;
        float[][] waist = {{k, k}, {k, -k}, {-k, -k}, {-k, k}};
        for (int i = 0; i < 4; i++) {
            float[] tip = tips[i];
            float[] w0 = waist[(i + 3) % 4];
            float[] w1 = waist[i];
            vc.addVertex(m, x, y, 0).setColor(center);
            vc.addVertex(m, x + w0[0], y + w0[1], 0).setColor(center);
            vc.addVertex(m, x + tip[0], y + tip[1], 0).setColor(edge);
            vc.addVertex(m, x + w1[0], y + w1[1], 0).setColor(center);
            vc.addVertex(m, x, y, 0).setColor(center);
            vc.addVertex(m, x + w1[0], y + w1[1], 0).setColor(center);
            vc.addVertex(m, x + tip[0], y + tip[1], 0).setColor(edge);
            vc.addVertex(m, x + w0[0], y + w0[1], 0).setColor(center);
        }
    }

    // ------------------------------------------------------------------ screen veins

    private void startVeins(float strength) {
        veinStart = System.currentTimeMillis();
        veinStrength = strength;
        veinPaths.clear();
        int count = 5 + RANDOM.nextInt(4);
        for (int i = 0; i < count; i++) {
            // start on a screen edge (0..1 coordinates) and wander inwards
            int edge = RANDOM.nextInt(4);
            float x = edge == 0 ? 0f : edge == 1 ? 1f : RANDOM.nextFloat();
            float y = edge == 2 ? 0f : edge == 3 ? 1f : RANDOM.nextFloat();
            double dir = Math.atan2(0.5 - y, 0.5 - x) + (RANDOM.nextDouble() - 0.5) * 1.2;
            int steps = 10 + RANDOM.nextInt(8);
            float[][] path = new float[steps][2];
            for (int s = 0; s < steps; s++) {
                path[s][0] = x;
                path[s][1] = y;
                dir += (RANDOM.nextDouble() - 0.5) * 1.1;
                float len = 0.025f + RANDOM.nextFloat() * 0.02f;
                x += (float) Math.cos(dir) * len;
                y += (float) Math.sin(dir) * len;
            }
            veinPaths.add(path);
        }
    }

    @Override
    public void renderOverlay(GuiGraphics ctx) {
        if (veinPaths.isEmpty()) return;
        float t = (System.currentTimeMillis() - veinStart) / 650f;
        if (t >= 1f) {
            veinPaths.clear();
            return;
        }
        float alpha = (t < 0.15f ? t / 0.15f : 1f - (t - 0.15f) / 0.85f) * veinStrength;
        int w = ctx.guiWidth(), h = ctx.guiHeight();
        int c = color.color();
        // a warm tint at the edges, then the veins themselves (a soft wide line under a bright thin one)
        dev.onix.client.gui.SoftShapes.vignette(ctx, 0, 0, w, h, FxDraw.withAlpha(c, alpha * 0.35f));
        for (float[][] path : veinPaths) {
            int shown = Math.max(2, Math.round(path.length * Math.min(1f, t * 3f)));
            for (int s = 0; s + 1 < shown; s++) {
                float x0 = path[s][0] * w, y0 = path[s][1] * h, x1 = path[s + 1][0] * w, y1 = path[s + 1][1] * h;
                float taper = 1f - s / (float) path.length;
                segment(ctx, x0, y0, x1, y1, 3.5f * taper + 1f, FxDraw.withAlpha(c, alpha * 0.35f));
                segment(ctx, x0, y0, x1, y1, 1.2f * taper + 0.6f, FxDraw.withAlpha(0xFFFFF4D6, alpha));
            }
        }
    }

    private static void segment(GuiGraphics ctx, float x0, float y0, float x1, float y1, float width, int color) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x0, y0);
        ctx.pose().rotate((float) Math.atan2(dy, dx));
        int hw = Math.max(1, Math.round(width / 2f));
        ctx.fill(0, -hw, Math.round(len) + 1, hw, color);
        ctx.pose().popMatrix();
    }
}
