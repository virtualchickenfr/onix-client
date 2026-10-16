package dev.onix.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.CustomSkyModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Draws Custom Sky sky boxes in place of the vanilla overworld sky (WorldRendererMixin). */
public final class SkyRenderer {
    private static final ResourceLocation SUN = ResourceLocation.withDefaultNamespace("textures/environment/sun.png");
    private static final ResourceLocation MOON = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");

    private SkyRenderer() {}

    private static CustomSkyModule module() {
        return OnixClient.MODULES == null ? null : OnixClient.MODULES.get(CustomSkyModule.class);
    }

    /** 1 at noon, 0 at night, smooth in between (same curve vanilla uses for sky brightness). */
    private static float daylight(ClientLevel world, float tickDelta) {
        float angle = world.getTimeOfDay(tickDelta);
        return Mth.clamp(Mth.cos(angle * ((float) Math.PI * 2f)) * 2f + 0.5f, 0f, 1f);
    }

    /** @return true if the custom sky was drawn and the vanilla sky must be skipped. */
    public static boolean render(Matrix4f viewMatrix, float tickDelta) {
        CustomSkyModule m = module();
        ClientLevel world = Minecraft.getInstance().level;
        if (m == null || world == null || !m.appliesHere()) return false;

        float day = daylight(world, tickDelta);
        float rain = world.getRainLevel(tickDelta);
        float base = m.brightness.getFloat() / 100f * (1f - rain * 0.45f);
        Skies.Sky daySky = m.day();
        Skies.Sky nightSky = m.night();

        PoseStack matrices = new PoseStack();
        matrices.mulPose(viewMatrix);
        float minutes = (System.currentTimeMillis() % 86_400_000L) / 60_000f;
        matrices.mulPose(Axis.YP.rotationDegrees(minutes * m.rotation.getFloat()));

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (nightSky == null) {
            float shade = m.darkenAtNight.isOn() ? Mth.lerp(day, 0.22f, 1f) : 1f;
            box(matrices, daySky.texture(), base * shade, 1f);
        } else {
            box(matrices, nightSky.texture(), base, 1f);
            if (day > 0.001f) box(matrices, daySky.texture(), base, day);
        }
        RenderSystem.enableCull();

        if (m.sunAndMoon.isOn()) sunAndMoon(viewMatrix, world, tickDelta, 1f - rain);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        return true;
    }

    /** Six faces exactly like vanilla's End sky, but each sampling its own cell of the 3 x 2 atlas. */
    private static void box(PoseStack matrices, ResourceLocation texture, float brightness, float alpha) {
        Skies.ensureFiltered(texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        int c = Math.round(Mth.clamp(brightness, 0f, 1f) * 255f);
        int color = (Math.round(alpha * 255f) << 24) | (c << 16) | (c << 8) | c;
        // about half a texel inwards so smooth filtering never samples the neighbouring face
        float inset = 0.0007f;
        for (int i = 0; i < 6; i++) {
            matrices.pushPose();
            switch (i) {
                case 1 -> matrices.mulPose(Axis.XP.rotationDegrees(90f));
                case 2 -> matrices.mulPose(Axis.XP.rotationDegrees(-90f));
                case 3 -> matrices.mulPose(Axis.XP.rotationDegrees(180f));
                case 4 -> matrices.mulPose(Axis.ZP.rotationDegrees(90f));
                case 5 -> matrices.mulPose(Axis.ZP.rotationDegrees(-90f));
                default -> {}
            }
            float u0 = (i % 3) / 3f + inset, u1 = (i % 3 + 1) / 3f - inset;
            float v0 = (i / 3) / 2f + inset, v1 = (i / 3 + 1) / 2f - inset;
            Matrix4f mat = matrices.last().pose();
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            b.addVertex(mat, -100f, -100f, -100f).setUv(u0, v0).setColor(color);
            b.addVertex(mat, -100f, -100f, 100f).setUv(u0, v1).setColor(color);
            b.addVertex(mat, 100f, -100f, 100f).setUv(u1, v1).setColor(color);
            b.addVertex(mat, 100f, -100f, -100f).setUv(u1, v0).setColor(color);
            BufferUploader.drawWithShader(b.buildOrThrow());
            matrices.popPose();
        }
    }

    /** Vanilla sun and moon, following the time of day. */
    private static void sunAndMoon(Matrix4f viewMatrix, ClientLevel world, float tickDelta, float alpha) {
        PoseStack matrices = new PoseStack();
        matrices.mulPose(viewMatrix);
        matrices.mulPose(Axis.YP.rotationDegrees(-90f));
        matrices.mulPose(Axis.XP.rotationDegrees(world.getTimeOfDay(tickDelta) * 360f));
        Matrix4f m = matrices.last().pose();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        float k = 30f;
        RenderSystem.setShaderTexture(0, SUN);
        BufferBuilder sun = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        sun.addVertex(m, -k, 100f, -k).setUv(0f, 0f);
        sun.addVertex(m, k, 100f, -k).setUv(1f, 0f);
        sun.addVertex(m, k, 100f, k).setUv(1f, 1f);
        sun.addVertex(m, -k, 100f, k).setUv(0f, 1f);
        BufferUploader.drawWithShader(sun.buildOrThrow());

        k = 20f;
        RenderSystem.setShaderTexture(0, MOON);
        int phase = world.getMoonPhase();
        int px = phase % 4;
        int py = phase / 4 % 2;
        float u0 = px / 4f, v0 = py / 2f, u1 = (px + 1) / 4f, v1 = (py + 1) / 2f;
        BufferBuilder moon = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        moon.addVertex(m, -k, -100f, k).setUv(u1, v1);
        moon.addVertex(m, k, -100f, k).setUv(u0, v1);
        moon.addVertex(m, k, -100f, -k).setUv(u0, v0);
        moon.addVertex(m, -k, -100f, -k).setUv(u1, v0);
        BufferUploader.drawWithShader(moon.buildOrThrow());
    }

    /** The horizon color of the current sky, for fog blending; null when the vanilla sky is used. */
    public static Vec3 horizonColor(float tickDelta) {
        CustomSkyModule m = module();
        ClientLevel world = Minecraft.getInstance().level;
        if (m == null || world == null || !m.appliesHere() || !m.matchFog.isOn()) return null;
        float day = daylight(world, tickDelta);
        int dayColor = m.day().horizon();
        Skies.Sky night = m.night();
        int nightColor = night != null ? night.horizon() : dayColor;
        float shade = night == null && m.darkenAtNight.isOn() ? Mth.lerp(day, 0.22f, 1f) : 1f;
        float k = m.brightness.getFloat() / 100f * shade;
        float t = night == null ? 1f : day;
        double r = Mth.lerpInt(t, (nightColor >> 16 & 0xFF), (dayColor >> 16 & 0xFF)) / 255.0 * k;
        double g = Mth.lerpInt(t, (nightColor >> 8 & 0xFF), (dayColor >> 8 & 0xFF)) / 255.0 * k;
        double b = Mth.lerpInt(t, (nightColor & 0xFF), (dayColor & 0xFF)) / 255.0 * k;
        return new Vec3(r, g, b);
    }
}
