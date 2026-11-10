package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.render.FxDraw;
import dev.onix.client.render.WorldDraw;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.NumberSetting;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** A glowing ring floating above your head (third person), optionally above your friends too. Only you see it. */
public class HaloModule extends Module {
    private final ColorSetting color = add(new ColorSetting("color", "Color", 0xFFFFE7A8));
    private final NumberSetting size = add(new NumberSetting("size", "Size", 0.32, 0.2, 0.6, 0.02).unit("blocks"));
    private final BoolSetting bob = add(new BoolSetting("bob", "Float", true).describe("The halo gently floats up and down."));
    private final BoolSetting others = add(new BoolSetting("others", "Other Players Too", false));

    public HaloModule() {
        super("halo", "Halo", "A glowing ring floats above your head.", Category.VISUAL, false);
        tags("halo", "ring", "angel", "glow", "cosmetic");
    }

    @Override
    public void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled() || mc().player == null || mc().level == null || context.consumers() == null || context.matrices() == null) return;
            PoseStack m = context.matrices();
            Vec3 cam = WorldDraw.camera(context);
            float d = WorldDraw.partialTick();
            VertexConsumer vc = context.consumers().getBuffer(RenderTypes.lightning());
            boolean firstPerson = mc().options.getCameraType().isFirstPerson();
            List<? extends Player> players = others.isOn() ? mc().level.players() : List.of((Player) mc().player);
            float r = size.getFloat();
            double wave = bob.isOn() ? Math.sin(System.currentTimeMillis() / 420.0) * 0.04 : 0;
            int c = color.color();
            for (Player p : players) {
                if (p == mc().player && firstPerson) continue;
                if (p.isInvisible() || p.distanceTo(mc().player) > 48) continue;
                Vec3 pos = p.getPosition(d).subtract(cam);
                double y = pos.y + p.getBbHeight() + 0.28 + wave;
                // a soft wide glow under a bright thin core, seen from above and below
                FxDraw.ringBoth(m, vc, pos.x, y, pos.z, r - 0.09f, r, FxDraw.withAlpha(c, 0f), FxDraw.withAlpha(c, 0.7f), 48);
                FxDraw.ringBoth(m, vc, pos.x, y, pos.z, r, r + 0.09f, FxDraw.withAlpha(c, 0.7f), FxDraw.withAlpha(c, 0f), 48);
                FxDraw.ringBoth(m, vc, pos.x, y + 0.001, pos.z, r - 0.025f, r + 0.025f, FxDraw.withAlpha(0xFFFFFFFF, 0.95f), FxDraw.withAlpha(0xFFFFFFFF, 0.95f), 48);
                FxDraw.band(m, vc, pos.x, y + 0.03, pos.z, r, -0.06f, FxDraw.withAlpha(c, 0.5f), FxDraw.withAlpha(c, 0f), 48);
            }
        });
    }
}
