package net.minecraft.client.render.item.model;

import com.google.common.base.Suppliers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.tint.TintSource;
import net.minecraft.client.render.item.tint.TintSourceTypes;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BakedQuadFactory;
import net.minecraft.client.render.model.BakedSimpleModel;
import net.minecraft.client.render.model.Baker;
import net.minecraft.client.render.model.ModelRotation;
import net.minecraft.client.render.model.ModelSettings;
import net.minecraft.client.render.model.ModelTextures;
import net.minecraft.client.render.model.ResolvableModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class BasicItemModel implements ItemModel {
	private final List<TintSource> tints;
	private final List<BakedQuad> quads;
	private final Supplier<Vector3f[]> vector;
	private final ModelSettings settings;

	public BasicItemModel(List<TintSource> tints, List<BakedQuad> quads, ModelSettings settings) {
		this.tints = tints;
		this.quads = quads;
		this.settings = settings;
		this.vector = Suppliers.memoize(() -> bakeQuads(this.quads));
	}

	public static Vector3f[] bakeQuads(List<BakedQuad> quads) {
		Set<Vector3f> set = new HashSet();

		for (BakedQuad bakedQuad : quads) {
			BakedQuadFactory.calculatePosition(bakedQuad.vertexData(), set::add);
		}

		return (Vector3f[])set.toArray(Vector3f[]::new);
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
			layerRenderState.setGlint(shouldUseSpecialGlint(stack) ? ItemRenderState.Glint.SPECIAL : ItemRenderState.Glint.STANDARD);
		}

		int i = this.tints.size();
		int[] is = layerRenderState.initTints(i);

		for (int j = 0; j < i; j++) {
			is[j] = ((TintSource)this.tints.get(j)).getTint(stack, world, user);
		}

		layerRenderState.setVector(this.vector);
		layerRenderState.setRenderLayer(RenderLayers.getItemLayer(stack));
		this.settings.addSettings(layerRenderState, displayContext);
		layerRenderState.getQuads().addAll(this.quads);
	}

	private static boolean shouldUseSpecialGlint(ItemStack stack) {
		return stack.isIn(ItemTags.COMPASSES) || stack.isOf(Items.CLOCK);
	}

	@Environment(EnvType.CLIENT)
	public record Unbaked(Identifier model, List<TintSource> tints) implements ItemModel.Unbaked {
		public static final MapCodec<BasicItemModel.Unbaked> CODEC = RecordCodecBuilder.mapCodec(
			instance -> instance.group(
					Identifier.CODEC.fieldOf("model").forGetter(BasicItemModel.Unbaked::model),
					TintSourceTypes.CODEC.listOf().optionalFieldOf("tints", List.of()).forGetter(BasicItemModel.Unbaked::tints)
				)
				.apply(instance, BasicItemModel.Unbaked::new)
		);

		@Override
		public void resolve(ResolvableModel.Resolver resolver) {
			resolver.markDependency(this.model);
		}

		@Override
		public ItemModel bake(ItemModel.BakeContext context) {
			Baker baker = context.blockModelBaker();
			BakedSimpleModel bakedSimpleModel = baker.getModel(this.model);
			ModelTextures modelTextures = bakedSimpleModel.getTextures();
			List<BakedQuad> list = bakedSimpleModel.bakeGeometry(modelTextures, baker, ModelRotation.X0_Y0).getAllQuads();
			ModelSettings modelSettings = ModelSettings.resolveSettings(baker, bakedSimpleModel, modelTextures);
			return new BasicItemModel(this.tints, list, modelSettings);
		}

		@Override
		public MapCodec<BasicItemModel.Unbaked> getCodec() {
			return CODEC;
		}
	}
}
