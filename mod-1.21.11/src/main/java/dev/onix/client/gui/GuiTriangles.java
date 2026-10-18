package dev.onix.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/** Coloured triangles in menus (1.21.11 GUI rendering has no triangle primitive; each one is a quad with a repeated corner). */
public record GuiTriangles(Matrix3x2f pose, float[] xy, int[] colors, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
        implements GuiElementRenderState {

    public static void draw(GuiGraphics ctx, float[] xy, int[] colors) {
        if (colors.length < 3) return;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int i = 0; i < colors.length; i++) {
            minX = Math.min(minX, xy[i * 2]);
            maxX = Math.max(maxX, xy[i * 2]);
            minY = Math.min(minY, xy[i * 2 + 1]);
            maxY = Math.max(maxY, xy[i * 2 + 1]);
        }
        Matrix3x2f pose = new Matrix3x2f(ctx.pose());
        ScreenRectangle scissor = ctx.scissorStack.peek();
        ScreenRectangle area = new ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY),
                Math.max(1, (int) Math.ceil(maxX - minX) + 1), Math.max(1, (int) Math.ceil(maxY - minY) + 1)).transformMaxBounds(pose);
        if (scissor != null) area = scissor.intersection(area);
        if (area == null) return;
        ctx.guiRenderState.submitGuiElement(new GuiTriangles(pose, xy.clone(), colors.clone(), scissor, area));
    }

    @Override
    public void buildVertices(VertexConsumer vc) {
        for (int t = 0; t + 2 < colors.length; t += 3) {
            for (int k = 0; k < 4; k++) {
                int v = t + Math.min(k, 2);
                vc.addVertexWith2DPose(pose, xy[v * 2], xy[v * 2 + 1]).setColor(colors[v]);
            }
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
