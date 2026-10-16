package dev.onix.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Draws the local player with a rotatable pose (used by the wardrobe). Uses the current matrix. */
public final class PlayerPreview {
    private PlayerPreview() {}

    public static boolean available() {
        return Minecraft.getInstance().player != null;
    }

    /** Draws the player centred on (cx, cy). */
    public static void draw(GuiGraphics ctx, float cx, float cy, int size, float rotation) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        float bodyYaw = player.yBodyRot, prevBodyYaw = player.yBodyRotO;
        float yaw = player.getYRot(), prevYaw = player.yRotO;
        float pitch = player.getXRot(), prevPitch = player.xRotO;
        float headYaw = player.yHeadRot, prevHeadYaw = player.yHeadRotO;

        float r = 180f + rotation;
        player.yBodyRot = r;
        player.yBodyRotO = r;
        player.setYRot(r);
        player.yRotO = r;
        player.setXRot(0f);
        player.xRotO = 0f;
        player.yHeadRot = r;
        player.yHeadRotO = r;

        Quaternionf rot = new Quaternionf().rotateZ((float) Math.PI);
        float scale = player.getScale();
        Vector3f offset = new Vector3f(0f, player.getBbHeight() / 2f + 0.0625f * scale, 0f);
        InventoryScreen.renderEntityInInventory(ctx, cx, cy, size / scale, offset, rot, null, player);

        player.yBodyRot = bodyYaw;
        player.yBodyRotO = prevBodyYaw;
        player.setYRot(yaw);
        player.yRotO = prevYaw;
        player.setXRot(pitch);
        player.xRotO = prevPitch;
        player.yHeadRot = headYaw;
        player.yHeadRotO = prevHeadYaw;
    }
}
