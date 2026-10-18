package dev.onix.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.LinkedHashMap;
import java.util.Map;

/** A buffer source that records per render type and then queues everything on a {@link SubmitNodeCollector}. */
public final class RecordingBuffers implements MultiBufferSource {
    private final Map<RenderType, VertexRecorder> buffers = new LinkedHashMap<>();

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return buffers.computeIfAbsent(type, t -> new VertexRecorder());
    }

    public void submit(SubmitNodeCollector collector) {
        buffers.forEach((type, rec) -> rec.submit(collector, type));
    }
}
