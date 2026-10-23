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
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/** Glowing rings that spread out on the ground where you jump (and land). Only you see them. */
public class JumpCirclesModule extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Glow", "Ring", "Double", "Filled"));
    private final ColorSetting color = add(ColorSetting.accent("color", "Color"));
    private final BoolSetting rainbow = add(new BoolSetting("rainbow", "Rainbow", false));
    private final NumberSetting size = add(new NumberSetting("size", "Size", 1.2, 0.4, 3, 0.1).unit("blocks"));
    private final NumberSetting duration = add(new NumberSetting("duration", "Duration", 0.9, 0.3, 2.5, 0.1).unit("s"));
    private final BoolSetting onLand = add(new BoolSetting("land", "Also on Landing", true));
    private final BoolSetting others = add(new BoolSetting("others", "Other Players Too", false));

    private record Circle(Vec3 pos, long start, int color) {}

    private final List<Circle> circles = new ArrayList<>();
    private final java.util.Map<Integer, Boolean> grounded = new java.util.HashMap<>();
    private float hue;

    public JumpCirclesModule() {
        super("jumpcircles", "Jump Circles", "Rings spread out on the ground where you jump and land.", Category.VISUAL, false);
        tags("jump", "circle", "ring", "effect", "cosmetic");
    }

    /** Where a player last stood on the ground and their height last tick, per entity id. */
    private final java.util.Map<Integer, Vec3> groundPos = new java.util.HashMap<>();
    private final java.util.Map<Integer, Double> lastY = new java.util.HashMap<>();

    @Override
    public void onTick() {
        if (mc().level == null || mc().player == null) return;
        var players = others.isOn() ? mc().level.players() : List.of(mc().player);
        for (var p : players) {
            int id = p.getId();
            boolean on = p.onGround();
            Boolean before = grounded.put(id, on);
            Double prevY = lastY.put(id, p.getY());
            if (on) groundPos.put(id, p.position());
            if (before == null || before == on || p.isPassenger() || p.isFallFlying()) continue;
            // a jump: left the ground going up (the height change also works for other players, whose
            // movement the client does not know); the ring goes where the feet were, not in the air
            if (before && !on && prevY != null && p.getY() - prevY > 0.05) {
                Vec3 g = groundPos.get(id);
                add(g != null ? g : p.position());
            } else if (!before && on && onLand.isOn()) {
                add(p.position());
            }
        }
        if (grounded.size() > 256) {
            grounded.clear();
            groundPos.clear();
            lastY.clear();
        }
    }

    private void add(Vec3 pos) {
        hue += 0.13f;
        int c = rainbow.isOn() ? FxDraw.rainbow(hue) : color.color();
        circles.add(new Circle(pos, System.currentTimeMillis(), c));
        if (circles.size() > 64) circles.remove(0);
    }

    @Override
    protected void onDisable() {
        circles.clear();
        grounded.clear();
        groundPos.clear();
        lastY.clear();
    }

    @Override
    public void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled() || circles.isEmpty() || context.consumers() == null) return;
            PoseStack matrices = context.matrices();
            if (matrices == null) return;
            Vec3 cam = WorldDraw.camera(context);
            long now = System.currentTimeMillis();
            double life = duration.get() * 1000;
            circles.removeIf(c -> now - c.start() > life * 1.35);
            boolean glow = style.is("Glow");
            VertexConsumer vc = context.consumers().getBuffer(glow ? RenderTypes.lightning() : RenderTypes.debugQuads());
            float s = size.getFloat();
            for (Circle c : circles) {
                double x = c.pos().x - cam.x, y = c.pos().y - cam.y + 0.03, z = c.pos().z - cam.z;
                float t = (float) ((now - c.start()) / life);
                draw(matrices, vc, x, y, z, t, s, c.color());
                if (style.is("Double")) draw(matrices, vc, x, y + 0.001, z, t - 0.28f, s * 0.7f, c.color());
            }
        });
    }

    private void draw(PoseStack matrices, VertexConsumer vc, double x, double y, double z, float t, float s, int color) {
        if (t <= 0 || t >= 1) return;
        float ease = 1f - (1f - t) * (1f - t) * (1f - t);
        float radius = Math.max(0.05f, s * ease);
        float fade = 1f - t;
        int solid = FxDraw.withAlpha(color, fade);
        int clear = FxDraw.withAlpha(color, 0f);
        switch (style.get()) {
            case "Filled" -> FxDraw.ringUp(matrices, vc, x, y, z, 0f, radius, FxDraw.withAlpha(color, fade * 0.15f), FxDraw.withAlpha(color, fade * 0.6f), 48);
            case "Glow" -> {
                float w = 0.22f * s;
                FxDraw.ringUp(matrices, vc, x, y, z, Math.max(0, radius - w), radius, clear, solid, 48);
                FxDraw.ringUp(matrices, vc, x, y, z, radius, radius + w * 0.6f, solid, clear, 48);
            }
            default -> {
                float w = 0.06f * s;
                FxDraw.ringUp(matrices, vc, x, y, z, Math.max(0, radius - w), radius + w, solid, solid, 48);
            }
        }
    }
}
