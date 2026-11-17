package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.render.WorldDraw;
import dev.onix.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/** A countdown above dropped items: how long until they vanish (5 minutes after they were dropped). */
public class DespawnTimerModule extends Module {
    private static final int LIFESPAN = 6000;

    private final NumberSetting range = add(new NumberSetting("range", "Range", 24, 4, 64, 4).unit("blocks"));
    private final NumberSetting below = add(new NumberSetting("below", "Only Below", 300, 10, 300, 10).unit("s")
            .describe("Only show items that vanish within this time."));
    private final NumberSetting size = add(new NumberSetting("size", "Size", 1.0, 0.5, 2.5, 0.1).unit("x"));

    public DespawnTimerModule() {
        super("despawntimer", "Despawn Timer", "A countdown above dropped items until they vanish.", Category.VISUAL, false);
        tags("despawn", "items", "drop", "timer", "countdown");
    }

    @Override
    public void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled() || mc().level == null || context.consumers() == null || context.matrices() == null) return;
            PoseStack m = context.matrices();
            Vec3 cam = WorldDraw.camera(context);
            float delta = WorldDraw.partialTick();
            Font font = mc().font;
            double max = range.get() * range.get();
            for (Entity e : mc().level.entitiesForRendering()) {
                if (!(e instanceof ItemEntity item) || item.hasPickUpDelay() && item.getAge() < 10) continue;
                Vec3 pos = e.getPosition(delta);
                if (pos.distanceToSqr(cam) > max) continue;
                int left = Math.max(0, LIFESPAN - item.getAge()) / 20;
                if (left > below.get()) continue;
                String text = left >= 60 ? (left / 60) + ":" + String.format("%02d", left % 60) : left + "s";
                // green with plenty of time, orange in the last minute, red in the last 10 seconds
                int color = left <= 10 ? 0xFFFF4D5E : left <= 60 ? 0xFFFFB347 : 0xFF7CF2A0;
                float s = 0.02f * size.getFloat();
                m.pushPose();
                m.translate(pos.x - cam.x, pos.y - cam.y + 0.75, pos.z - cam.z);
                m.mulPose(mc().gameRenderer.getMainCamera().rotation());
                m.scale(s, -s, s);
                font.drawInBatch(text, -font.width(text) / 2f, 0, color, false, m.last().pose(), context.consumers(),
                        Font.DisplayMode.NORMAL, 0x55000000, 0xF000F0);
                m.popPose();
            }
        });
    }
}
