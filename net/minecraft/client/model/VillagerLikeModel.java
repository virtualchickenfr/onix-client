package net.minecraft.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface VillagerLikeModel {
	void hatVisible(boolean bl);

	void translateToArms(PoseStack poseStack);
}
