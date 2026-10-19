package net.minecraft.client.render.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.decoration.painting.PaintingVariant;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class PaintingEntityRenderState extends EntityRenderState {
	public Direction facing = Direction.NORTH;
	@Nullable
	public PaintingVariant variant;
	public int[] lightmapCoordinates = new int[0];
}
