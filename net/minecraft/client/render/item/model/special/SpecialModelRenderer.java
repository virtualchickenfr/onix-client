package net.minecraft.client.render.item.model.special;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface SpecialModelRenderer<T> {
	void render(
		@Nullable T data, ItemDisplayContext displayContext, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, boolean glint
	);

	@Nullable
	T getData(ItemStack stack);

	@Environment(EnvType.CLIENT)
	public interface Unbaked {
		@Nullable
		SpecialModelRenderer<?> bake(LoadedEntityModels entityModels);

		MapCodec<? extends SpecialModelRenderer.Unbaked> getCodec();
	}
}
