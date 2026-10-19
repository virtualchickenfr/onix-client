package dev.onix.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/** Draws custom geometry straight into the frame's buffers (before 1.21.9 there is no submit collector). */
public final class FxSubmit {
    private FxSubmit() {}

    public interface Drawer {
        void draw(Matrix4f pose, VertexConsumer vc);
    }

    public static void submit(MultiBufferSource buffers, PoseStack matrices, RenderType type, Drawer drawer) {
        drawer.draw(matrices.last().pose(), buffers.getBuffer(type));
    }
}
