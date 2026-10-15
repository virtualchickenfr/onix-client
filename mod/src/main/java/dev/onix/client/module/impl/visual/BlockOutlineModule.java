package dev.onix.client.module.impl.visual;

import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

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
        WorldRenderEvents.BLOCK_OUTLINE.register((context, outline) -> {
            if (!isEnabled() || context.consumers() == null) return true;
            MatrixStack matrices = context.matrixStack();
            if (matrices == null) return true;
            BlockPos pos = outline.blockPos();
            BlockState state = outline.blockState();
            VoxelShape shape = fullBlock.isOn() ? net.minecraft.util.shape.VoxelShapes.fullCube()
                    : state.getOutlineShape(context.world(), pos, ShapeContext.of(outline.entity()));
            if (shape.isEmpty()) return true;
            double ox = pos.getX() - outline.cameraX();
            double oy = pos.getY() - outline.cameraY();
            double oz = pos.getZ() - outline.cameraZ();
            int lc = lineColor.color();
            int fc = fillColor.color();
            VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());
            for (Box box : shape.getBoundingBoxes()) {
                Box b = box.offset(ox, oy, oz).expand(0.002);
                if (fill.isOn()) {
                    VertexConsumer quads = context.consumers().getBuffer(RenderLayer.getDebugFilledBox());
                    WorldRenderer.renderFilledBox(matrices, quads, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ,
                            r(fc), g(fc), bl(fc), a(fc));
                    lines = context.consumers().getBuffer(RenderLayer.getLines());
                }
                WorldRenderer.drawBox(matrices, lines, b, r(lc), g(lc), bl(lc), a(lc));
            }
            return false;
        });
    }

    private static float a(int c) { return ((c >>> 24) & 0xFF) / 255f; }
    private static float r(int c) { return ((c >> 16) & 0xFF) / 255f; }
    private static float g(int c) { return ((c >> 8) & 0xFF) / 255f; }
    private static float bl(int c) { return (c & 0xFF) / 255f; }
}
