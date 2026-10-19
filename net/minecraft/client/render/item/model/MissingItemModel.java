package net.minecraft.client.render.item.model;

import com.google.common.base.Suppliers;
import java.util.List;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.ModelSettings;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

@Environment(EnvType.CLIENT)
public class MissingItemModel implements ItemModel {
	private final List<BakedQuad> quads;
	private final Supplier<Vector3f[]> vector;
	private final ModelSettings settings;

	public MissingItemModel(List<BakedQuad> quads, ModelSettings settings) {
		this.quads = quads;
		this.settings = settings;
		this.vector = Suppliers.memoize(() -> BasicItemModel.bakeQuads(this.quads));
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
		layerRenderState.setRenderLayer(TexturedRenderLayers.getEntityCutout());
		this.settings.addSettings(layerRenderState, displayContext);
		layerRenderState.setVector(this.vector);
		layerRenderState.getQuads().addAll(this.quads);
	}
}
