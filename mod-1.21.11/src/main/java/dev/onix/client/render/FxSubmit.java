package dev.onix.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.joml.Matrix4f;

/** Queues custom geometry with the frame's renderer (1.21.9+: a submit collector). */
public final class FxSubmit {
    private FxSubmit() {}

    public interface Drawer {
        void draw(Matrix4f pose, VertexConsumer vc);
    }

    public static void submit(SubmitNodeCollector collector, PoseStack matrices, RenderType type, Drawer drawer) {
        collector.submitCustomGeometry(matrices, type, (pose, vc) -> drawer.draw(pose.pose(), vc));
    }
}
