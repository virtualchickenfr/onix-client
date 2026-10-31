package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.render.WorldDraw;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.Locale;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.gui.Font;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;

/** A countdown above lit TNT (and TNT minecarts): how long until it explodes, coloured from green to red. */
public class TntTimerModule extends Module {
    private final ModeSetting format = add(new ModeSetting("format", "Format", "Seconds", "Ticks"));
    private final BoolSetting colored = add(new BoolSetting("colored", "Color by Time", true)
            .describe("Green while there is time, red just before it explodes."));
    private final BoolSetting throughWalls = add(new BoolSetting("throughWalls", "Show Through Walls", true));
    private final BoolSetting minecarts = add(new BoolSetting("minecarts", "TNT Minecarts", true));
    private final BoolSetting background = add(new BoolSetting("background", "Background", true));
    private final NumberSetting size = add(new NumberSetting("size", "Size", 1.2, 0.5, 3, 0.1).unit("x"));
    private final NumberSetting range = add(new NumberSetting("range", "Range", 64, 8, 128, 8).unit("blocks"));

    public TntTimerModule() {
        super("tnttimer", "TNT Timer", "A countdown above lit TNT until it explodes.", Category.VISUAL, false);
        tags("tnt", "fuse", "explosion", "timer", "countdown");
    }

    /** Ticks left on a lit TNT or TNT minecart, or -1. */
    private int fuse(Entity e) {
        if (e instanceof PrimedTnt tnt) return tnt.getFuse();
        if (minecarts.isOn() && e instanceof net.minecraft.world.entity.vehicle.minecart.MinecartTNT cart && cart.isPrimed()) return cart.getFuse();
        return -1;
    }

    private static int color(float seconds) {
        // green (4 s and more) → yellow → red (0 s)
        float t = Math.max(0f, Math.min(1f, seconds / 4f));
        int r = t > 0.5f ? Math.round(255 * (1 - t) * 2) : 255;
        int g = t > 0.5f ? 255 : Math.round(255 * t * 2);
        return 0xFF000000 | r << 16 | g << 8 | 0x40;
    }

    @Override
    public void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled() || mc().level == null || context.consumers() == null || context.matrices() == null) return;
            PoseStack matrices = context.matrices();
            Vec3 cam = WorldDraw.camera(context);
            float delta = WorldDraw.partialTick();
            Font font = mc().font;
            double max = range.get() * range.get();
            for (Entity e : mc().level.entitiesForRendering()) {
                int fuse = fuse(e);
                if (fuse < 0) continue;
                Vec3 pos = e.getPosition(delta);
                if (pos.distanceToSqr(cam) > max) continue;
                float ticksLeft = Math.max(0f, fuse - delta);
                float seconds = ticksLeft / 20f;
                String text = format.is("Ticks") ? String.valueOf(Math.max(0, Math.round(ticksLeft))) : String.format(Locale.ROOT, "%.1fs", seconds);
                int color = colored.isOn() ? color(seconds) : 0xFFFFFFFF;
                // a small pulse in the last second
                float pulse = seconds < 1f ? 1f + 0.12f * (float) Math.abs(Math.sin(seconds * Math.PI * 4)) : 1f;
                float s = 0.025f * size.getFloat() * pulse;
                matrices.pushPose();
                matrices.translate(pos.x - cam.x, pos.y - cam.y + e.getBbHeight() + 0.45, pos.z - cam.z);
                matrices.mulPose(mc().gameRenderer.getMainCamera().rotation());
                matrices.scale(s, -s, s);
                float x = -font.width(text) / 2f;
                int bg = background.isOn() ? 0x55000000 : 0;
                Font.DisplayMode mode = throughWalls.isOn() ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
                font.drawInBatch(text, x, 0, throughWalls.isOn() ? (color & 0x00FFFFFF) | 0x90000000 : color, false,
                        matrices.last().pose(), context.consumers(), mode, bg, 0xF000F0);
                if (throughWalls.isOn()) {
                    // full colour where the TNT is in plain sight
                    font.drawInBatch(text, x, 0, color, false, matrices.last().pose(), context.consumers(), Font.DisplayMode.NORMAL, 0, 0xF000F0);
                }
                matrices.popPose();
            }
        });
    }
}
