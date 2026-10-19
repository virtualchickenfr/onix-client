package net.minecraft.client.render.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.decoration.DisplayEntity;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class DisplayEntityRenderState extends EntityRenderState {
	@Nullable
	public DisplayEntity.RenderState displayRenderState;
	public float lerpProgress;
	public float yaw;
	public float pitch;

	public abstract boolean canRender();
}
