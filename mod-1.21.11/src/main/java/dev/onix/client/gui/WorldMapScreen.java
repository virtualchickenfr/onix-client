package dev.onix.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import dev.onix.client.OnixClient;
import dev.onix.client.module.impl.hud.MinimapModule;
import dev.onix.client.module.impl.hud.WaypointsModule;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The world map (Minimap → World Map Key, default M): everything you explored in this dimension, from above. Drag to
 * move, scroll to zoom, right click to put a waypoint there, Space to centre on yourself.
 */
public class WorldMapScreen extends Screen {
    private static final int TEX = 512;
    private static final Identifier TEXTURE_ID = Identifier.fromNamespaceAndPath("onix", "world_map");

    private final MinimapModule minimap;
    private DynamicTexture texture;
    private int texH = TEX;
    private double centerX;
    private double centerZ;
    private float scale = 1f;            // GUI pixels per block
    private String builtFor = "";
    private long builtAt;
    private boolean dragging;

    public WorldMapScreen(MinimapModule minimap) {
        super(Component.literal("World Map"));
        this.minimap = minimap;
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p != null) {
            centerX = p.getX();
            centerZ = p.getZ();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        if (texture != null) {
            texture.close();
            minecraft.getTextureManager().release(TEXTURE_ID);
            texture = null;
        }
    }

    private double worldX(double sx) {
        return centerX + (sx - width / 2.0) / scale;
    }

    private double worldZ(double sy) {
        return centerZ + (sy - height / 2.0) / scale;
    }

    /** Fills the texture with the visible area (only when the view changed or every half second for new chunks). */
    private void build() {
        int th = Math.max(64, Math.min(TEX, Math.round(TEX * height / (float) Math.max(1, width))));
        String key = Math.round(centerX * 4) + ":" + Math.round(centerZ * 4) + ":" + scale + ":" + width + "x" + height;
        long now = System.currentTimeMillis();
        if (texture != null && th == texH && key.equals(builtFor) && now - builtAt < 500) return;
        if (texture == null || th != texH) {
            if (texture != null) texture.close();
            texH = th;
            texture = new DynamicTexture(() -> "onix world map", TEX, texH, false);
            minecraft.getTextureManager().register(TEXTURE_ID, texture);
        }
        NativeImage img = texture.getPixels();
        if (img == null) return;
        double left = worldX(0), top = worldZ(0);
        double perX = width / scale / TEX, perZ = height / scale / texH;
        for (int ty = 0; ty < texH; ty++) {
            int wz = (int) Math.floor(top + (ty + 0.5) * perZ);
            for (int tx = 0; tx < TEX; tx++) {
                int wx = (int) Math.floor(left + (tx + 0.5) * perX);
                int c = minimap.colorAt(wx, wz);
                img.setPixel(tx, ty, c == 0 ? 0xFF101317 : c);
            }
        }
        texture.upload();
        builtFor = key;
        builtAt = now;
    }

    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xFF101317);
        build();
        if (texture != null) {
            ctx.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, 0, 0, 0f, 0f, width, height, TEX, texH, TEX, texH);
        }
        // waypoints
        WaypointsModule wp = OnixClient.MODULES.get(WaypointsModule.class);
        if (wp != null) {
            for (int[] w : wp.inThisDimension()) {
                float sx = (float) ((w[0] + 0.5 - centerX) * scale + width / 2.0);
                float sy = (float) ((w[1] + 0.5 - centerZ) * scale + height / 2.0);
                if (sx < -8 || sy < -8 || sx > width + 8 || sy > height + 8) continue;
                MinimapModule.diamond(ctx, sx, sy, 5f, 0xAA000000);
                MinimapModule.diamond(ctx, sx, sy, 3.8f, 0xFFFFD34D);
            }
        }
        // other players
        if (minecraft.level != null) {
            for (var p : minecraft.level.players()) {
                if (p == minecraft.player) continue;
                float sx = (float) ((p.getX() - centerX) * scale + width / 2.0);
                float sy = (float) ((p.getZ() - centerZ) * scale + height / 2.0);
                MinimapModule.diamond(ctx, sx, sy, 3f, 0xFFFFFFFF);
                Theme.small(ctx, p.getName().getString(), Math.round(sx) + 5, Math.round(sy) - 3, 0xDDFFFFFF);
            }
        }
        // you
        var me = minecraft.player;
        if (me != null) {
            float sx = (float) ((me.getX() - centerX) * scale + width / 2.0);
            float sy = (float) ((me.getZ() - centerZ) * scale + height / 2.0);
            minimap.arrow(ctx, sx, sy, (float) Math.toRadians(me.getYRot() + 180f));
        }

        // top bar and the coordinates under the mouse
        ctx.fillGradient(0, 0, width, 30, 0xCC0B0D10, 0x000B0D10);
        Theme.text(ctx, "World Map", 10, 8, 0xFFFFFFFF);
        String dim = minecraft.level == null ? "" : minecraft.level.dimension().identifier().getPath().replace('_', ' ');
        Theme.small(ctx, dim, 10 + Theme.width("World Map") + 8, 10, 0x99FFFFFF);
        String zoomText = (scale >= 1 ? Math.round(scale) + ":1" : "1:" + Math.round(1 / scale));
        Theme.small(ctx, zoomText, width - 10 - Theme.smallWidth(zoomText), 10, 0x99FFFFFF);
        ctx.fillGradient(0, height - 30, width, height, 0x000B0D10, 0xCC0B0D10);
        String at = (int) Math.floor(worldX(mouseX)) + ", " + (int) Math.floor(worldZ(mouseY));
        Theme.text(ctx, at, 10, height - 16, 0xFFE6E6EA);
        String hint = "Drag to move · Scroll to zoom · Right click: waypoint · Space: centre";
        Theme.small(ctx, hint, width - 10 - Theme.smallWidth(hint), height - 14, 0x88FFFFFF);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // the map is the background
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent onix$e, boolean onix$dbl) {
        if (onix$e.button() == 1) {
            int x = (int) Math.floor(worldX(onix$e.x()));
            int z = (int) Math.floor(worldZ(onix$e.y()));
            WaypointsModule wp = OnixClient.MODULES.get(WaypointsModule.class);
            if (wp != null) {
                if (!wp.isEnabled()) wp.setEnabled(true);
                String name = "Map " + x + " " + z;
                wp.add(name, x, minimap.heightAt(x, z), z);
                Notifications.show("Waypoint added", name);
            }
            return true;
        }
        dragging = onix$e.button() == 0;
        return true;
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent onix$e) {
        dragging = false;
        return true;
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent onix$e, double onix$dx, double onix$dy) {
        if (!dragging) return false;
        centerX -= onix$dx / scale;
        centerZ -= onix$dy / scale;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // zoom around the mouse, so the place under it stays put
        double wx = worldX(mouseX), wz = worldZ(mouseY);
        float next = (float) Math.max(0.125, Math.min(8, scale * (verticalAmount > 0 ? 1.25 : 0.8)));
        scale = next;
        centerX = wx - (mouseX - width / 2.0) / scale;
        centerZ = wz - (mouseY - height / 2.0) / scale;
        return true;
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent onix$k) {
        int key = onix$k.key();
        if (key == 32 && minecraft.player != null) {          // space: back to you
            centerX = minecraft.player.getX();
            centerZ = minecraft.player.getZ();
            return true;
        }
        if (key == 77) {                                      // M closes it again
            onClose();
            return true;
        }
        return super.keyPressed(onix$k);
    }
}
