package net.minecraft.client.render.item.model;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.model.special.SpecialModelRenderer;
import net.minecraft.client.render.item.model.special.SpecialModelTypes;
import net.minecraft.client.render.model.BakedSimpleModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelSettings;
import net.minecraft.client.render.model.ModelTextures;
import net.minecraft.client.render.model.ResolvableModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class SpecialItemModel<T> implements ItemModel {
	private static final Vector3f[] EMPTY = new Vector3f[]{
		new Vector3f(0.0F, 0.0F, 0.0F),
		new Vector3f(0.0F, 0.0F, 1.0F),
		new Vector3f(0.0F, 1.0F, 1.0F),
		new Vector3f(0.0F, 1.0F, 0.0F),
		new Vector3f(1.0F, 1.0F, 0.0F),
		new Vector3f(1.0F, 1.0F, 1.0F),
		new Vector3f(1.0F, 0.0F, 1.0F),
		new Vector3f(1.0F, 0.0F, 0.0F)
	};
	private final SpecialModelRenderer<T> specialModelType;
	private final ModelSettings settings;

	public SpecialItemModel(SpecialModelRenderer<T> specialModelType, ModelSettings settings) {
		this.specialModelType = specialModelType;
		this.settings = settings;
	}

	@Override
	public void update(
		ItemRenderState state,
		ItemStack stack,
		ItemModelManager resolver,
		ItemDisplayContext displayContext,
		@Nullable ClientWorld world,
		@Nullable LivingEntity user,
		int seed
	) {
		ItemRenderState.LayerRenderState layerRenderState = state.newLayer();
		if (stack.hasGlint()) {
			layerRenderState.setGlint(ItemRenderState.Glint.STANDARD);
		}

		layerRenderState.setVector(() -> EMPTY);
		layerRenderState.setSpecialModel(this.specialModelType, this.specialModelType.getData(stack));
		this.settings.addSettings(layerRenderState, displayContext);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(Identifier base, SpecialModelRenderer.Unbaked specialModel) implements ItemModel.Unbaked {
		public static final MapCodec<SpecialItemModel.Unbaked> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Identifier.CODEC.fieldOf("base").forGetter(SpecialItemModel.Unbaked::base),
					SpecialModelTypes.CODEC.fieldOf("model").forGetter(SpecialItemModel.Unbaked::specialModel)
				)
				.apply(instance, SpecialItemModel.Unbaked::new)
		);

		@Override
		public void resolve(ResolvableModel.Resolver resolver) {
			resolver.markDependency(this.base);
		}

		@Override
		public ItemModel bake(ItemModel.BakeContext context) {
			SpecialModelRenderer<?> specialModelRenderer = this.specialModel.bake(context.entityModelSet());
			if (specialModelRenderer == null) {
				return context.missingItemModel();
			} else {
				ModelSettings modelSettings = this.getSettings(context);
				return new SpecialItemModel<>(specialModelRenderer, modelSettings);
			}
		}

		private ModelSettings getSettings(ItemModel.BakeContext context) {
			Baker baker = context.blockModelBaker();
			BakedSimpleModel bakedSimpleModel = baker.getModel(this.base);
			ModelTextures modelTextures = bakedSimpleModel.getTextures();
			return ModelSettings.resolveSettings(baker, bakedSimpleModel, modelTextures);
		}

		@Override
		public MapCodec<SpecialItemModel.Unbaked> getCodec() {
			return CODEC;
		}
	}
}
