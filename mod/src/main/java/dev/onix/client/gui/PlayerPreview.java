package dev.onix.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Draws the local player with a rotatable pose (used by the wardrobe). Uses the current matrix. */
public final class PlayerPreview {
    private PlayerPreview() {}

    public static boolean available() {
        return MinecraftClient.getInstance().player != null;
    }

    /** Draws the player centred on (cx, cy). */
    public static void draw(DrawContext ctx, float cx, float cy, int size, float rotation) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) return;

        float bodyYaw = player.bodyYaw, prevBodyYaw = player.prevBodyYaw;
        float yaw = player.getYaw(), prevYaw = player.prevYaw;
        float pitch = player.getPitch(), prevPitch = player.prevPitch;
        float headYaw = player.headYaw, prevHeadYaw = player.prevHeadYaw;

        float r = 180f + rotation;
        player.bodyYaw = r;
        player.prevBodyYaw = r;
        player.setYaw(r);
        player.prevYaw = r;
        player.setPitch(0f);
        player.prevPitch = 0f;
        player.headYaw = r;
        player.prevHeadYaw = r;

        Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI);
        float scale = player.getScale();
        Vector3f offset = new Vector3f(0f, player.getHeight() / 2f + 0.0625f * scale, 0f);
        InventoryScreen.drawEntity(ctx, cx, cy, size / scale, offset, rot, null, player);

        player.bodyYaw = bodyYaw;
        player.prevBodyYaw = prevBodyYaw;
        player.setYaw(yaw);
        player.prevYaw = prevYaw;
        player.setPitch(pitch);
        player.prevPitch = prevPitch;
        player.headYaw = headYaw;
        player.prevHeadYaw = prevHeadYaw;
    }
}
