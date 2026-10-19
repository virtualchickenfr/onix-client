package net.minecraft.client.render.item.model;

import com.mojang.serialization.MapCodec;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.entity.model.LoadedEntityModels;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ResolvableModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.ContextSwapper;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public interface ItemModel {
	void update(
		ItemRenderState state,
		ItemStack stack,
		ItemModelManager resolver,
		ItemDisplayContext displayContext,
		@Nullable ClientWorld world,
		@Nullable LivingEntity user,
		int seed
	);

	@Environment(EnvType.CLIENT)
	public record BakeContext(Baker blockModelBaker, LoadedEntityModels entityModelSet, ItemModel missingItemModel, @Nullable ContextSwapper contextSwapper) {
	}

	@Environment(EnvType.CLIENT)
	public interface Unbaked extends ResolvableModel {
		MapCodec<? extends ItemModel.Unbaked> getCodec();

		ItemModel bake(ItemModel.BakeContext context);
	}
}
