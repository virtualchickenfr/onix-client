package dev.onix.client.module.impl.visual;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.onix.client.module.Category;
import dev.onix.client.module.Module;
import dev.onix.client.setting.BoolSetting;
import dev.onix.client.setting.ColorSetting;
import dev.onix.client.setting.ModeSetting;
import dev.onix.client.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Chunk borders drawn by Onix (colors, radius, grid) or the vanilla F3 + G view. */
public class ChunkBordersModule extends Module {
    private final ModeSetting style = add(new ModeSetting("style", "Style", "Onix", "Vanilla")
            .describe("Vanilla uses the F3 + G renderer; Onix draws its own configurable lines."));
    private final NumberSetting radius = add(new NumberSetting("radius", "Chunk Radius", 0, 0, 3, 1).unit("chunks")
            .describe("0 shows only the chunk you stand in.").visibleIf(this::onix));
    private final ModeSetting height = add(new ModeSetting("height", "Height", "Around You", "Full World").visibleIf(this::onix));
    private final NumberSetting range = add(new NumberSetting("range", "Vertical Range", 24, 4, 96, 4).unit("blocks")
            .visibleIf(() -> onix() && height.is("Around You")));
    private final ColorSetting borderColor = add(ColorSetting.accent("borderColor", "Border Color").visibleIf(this::onix));

    {
        group("Grid");
    }

    private final BoolSetting grid = add(new BoolSetting("grid", "Grid on Current Chunk", true).visibleIf(this::onix));
    private final NumberSetting spacing = add(new NumberSetting("spacing", "Grid Spacing", 4, 1, 8, 1).unit("blocks")
            .visibleIf(() -> onix() && grid.isOn()));
    private final ColorSetting gridColor = add(new ColorSetting("gridColor", "Grid Color", 0x66FFFFFF).visibleIf(() -> onix() && grid.isOn()));
    private final BoolSetting sections = add(new BoolSetting("sections", "Section Lines (Every 16 Y)", true).visibleIf(this::onix));

    private boolean vanillaShown;

    public ChunkBordersModule() {
        super("chunkborders", "Chunk Borders", "Show chunk borders without F3 + G, in your own colors.", Category.VISUAL, false);
        tags("f3 g", "chunks", "grid");
    }

    private boolean onix() {
        return style.is("Onix");
    }

    @Override
    public void onTick() {
        if (mc().debugRenderer == null) return;
        boolean want = !onix();
        if (want && !vanillaShown) {
            vanillaShown = mc().debugRenderer.switchRenderChunkborder();
            if (!vanillaShown) vanillaShown = mc().debugRenderer.switchRenderChunkborder();
        } else if (!want && vanillaShown) {
            mc().debugRenderer.switchRenderChunkborder();
            vanillaShown = false;
        }
    }

    @Override
    protected void onDisable() {
        if (vanillaShown && mc().debugRenderer != null) mc().debugRenderer.switchRenderChunkborder();
        vanillaShown = false;
    }

    @Override
    public void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (!isEnabled() || !onix() || mc().level == null || mc().player == null || context.consumers() == null) return;
            PoseStack matrices = context.matrixStack();
            if (matrices == null) return;
            Vec3 cam = context.camera().getPosition();
            VertexConsumer lines = context.consumers().getBuffer(RenderType.lines());

            double y1;
            double y2;
            if (height.is("Full World")) {
                y1 = mc().level.getMinBuildHeight();
                y2 = mc().level.getMaxBuildHeight();
            } else {
                y1 = Math.max(mc().level.getMinBuildHeight(), cam.y - range.get());
                y2 = Math.min(mc().level.getMaxBuildHeight(), cam.y + range.get());
            }
            ChunkPos center = mc().player.chunkPosition();
            int r = radius.getInt();
            int border = borderColor.color();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int x0 = (center.x + dx) * 16;
                    int z0 = (center.z + dz) * 16;
                    for (int corner = 0; corner < 4; corner++) {
                        int x = x0 + (corner % 2) * 16;
                        int z = z0 + (corner / 2) * 16;
                        line(matrices, lines, cam, x, y1, z, x, y2, z, border);
                    }
                    if (sections.isOn()) {
                        for (int y = (int) Math.ceil(y1 / 16.0) * 16; y <= y2; y += 16) {
                            line(matrices, lines, cam, x0, y, z0, x0 + 16, y, z0 + 16, border);
                        }
                    }
                }
            }
            if (grid.isOn()) {
                int x0 = center.x * 16;
                int z0 = center.z * 16;
                int step = Math.max(1, spacing.getInt());
                int color = gridColor.color();
                for (int i = step; i < 16; i += step) {
                    line(matrices, lines, cam, x0 + i, y1, z0, x0 + i, y2, z0, color);
                    line(matrices, lines, cam, x0 + i, y1, z0 + 16, x0 + i, y2, z0 + 16, color);
                    line(matrices, lines, cam, x0, y1, z0 + i, x0, y2, z0 + i, color);
                    line(matrices, lines, cam, x0 + 16, y1, z0 + i, x0 + 16, y2, z0 + i, color);
                }
                for (int y = (int) Math.ceil(y1 / step) * step; y <= y2; y += step) {
                    if (sections.isOn() && Math.floorMod(y, 16) == 0) continue;
                    line(matrices, lines, cam, x0, y, z0, x0 + 16, y, z0 + 16, color);
                }
            }
        });
    }

    /** Draws an axis-aligned line (or a flat rectangle when two axes differ) relative to the camera. */
    private static void line(PoseStack matrices, VertexConsumer lines, Vec3 cam,
                             double ax, double ay, double az, double bx, double by, double bz, int color) {
        float a = ((color >>> 24) & 0xFF) / 255f;
        if (a <= 0.01f) return;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        AABB box = new AABB(ax - cam.x, ay - cam.y, az - cam.z, bx - cam.x, by - cam.y, bz - cam.z);
        LevelRenderer.renderLineBox(matrices, lines, box, r, g, b, a);
    }
}
