package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.render.FxDraw;
import dev.onix.client.render.WorldDraw;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Raindrops leave little rings that spread on the ground and on water around you. Only you see them. */
public class RainRipplesModule extends Module {
    private static final Random RANDOM = new Random();

    private final NumberSetting amount = add(new NumberSetting("amount", "Amount", 6, 1, 20, 1).unit("/tick"));
    private final NumberSetting range = add(new NumberSetting("range", "Range", 10, 4, 24, 1).unit("blocks"));
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0x80D8E6FF));

    private record Ripple(Vec3 pos, long start, float size) {}

    private final List<Ripple> ripples = new ArrayList<>();

    public RainRipplesModule() {
        super("rainripples", "Rain Ripples", "Raindrops leave little rings on the ground and on water.", Category.VISUAL, false);
        tags("rain", "ripple", "puddle", "weather", "wet");
    }

    @Override
    public void onTick() {
        if (mc().level == null || mc().player == null || !mc().level.isRaining()) return;
        BlockPos me = mc().player.blockPosition();
        int r = range.getInt();
        for (int i = 0; i < amount.getInt(); i++) {
            int x = me.getX() + RANDOM.nextInt(r * 2 + 1) - r;
            int z = me.getZ() + RANDOM.nextInt(r * 2 + 1) - r;
            int y = mc().level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos top = new BlockPos(x, y, z);
            if (Math.abs(y - me.getY()) > 12 || !mc().level.isRainingAt(top)) continue;
            // water surfaces sit a little lower than a full block
            double surface = mc().level.getFluidState(top.below()).isEmpty() ? y : y - 0.11;
            ripples.add(new Ripple(new Vec3(x + RANDOM.nextDouble(), surface + 0.02, z + RANDOM.nextDouble()),
                    System.currentTimeMillis(), 0.18f + RANDOM.nextFloat() * 0.2f));
        }
        if (ripples.size() > 400) ripples.subList(0, ripples.size() - 400).clear();
    }

    @Override
    protected void onDisable() {
        ripples.clear();
    }

    @Override
    public void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled() || ripples.isEmpty() || context.consumers() == null || context.matrices() == null) return;
            long now = System.currentTimeMillis();
            ripples.removeIf(rp -> now - rp.start() > 800);
            Vec3 cam = WorldDraw.camera(context);
            VertexConsumer vc = context.consumers().getBuffer(RenderTypes.debugQuads());
            int c = color.color();
            for (Ripple rp : ripples) {
                float t = (now - rp.start()) / 800f;
                float ease = 1f - (1f - t) * (1f - t);
                float radius = rp.size() * (0.2f + ease);
                float fade = 1f - t;
                Vec3 p = rp.pos().subtract(cam);
                FxDraw.ringUp(context.matrices(), vc, p.x, p.y, p.z, radius * 0.86f, radius, FxDraw.withAlpha(c, fade), FxDraw.withAlpha(c, fade), 20);
                if (t > 0.3f) {
                    float r2 = radius * 0.55f;
                    FxDraw.ringUp(context.matrices(), vc, p.x, p.y + 0.001, p.z, r2 * 0.85f, r2, FxDraw.withAlpha(c, fade * 0.6f), FxDraw.withAlpha(c, fade * 0.6f), 16);
                }
            }
        });
    }
}
