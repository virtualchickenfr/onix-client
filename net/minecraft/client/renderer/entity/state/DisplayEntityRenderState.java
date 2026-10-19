package net.minecraft.client.renderer.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Display;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public abstract class DisplayEntityRenderState extends EntityRenderState {
	@Nullable
	public Display.RenderState renderState;
	public float interpolationProgress;
	public float entityYRot;
	public float entityXRot;

	public abstract boolean hasSubState();
}
