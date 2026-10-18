package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import dev.onix.client.render.WorldDraw;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Custom color and fill for the outline of the block you look at. */
public class BlockOutlineModule extends Module {
    private final ColorSetting lineColor = add(ColorSetting.accent("lineColor", "Outline Color"));
    private final BoolSetting fill = add(new BoolSetting("fill", "Fill", true));
    private final ColorSetting fillColor = add(new ColorSetting("fillColor", "Fill Color", 0x30FFFFFF).visibleIf(fill::isOn));
    private final BoolSetting fullBlock = add(new BoolSetting("fullBlock", "Use Full Block", false)
            .describe("Draw a full cube instead of the exact shape (stairs, slabs …)."));

    public BlockOutlineModule() {
        super("blockoutline", "Block Outline", "Color and fill for the block selection outline.", Category.VISUAL, false);
        tags("block overlay", "selection", "highlight");
    }

    @Override
    public void init() {
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
            if (!isEnabled() || context.consumers() == null || outline == null) return true;
            PoseStack matrices = context.matrices();
            if (matrices == null) return true;
            BlockPos pos = outline.pos();
            VoxelShape shape = fullBlock.isOn() ? net.minecraft.world.phys.shapes.Shapes.block() : outline.shape();
            if (shape == null || shape.isEmpty()) return true;
            net.minecraft.world.phys.Vec3 cam = WorldDraw.camera(context);
            double ox = pos.getX() - cam.x;
            double oy = pos.getY() - cam.y;
            double oz = pos.getZ() - cam.z;
            int lc = lineColor.color();
            int fc = fillColor.color();
            VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
            for (AABB box : shape.toAabbs()) {
                AABB b = box.move(ox, oy, oz).inflate(0.002);
                if (fill.isOn()) {
                    VertexConsumer quads = context.consumers().getBuffer(RenderTypes.debugFilledBox());
                    WorldDraw.filledBox(matrices, quads, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ,
                            r(fc), g(fc), bl(fc), a(fc));
                    lines = context.consumers().getBuffer(RenderTypes.lines());
                }
                WorldDraw.lineBox(matrices, lines, b, r(lc), g(lc), bl(lc), a(lc));
            }
            return false;
        });
    }

    private static float a(int c) { return ((c >>> 24) & 0xFF) / 255f; }
    private static float r(int c) { return ((c >> 16) & 0xFF) / 255f; }
    private static float g(int c) { return ((c >> 8) & 0xFF) / 255f; }
    private static float bl(int c) { return (c & 0xFF) / 255f; }
}
