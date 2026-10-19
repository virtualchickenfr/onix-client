package net.minecraft.client.renderer.special;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public interface SpecialModelRenderer<T> {
	void render(@Nullable T object, ItemDisplayContext itemDisplayContext, PoseStack poseStack, MultiBufferSource multiBufferSource, int i, int j, boolean bl);

	void getExtents(Set<Vector3f> set);

	@Nullable
	T extractArgument(ItemStack itemStack);

	@Environment(EnvType.CLIENT)
	interface Unbaked {
		@Nullable
		SpecialModelRenderer<?> bake(EntityModelSet entityModelSet);

		MapCodec<? extends SpecialModelRenderer.Unbaked> type();
	}
}
