package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface NoDataSpecialModelRenderer extends SpecialModelRenderer<Void> {
	@Nullable
	default Void extractArgument(ItemStack itemStack) {
		return null;
	}

	default void render(
		@Nullable Void void_, ItemDisplayContext itemDisplayContext, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, boolean bl
	) {
		this.render(itemDisplayContext, poseStack, multiBufferSource, i, j, bl);
	}

	void render(ItemDisplayContext itemDisplayContext, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, boolean bl);
}
