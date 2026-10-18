package dev.onix.client.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.CustomSkyModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws Custom Sky sky boxes in place of the vanilla overworld sky (SkyRendererMixin). Minecraft 1.21.11 renders
 * through render passes: the box is a static vertex buffer drawn with the End sky pipeline, its brightness and
 * fade come from the colour modulator.
 */
public final class SkyRenderer {
    private static GpuBuffer boxBuffer;

    private SkyRenderer() {}

    private static CustomSkyModule module() {
        return OnixClient.MODULES == null ? null : OnixClient.MODULES.get(CustomSkyModule.class);
    }

    /** True while a custom sky replaces the vanilla one. */
    public static boolean active() {
        CustomSkyModule m = module();
        return m != null && Minecraft.getInstance().level != null && m.appliesHere();
    }

    public static boolean sunAndMoon() {
        CustomSkyModule m = module();
        return m == null || m.sunAndMoon.isOn();
    }

    private static float partialTick() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    }

    /** The classic time-of-day curve (0 = noon … 0.5 = midnight). */
    private static float timeOfDay(ClientLevel world) {
        double d = Mth.frac((world.getDayTime() + partialTick()) / 24000.0 - 0.25);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return (float) (d * 2.0 + e) / 3.0f;
    }

    /** 1 at noon, 0 at night, smooth in between (same curve vanilla used for sky brightness). */
    private static float daylight(ClientLevel world) {
        return Mth.clamp(Mth.cos(timeOfDay(world) * ((float) Math.PI * 2f)) * 2f + 0.5f, 0f, 1f);
    }

    /** @return true if the custom sky was drawn and the vanilla sky disc must be skipped. */
    public static boolean render() {
        CustomSkyModule m = module();
        ClientLevel world = Minecraft.getInstance().level;
        if (m == null || world == null || !m.appliesHere()) return false;

        float day = daylight(world);
        float rain = world.getRainLevel(partialTick());
        float base = m.brightness.getFloat() / 100f * (1f - rain * 0.45f);
        Skies.Sky daySky = m.day();
        Skies.Sky nightSky = m.night();

        Matrix4f rotation = new Matrix4f();
        float minutes = (System.currentTimeMillis() % 86_400_000L) / 60_000f;
        rotation.rotate(Axis.YP.rotationDegrees(minutes * m.rotation.getFloat()));

        if (nightSky == null) {
            float shade = m.darkenAtNight.isOn() ? Mth.lerp(day, 0.22f, 1f) : 1f;
            box(rotation, daySky.texture(), base * shade, 1f);
        } else {
            box(rotation, nightSky.texture(), base, 1f);
            if (day > 0.001f) box(rotation, daySky.texture(), base, day);
        }
        return true;
    }

    /** Six faces exactly like vanilla's End sky, but each sampling its own cell of the 3 x 2 atlas. */
    private static GpuBuffer buffer() {
        if (boxBuffer != null) return boxBuffer;
        VertexFormat format = DefaultVertexFormat.POSITION_TEX_COLOR;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(24 * format.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, format);
            // about half a texel inwards so smooth filtering never samples the neighbouring face
            float inset = 0.0007f;
            PoseStack face = new PoseStack();
            for (int i = 0; i < 6; i++) {
                face.pushPose();
                switch (i) {
                    case 1 -> face.mulPose(Axis.XP.rotationDegrees(90f));
                    case 2 -> face.mulPose(Axis.XP.rotationDegrees(-90f));
                    case 3 -> face.mulPose(Axis.XP.rotationDegrees(180f));
                    case 4 -> face.mulPose(Axis.ZP.rotationDegrees(90f));
                    case 5 -> face.mulPose(Axis.ZP.rotationDegrees(-90f));
                    default -> {}
                }
                float u0 = (i % 3) / 3f + inset, u1 = (i % 3 + 1) / 3f - inset;
                float v0 = (i / 3) / 2f + inset, v1 = (i / 3 + 1) / 2f - inset;
                Matrix4f mat = face.last().pose();
                b.addVertex(mat, -100f, -100f, -100f).setUv(u0, v0).setColor(-1);
                b.addVertex(mat, -100f, -100f, 100f).setUv(u0, v1).setColor(-1);
                b.addVertex(mat, 100f, -100f, 100f).setUv(u1, v1).setColor(-1);
                b.addVertex(mat, 100f, -100f, -100f).setUv(u1, v0).setColor(-1);
                face.popPose();
            }
            try (MeshData mesh = b.buildOrThrow()) {
                boxBuffer = RenderSystem.getDevice().createBuffer(() -> "Onix sky box", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            }
        }
        return boxBuffer;
    }

    private static void box(Matrix4f rotation, Identifier texture, float brightness, float alpha) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
        if (tex == null) return;
        float c = Mth.clamp(brightness, 0f, 1f);
        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.mul(rotation);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(view, new Vector4f(c, c, c, alpha), new Vector3f(), new Matrix4f());
        view.popMatrix();
        var target = Minecraft.getInstance().getMainRenderTarget();
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(36);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Onix sky", target.getColorTextureView(), OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(RenderPipelines.END_SKY);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.bindTexture("Sampler0", tex.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.setVertexBuffer(0, buffer());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(0, 0, 36, 1);
        }
    }

    /** The horizon colour of the current sky (ARGB), for fog blending; null when the vanilla sky is used. */
    public static Integer horizonColor() {
        CustomSkyModule m = module();
        ClientLevel world = Minecraft.getInstance().level;
        if (m == null || world == null || !m.appliesHere() || !m.matchFog.isOn()) return null;
        float day = daylight(world);
        int dayColor = m.day().horizon();
        Skies.Sky night = m.night();
        int nightColor = night != null ? night.horizon() : dayColor;
        float shade = night == null && m.darkenAtNight.isOn() ? Mth.lerp(day, 0.22f, 1f) : 1f;
        float k = m.brightness.getFloat() / 100f * shade;
        float t = night == null ? 1f : day;
        int r = Math.round(Mth.lerpInt(t, (nightColor >> 16 & 0xFF), (dayColor >> 16 & 0xFF)) * k);
        int g = Math.round(Mth.lerpInt(t, (nightColor >> 8 & 0xFF), (dayColor >> 8 & 0xFF)) * k);
        int b = Math.round(Mth.lerpInt(t, (nightColor & 0xFF), (dayColor & 0xFF)) * k);
        return 0xFF000000 | Mth.clamp(r, 0, 255) << 16 | Mth.clamp(g, 0, 255) << 8 | Mth.clamp(b, 0, 255);
    }
}
