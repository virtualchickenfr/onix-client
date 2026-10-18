package dev.onix.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws the local player with a rotatable pose (used by the wardrobe). In 1.21.11 entities in menus are drawn as a
 * "picture in picture" into a screen rectangle, so the position is given in screen (GUI) coordinates.
 */
public final class PlayerPreview {
    private PlayerPreview() {}

    public static boolean available() {
        return Minecraft.getInstance().player != null;
    }

    /** Draws the player centred on (cx, cy) in screen coordinates; {@code size} is about half the player's height. */
    public static void draw(GuiGraphics ctx, float cx, float cy, int size, float rotation) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || size <= 0) return;
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        EntityRenderState state = renderer.createRenderState(player, 1f);
        state.lightCoords = 15728880;
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180f + rotation;
            living.yRot = 0f;
            living.xRot = 0f;
            living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
            living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
            living.scale = 1f;
        }
        Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI);
        Vector3f offset = new Vector3f(0f, state.boundingBoxHeight / 2f + 0.0625f, 0f);
        int halfW = Math.round(size * 0.9f);
        int halfH = Math.round(size * 1.25f);
        int x = Math.round(cx);
        int y = Math.round(cy);
        ctx.submitEntityRenderState(state, size, offset, rot, null, x - halfW, y - halfH, x + halfW, y + halfH);
    }
}
