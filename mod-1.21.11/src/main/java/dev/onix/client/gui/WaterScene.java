package dev.onix.client.gui;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.onix.client.OnixClient;
import java.nio.ByteBuffer;
import java.util.OptionalInt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

/**
 * The sunken scene's water drawn on the graphics card (shaders/post/water.fsh): caustics, light shafts flickering with
 * the waves, the rippling surface and drifting specks. The screens that show the scene ask for it every frame
 * ({@link #request}); WaterSceneMixin then draws it straight into the screen before the menus are drawn on top.
 * If the shader cannot run, {@link #drawn} stays false and FallTransition draws its simpler water instead.
 */
public final class WaterScene {
    private WaterScene() {}

    private static final int UBO_SIZE = new Std140SizeCalculator().putVec2().putFloat().get();
    private static final long START = System.nanoTime();
    private static RenderPipeline pipeline;
    private static GpuBuffer ubo;
    private static boolean broken;
    private static volatile long wantedUntil;
    private static volatile long drawnAt;

    /** A screen shows the scene: draw the water from the next frame on (for as long as it keeps asking). */
    public static void request() {
        if (!broken) wantedUntil = System.currentTimeMillis() + 120;
    }

    /** The water was drawn this frame (the screen then skips its own background). */
    public static boolean drawn() {
        return !broken && System.currentTimeMillis() - drawnAt < 60;
    }

    /** Called once a frame before the menus are drawn. */
    public static void renderIfWanted() {
        if (broken || System.currentTimeMillis() > wantedUntil) return;
        try {
            RenderTarget target = Minecraft.getInstance().getMainRenderTarget();
            if (target.getColorTextureView() == null) return;
            if (pipeline == null) {
                pipeline = RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                        .withLocation(Identifier.fromNamespaceAndPath("onix", "pipeline/water"))
                        .withVertexShader(Identifier.fromNamespaceAndPath("onix", "post/fullscreen"))
                        .withFragmentShader(Identifier.fromNamespaceAndPath("onix", "post/water"))
                        .withUniform("WaterConfig", UniformType.UNIFORM_BUFFER)
                        .build();
            }
            if (ubo == null) {
                ubo = RenderSystem.getDevice().createBuffer(() -> "Onix water", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UBO_SIZE);
            }
            float seconds = (System.nanoTime() - START) / 1_000_000_000f % 3600f;
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = Std140Builder.onStack(stack, UBO_SIZE).putVec2(target.width, target.height).putFloat(seconds).get();
                encoder.writeToBuffer(ubo.slice(), data);
            }
            try (RenderPass pass = encoder.createRenderPass(() -> "Onix water", target.getColorTextureView(), OptionalInt.empty())) {
                pass.setPipeline(pipeline);
                pass.setUniform("WaterConfig", ubo);
                pass.draw(0, 3);
            }
            drawnAt = System.currentTimeMillis();
        } catch (Throwable e) {
            broken = true;   // the simpler water in FallTransition takes over
            OnixClient.LOGGER.warn("Water shader unavailable, using the simple water: {}", e.toString());
        }
    }
}
