package dev.onix.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.visual.CustomSkyModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Draws Custom Sky sky boxes in place of the vanilla overworld sky (WorldRendererMixin). */
public final class SkyRenderer {
    private static final Identifier SUN = Identifier.ofVanilla("textures/environment/sun.png");
    private static final Identifier MOON = Identifier.ofVanilla("textures/environment/moon_phases.png");

    private SkyRenderer() {}

    private static CustomSkyModule module() {
        return OnixClient.MODULES == null ? null : OnixClient.MODULES.get(CustomSkyModule.class);
    }

    /** 1 at noon, 0 at night, smooth in between (same curve vanilla uses for sky brightness). */
    private static float daylight(ClientWorld world, float tickDelta) {
        float angle = world.getSkyAngle(tickDelta);
        return MathHelper.clamp(MathHelper.cos(angle * ((float) Math.PI * 2f)) * 2f + 0.5f, 0f, 1f);
    }

    /** @return true if the custom sky was drawn and the vanilla sky must be skipped. */
    public static boolean render(Matrix4f viewMatrix, float tickDelta) {
        CustomSkyModule m = module();
        ClientWorld world = MinecraftClient.getInstance().world;
        if (m == null || world == null || !m.appliesHere()) return false;

        float day = daylight(world, tickDelta);
        float rain = world.getRainGradient(tickDelta);
        float base = m.brightness.getFloat() / 100f * (1f - rain * 0.45f);
        Skies.Sky daySky = m.day();
        Skies.Sky nightSky = m.night();

        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);
        float minutes = (System.currentTimeMillis() % 86_400_000L) / 60_000f;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(minutes * m.rotation.getFloat()));

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        if (nightSky == null) {
            float shade = m.darkenAtNight.isOn() ? MathHelper.lerp(day, 0.22f, 1f) : 1f;
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
    private static void box(MatrixStack matrices, Identifier texture, float brightness, float alpha) {
        Skies.ensureFiltered(texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.setShaderTexture(0, texture);
        int c = Math.round(MathHelper.clamp(brightness, 0f, 1f) * 255f);
        int color = (Math.round(alpha * 255f) << 24) | (c << 16) | (c << 8) | c;
        // about half a texel inwards so smooth filtering never samples the neighbouring face
        float inset = 0.0007f;
        for (int i = 0; i < 6; i++) {
            matrices.push();
            switch (i) {
                case 1 -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
                case 2 -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90f));
                case 3 -> matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180f));
                case 4 -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90f));
                case 5 -> matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90f));
                default -> {}
            }
            float u0 = (i % 3) / 3f + inset, u1 = (i % 3 + 1) / 3f - inset;
            float v0 = (i / 3) / 2f + inset, v1 = (i / 3 + 1) / 2f - inset;
            Matrix4f mat = matrices.peek().getPositionMatrix();
            BufferBuilder b = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
            b.vertex(mat, -100f, -100f, -100f).texture(u0, v0).color(color);
            b.vertex(mat, -100f, -100f, 100f).texture(u0, v1).color(color);
            b.vertex(mat, 100f, -100f, 100f).texture(u1, v1).color(color);
            b.vertex(mat, 100f, -100f, -100f).texture(u1, v0).color(color);
            BufferRenderer.drawWithGlobalProgram(b.end());
            matrices.pop();
        }
    }

    /** Vanilla sun and moon, following the time of day. */
    private static void sunAndMoon(Matrix4f viewMatrix, ClientWorld world, float tickDelta, float alpha) {
        MatrixStack matrices = new MatrixStack();
        matrices.multiplyPositionMatrix(viewMatrix);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90f));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(world.getSkyAngle(tickDelta) * 360f));
        Matrix4f m = matrices.peek().getPositionMatrix();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE,
                GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);

        float k = 30f;
        RenderSystem.setShaderTexture(0, SUN);
        BufferBuilder sun = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        sun.vertex(m, -k, 100f, -k).texture(0f, 0f);
        sun.vertex(m, k, 100f, -k).texture(1f, 0f);
        sun.vertex(m, k, 100f, k).texture(1f, 1f);
        sun.vertex(m, -k, 100f, k).texture(0f, 1f);
        BufferRenderer.drawWithGlobalProgram(sun.end());

        k = 20f;
        RenderSystem.setShaderTexture(0, MOON);
        int phase = world.getMoonPhase();
        int px = phase % 4;
        int py = phase / 4 % 2;
        float u0 = px / 4f, v0 = py / 2f, u1 = (px + 1) / 4f, v1 = (py + 1) / 2f;
        BufferBuilder moon = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        moon.vertex(m, -k, -100f, k).texture(u1, v1);
        moon.vertex(m, k, -100f, k).texture(u0, v1);
        moon.vertex(m, k, -100f, -k).texture(u0, v0);
        moon.vertex(m, -k, -100f, -k).texture(u1, v0);
        BufferRenderer.drawWithGlobalProgram(moon.end());
    }

    /** The horizon color of the current sky, for fog blending; null when the vanilla sky is used. */
    public static Vec3d horizonColor(float tickDelta) {
        CustomSkyModule m = module();
        ClientWorld world = MinecraftClient.getInstance().world;
        if (m == null || world == null || !m.appliesHere() || !m.matchFog.isOn()) return null;
        float day = daylight(world, tickDelta);
        int dayColor = m.day().horizon();
        Skies.Sky night = m.night();
        int nightColor = night != null ? night.horizon() : dayColor;
        float shade = night == null && m.darkenAtNight.isOn() ? MathHelper.lerp(day, 0.22f, 1f) : 1f;
        float k = m.brightness.getFloat() / 100f * shade;
        float t = night == null ? 1f : day;
        double r = MathHelper.lerp(t, (nightColor >> 16 & 0xFF), (dayColor >> 16 & 0xFF)) / 255.0 * k;
        double g = MathHelper.lerp(t, (nightColor >> 8 & 0xFF), (dayColor >> 8 & 0xFF)) / 255.0 * k;
        double b = MathHelper.lerp(t, (nightColor & 0xFF), (dayColor & 0xFF)) / 255.0 * k;
        return new Vec3d(r, g, b);
    }
}
