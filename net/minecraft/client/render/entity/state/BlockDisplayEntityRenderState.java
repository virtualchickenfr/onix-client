package net.minecraft.client.render.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.decoration.DisplayEntity;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BlockDisplayEntityRenderState extends DisplayEntityRenderState {
	@Nullable
	public DisplayEntity.BlockDisplayEntity.Data data;

	@Override
	public boolean canRender() {
		return this.data != null;
	}
}
