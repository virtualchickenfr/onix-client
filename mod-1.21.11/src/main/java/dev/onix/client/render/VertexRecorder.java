package dev.onix.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.Arrays;

/**
 * Collects vertices that are already transformed by the caller's pose, then hands them to the 1.21.11 submit queue
 * in one go. This lets code written for "get a buffer, add vertices" draw through {@link SubmitNodeCollector}.
 */
public final class VertexRecorder implements VertexConsumer {
    private static final int STRIDE = 13; // x y z color u v overlayU overlayV lightU lightV nx ny nz
    private float[] data = new float[STRIDE * 256];
    private int count = -1;

    private float[] cur() {
        return data;
    }

    private int base() {
        return count * STRIDE;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        count++;
        if ((count + 1) * STRIDE > data.length) data = Arrays.copyOf(data, data.length * 2);
        int b = base();
        data[b] = x;
        data[b + 1] = y;
        data[b + 2] = z;
        data[b + 3] = Float.intBitsToFloat(0xFFFFFFFF);
        for (int i = 4; i < STRIDE; i++) data[b + i] = 0f;
        data[b + 11] = 1f;
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b2, int a) {
        return setColor((a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b2 & 0xFF));
    }

    @Override
    public VertexConsumer setColor(int argb) {
        if (count >= 0) data[base() + 3] = Float.intBitsToFloat(argb);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        if (count >= 0) {
            data[base() + 4] = u;
            data[base() + 5] = v;
        }
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        if (count >= 0) {
            data[base() + 6] = u;
            data[base() + 7] = v;
        }
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        if (count >= 0) {
            data[base() + 8] = u;
            data[base() + 9] = v;
        }
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        if (count >= 0) {
            data[base() + 10] = x;
            data[base() + 11] = y;
            data[base() + 12] = z;
        }
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }

    public boolean isEmpty() {
        return count < 0;
    }

    /** Writes everything into another consumer (positions and normals are used as they are). */
    public void replay(VertexConsumer out) {
        for (int i = 0; i <= count; i++) {
            int b = i * STRIDE;
            out.addVertex(data[b], data[b + 1], data[b + 2])
                    .setColor(Float.floatToRawIntBits(data[b + 3]))
                    .setUv(data[b + 4], data[b + 5])
                    .setUv1((int) data[b + 6], (int) data[b + 7])
                    .setUv2((int) data[b + 8], (int) data[b + 9])
                    .setNormal(data[b + 10], data[b + 11], data[b + 12]);
        }
    }

    /** Queues the recorded vertices with a render type (they already carry the full transform). */
    public void submit(SubmitNodeCollector collector, RenderType type) {
        if (isEmpty()) return;
        VertexRecorder copy = this;
        collector.submitCustomGeometry(new PoseStack(), type, (pose, vc) -> copy.replay(vc));
    }
}
