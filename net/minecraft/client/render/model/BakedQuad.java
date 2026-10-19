package net.minecraft.client.render.model;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;

@Environment(EnvType.CLIENT)
public record BakedQuad(int[] vertexData, int tintIndex, Direction face, Sprite sprite, boolean shade, int lightEmission) {
	public boolean hasTint() {
		return this.tintIndex != -1;
	}
}
