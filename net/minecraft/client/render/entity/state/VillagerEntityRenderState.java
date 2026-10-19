package net.minecraft.client.render.entity.state;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.village.VillagerData;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class VillagerEntityRenderState extends ItemHolderEntityRenderState implements VillagerDataRenderState {
	public boolean headRolling;
	@Nullable
	public VillagerData villagerData;

	@Nullable
	@Override
	public VillagerData getVillagerData() {
		return this.villagerData;
	}
}
